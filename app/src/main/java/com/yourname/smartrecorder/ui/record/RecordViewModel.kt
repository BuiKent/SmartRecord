package com.yourname.smartrecorder.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_RECORDING
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import com.yourname.smartrecorder.core.service.AutoSaveManager
import com.yourname.smartrecorder.core.service.ForegroundServiceManager
import com.yourname.smartrecorder.data.stt.WhisperModelManager
import com.yourname.smartrecorder.domain.usecase.AddBookmarkUseCase
import com.yourname.smartrecorder.domain.usecase.GetRecordingsDirectoryUseCase
import com.yourname.smartrecorder.domain.usecase.PauseRecordingUseCase
import com.yourname.smartrecorder.domain.usecase.ResumeRecordingUseCase
import com.yourname.smartrecorder.domain.usecase.StartRecordingUseCase
import com.yourname.smartrecorder.domain.usecase.StopRecordingAndSaveUseCase
import com.yourname.smartrecorder.ui.screens.RecordUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.jvm.Volatile

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val getRecordingsDirectory: GetRecordingsDirectoryUseCase,
    private val startRecording: StartRecordingUseCase,
    private val stopRecordingAndSave: StopRecordingAndSaveUseCase,
    private val pauseRecording: PauseRecordingUseCase,
    private val resumeRecording: ResumeRecordingUseCase,
    private val addBookmark: AddBookmarkUseCase,
    private val audioRecorder: com.yourname.smartrecorder.core.audio.AudioRecorder,
    private val foregroundServiceManager: ForegroundServiceManager,
    private val autoSaveManager: AutoSaveManager,
    private val modelManager: WhisperModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()
    
    init {
        checkModelReady()
    }
    
    private val _navigateToTranscript = MutableStateFlow<String?>(null)
    val navigateToTranscript: StateFlow<String?> = _navigateToTranscript.asStateFlow()

    private var currentRecording: com.yourname.smartrecorder.domain.model.Recording? = null
    private var timerJob: Job? = null
    private var startTimeMs: Long = 0L
    private var totalPausedDurationMs: Long = 0L  // Total time spent paused
    private var pauseStartTimeMs: Long = 0L  // When pause started
    
    @Volatile
    private var isStarting: Boolean = false
    
    @Volatile
    private var isPaused: Boolean = false
    
    fun onNavigationHandled() {
        _navigateToTranscript.value = null
    }
    
    private fun checkModelReady() {
        AppLogger.logViewModel(TAG_TRANSCRIPT, "RecordViewModel", "checkModelReady", "Starting model check (silent)")
        viewModelScope.launch {
            try {
                // Check if model exists and is valid (silently, no UI feedback)
                val isDownloaded = withContext(Dispatchers.IO) {
                    modelManager.isModelDownloaded()
                }
                
                if (isDownloaded) {
                    AppLogger.d(TAG_TRANSCRIPT, "[RecordViewModel] Model is ready")
                    _uiState.update { 
                        it.copy(isModelReady = true) 
                    }
                } else {
                    // Model not found - wait a bit for SmartRecorderApplication to download it
                    AppLogger.d(TAG_TRANSCRIPT, "[RecordViewModel] Model not ready, waiting for background download...")
                    
                    // Wait up to 2 seconds, checking every 500ms for model to appear
                    var found = false
                    repeat(4) {
                        kotlinx.coroutines.delay(500)
                        val checked = withContext(Dispatchers.IO) {
                            modelManager.isModelDownloaded()
                        }
                        if (checked) {
                            AppLogger.d(TAG_TRANSCRIPT, "[RecordViewModel] Model is now ready (found after ${it + 1} checks)")
                            _uiState.update { 
                                it.copy(isModelReady = true) 
                            }
                            found = true
                            return@repeat
                        }
                    }
                    
                    // If still not found, try downloading as fallback (but WhisperModelManager will check for duplicates)
                    if (!found) {
                        AppLogger.d(TAG_TRANSCRIPT, "[RecordViewModel] Model still not ready after waiting, downloading as fallback...")
                        try {
                            val progressLogger = AppLogger.ProgressLogger(TAG_TRANSCRIPT, "[RecordViewModel] Model download")
                            modelManager.downloadModel { progress ->
                                progressLogger.logProgress(progress)
                                // Silent download - no UI update
                            }
                            
                            // Verify after download
                            val verified = withContext(Dispatchers.IO) {
                                modelManager.isModelDownloaded()
                            }
                            
                            if (verified) {
                                AppLogger.d(TAG_TRANSCRIPT, "[RecordViewModel] Model downloaded and verified successfully")
                                _uiState.update { 
                                    it.copy(isModelReady = true) 
                                }
                            } else {
                                AppLogger.w(TAG_TRANSCRIPT, "[RecordViewModel] Model verification failed after download")
                                // Keep isModelReady = false, but don't show error - user can still record
                            }
                        } catch (e: Exception) {
                            AppLogger.e(TAG_TRANSCRIPT, "[RecordViewModel] Failed to download model (silent)", e)
                            // Don't set error - allow recording without model
                            // Model will be available later when needed for transcription
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_TRANSCRIPT, "[RecordViewModel] Error checking model (silent)", e)
                // Don't set error - allow recording without model
            }
        }
    }

    fun onStartClick() {
        // Recording doesn't require model - model is only for transcription
        if (isStarting || _uiState.value.isRecording) {
            AppLogger.w(TAG_VIEWMODEL, "RecordViewModel: Start rejected - already starting or recording")
            return // Prevent concurrent starts
        }
        
        AppLogger.logViewModel(TAG_RECORDING, "RecordViewModel", "onStartClick", null)
        
        viewModelScope.launch {
            try {
                isStarting = true
                AppLogger.d(TAG_RECORDING, "Getting recordings directory")
                val outputDir = getRecordingsDirectory()
                AppLogger.d(TAG_RECORDING, "Starting recording -> outputDir: %s", outputDir.absolutePath)
                
                currentRecording = startRecording(outputDir)
                startTimeMs = System.currentTimeMillis()
                totalPausedDurationMs = 0L  // Reset pause duration
                pauseStartTimeMs = 0L
                
                AppLogger.logViewModel(TAG_RECORDING, "RecordViewModel", "Recording started", 
                    "recordingId=${currentRecording?.id}, startTime=$startTimeMs")
                
                // Start foreground service to keep recording active in background
                val fileName = File(currentRecording!!.filePath).name
                foregroundServiceManager.startRecordingService(currentRecording!!.id, fileName)
                
                // Start auto-save
                autoSaveManager.startAutoSave(currentRecording!!, startTimeMs)
                
                _uiState.update { it.copy(isRecording = true, isPaused = false, durationMs = 0L, error = null) }
                isPaused = false
                startTimer()
            } catch (e: Exception) {
                AppLogger.e(TAG_RECORDING, "Failed to start recording", e)
                _uiState.update { it.copy(error = e.message, isRecording = false) }
                currentRecording = null
            } finally {
                isStarting = false
            }
        }
    }

    fun onPauseClick() {
        AppLogger.logViewModel(TAG_RECORDING, "RecordViewModel", "onPauseClick", null)
        viewModelScope.launch {
            try {
                if (isPaused) {
                    // Resume recording
                    resumeRecording()
                    // Add pause duration to total paused time
                    totalPausedDurationMs += (System.currentTimeMillis() - pauseStartTimeMs)
                    pauseStartTimeMs = 0L
                    isPaused = false
                    startTimer()  // Restart timer (will cancel old one first)
                    _uiState.update { it.copy(isRecording = true, isPaused = false) }
                    AppLogger.d(TAG_RECORDING, "Recording resumed -> recordingId: %s, totalPaused: %d ms", 
                        currentRecording?.id, totalPausedDurationMs)
                } else {
                    // Pause recording
                    pauseRecording()
                    isPaused = true
                    pauseStartTimeMs = System.currentTimeMillis()  // Track when pause started
                    // Keep timer running to show duration when paused
                    _uiState.update { it.copy(isRecording = false, isPaused = true) }
                    AppLogger.d(TAG_RECORDING, "Recording paused -> recordingId: %s", currentRecording?.id)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_RECORDING, "Failed to pause/resume recording", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun onStopClick() {
        AppLogger.logViewModel(TAG_RECORDING, "RecordViewModel", "onStopClick", null)
        viewModelScope.launch {
            try {
                timerJob?.cancel()
                val recording = currentRecording ?: run {
                    AppLogger.w(TAG_RECORDING, "Stop called but no recording in progress")
                    return@launch
                }
                
                // Calculate actual recording duration (excluding paused time)
                val currentTime = System.currentTimeMillis()
                if (isPaused) {
                    // Add current pause duration to total
                    totalPausedDurationMs += (currentTime - pauseStartTimeMs)
                }
                val durationMs = currentTime - startTimeMs - totalPausedDurationMs
                AppLogger.d(TAG_RECORDING, "Stopping recording -> recordingId: %s, duration: %d ms (total: %d ms, paused: %d ms)", 
                    recording.id, durationMs, currentTime - startTimeMs, totalPausedDurationMs)
                
                // Stop foreground service
                foregroundServiceManager.stopRecordingService()
                
                // Stop auto-save and force final save
                autoSaveManager.forceSaveNow()
                autoSaveManager.stopAutoSave()
                
                val saved = stopRecordingAndSave(recording, durationMs)
                
                AppLogger.logViewModel(TAG_RECORDING, "RecordViewModel", "Recording saved", 
                    "recordingId=${saved.id}, title=${saved.title}, duration=${saved.durationMs}ms")
                
                _uiState.update { 
                    it.copy(
                        isRecording = false,
                        isPaused = false,
                        durationMs = 0L
                    )
                }
                currentRecording = null
                isPaused = false
                totalPausedDurationMs = 0L
                pauseStartTimeMs = 0L
                _navigateToTranscript.value = saved.id
                
                AppLogger.d(TAG_RECORDING, "Navigation triggered -> transcriptId: %s", saved.id)
            } catch (e: Exception) {
                AppLogger.e(TAG_RECORDING, "Failed to stop recording", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private var pausedDurationMs: Long = 0L  // Track duration when paused
    
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            try {
                while (true) {
                    delay(50) // Update more frequently for waveform
                    val currentState = _uiState.value
                    
                    // Stop timer only if no active recording (not recording AND not paused)
                    if (!currentState.isRecording && !currentState.isPaused) {
                        break
                    }
                    
                    val elapsed = System.currentTimeMillis() - startTimeMs - totalPausedDurationMs
                    if (currentState.isPaused) {
                        // Add current pause time to calculation
                        val currentPauseDuration = System.currentTimeMillis() - pauseStartTimeMs
                        val effectiveElapsed = elapsed - currentPauseDuration
                        _uiState.update { it.copy(durationMs = effectiveElapsed.coerceAtLeast(0), amplitude = 0) }
                    } else if (currentState.isRecording) {
                        // Get amplitude for waveform visualization only when actively recording
                        val amplitude = try {
                            audioRecorder.getAmplitude()
                        } catch (e: Exception) {
                            0
                        }
                        _uiState.update { it.copy(durationMs = elapsed, amplitude = amplitude) }
                        
                        // Update foreground service notification every second
                        if (elapsed % 1000 < 50) {
                            foregroundServiceManager.updateRecordingNotification(elapsed, isPaused)
                        }
                    }
                }
            } catch (e: Exception) {
                // Timer cancelled or error
            }
        }
    }
    
    private val _bookmarkAdded = MutableStateFlow(false)
    val bookmarkAdded: StateFlow<Boolean> = _bookmarkAdded.asStateFlow()
    
    fun onBookmarkAddedHandled() {
        _bookmarkAdded.value = false
    }
    
    fun onBookmarkClick(note: String = "") {
        val recording = currentRecording ?: return
        if (!_uiState.value.isRecording) return
        
        val timestampMs = System.currentTimeMillis() - startTimeMs
        AppLogger.logViewModel(TAG_RECORDING, "RecordViewModel", "onBookmarkClick", 
            "recordingId=${recording.id}, timestamp=${timestampMs}ms")
        
        viewModelScope.launch {
            try {
                addBookmark(recording.id, timestampMs, note)
                _bookmarkAdded.value = true
                AppLogger.d(TAG_RECORDING, "Bookmark added -> recordingId: %s, timestamp: %d ms", 
                    recording.id, timestampMs)
            } catch (e: Exception) {
                AppLogger.e(TAG_RECORDING, "Failed to add bookmark", e)
                _uiState.update { it.copy(error = "Failed to add bookmark: ${e.message}") }
            }
        }
    }
    
    fun clearError() {
        AppLogger.d(TAG_RECORDING, "[RecordViewModel] Error cleared by user")
        _uiState.update { it.copy(error = null) }
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        
        // Cleanup if recording was active
        if (_uiState.value.isRecording && currentRecording != null) {
            AppLogger.logRareCondition(TAG_RECORDING, 
                "ViewModel cleared while recording active", 
                "recordingId=${currentRecording?.id}")
            // Stop service and auto-save
            foregroundServiceManager.stopRecordingService()
            autoSaveManager.stopAutoSave()
        }
        
        // Note: AudioRecorder cleanup is handled by singleton lifecycle
    }
}

