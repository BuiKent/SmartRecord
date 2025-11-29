package com.yourname.smartrecorder.ui.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_RECORDING
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_REALTIME
import com.yourname.smartrecorder.core.service.AutoSaveManager
import com.yourname.smartrecorder.core.service.ForegroundServiceManager
import com.yourname.smartrecorder.core.service.RecordingForegroundService
import com.yourname.smartrecorder.data.repository.RecordingSessionRepository
import com.yourname.smartrecorder.data.stt.WhisperModelManager
import com.yourname.smartrecorder.domain.state.RecordingState
import com.yourname.smartrecorder.domain.usecase.AddBookmarkUseCase
import com.yourname.smartrecorder.domain.usecase.GetRecordingsDirectoryUseCase
import com.yourname.smartrecorder.domain.usecase.PauseRecordingUseCase
import com.yourname.smartrecorder.domain.usecase.ResumeRecordingUseCase
import com.yourname.smartrecorder.domain.usecase.StartRecordingUseCase
import com.yourname.smartrecorder.domain.usecase.StopRecordingAndSaveUseCase
import com.yourname.smartrecorder.domain.usecase.RealtimeTranscriptUseCase
import com.yourname.smartrecorder.core.speech.RecognitionState
import com.yourname.smartrecorder.ui.screens.RecordUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
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
    private val modelManager: WhisperModelManager,
    private val realtimeTranscript: RealtimeTranscriptUseCase,
    private val recordingSessionRepository: RecordingSessionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Expose recording state from repository
    // ⚠️ CRITICAL: Use Eagerly to ensure immediate subscription and correct initial value
    val recordingState: StateFlow<RecordingState> = 
        recordingSessionRepository.state
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly, // Subscribe immediately to get current state
                initialValue = recordingSessionRepository.getCurrentState() // Use actual current state as initial
            )
    
    // Derive UI state from repository state
    private val _otherUiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = combine(
        recordingState,
        _otherUiState
    ) { recordingState, otherState ->
        RecordUiState(
            isRecording = recordingState is RecordingState.Active && !recordingState.isPaused,
            isPaused = recordingState is RecordingState.Active && recordingState.isPaused,
            durationMs = when (recordingState) {
                is RecordingState.Active -> recordingState.getElapsedMs()
                else -> 0L
            },
            liveText = otherState.liveText,
            partialText = otherState.partialText,
            error = otherState.error,
            amplitude = otherState.amplitude,
            isModelReady = otherState.isModelReady,
            isLiveTranscribeMode = otherState.isLiveTranscribeMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordUiState()
    )
    
    // BroadcastReceiver để nhận stop từ notification
    private val stopFromNotificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RecordingForegroundService.BROADCAST_STOP) {
                AppLogger.logCritical(TAG_RECORDING, "Stop from notification received")
                // Gọi onStopClick để lưu vào database
                onStopClick()
            }
        }
    }
    
    init {
        checkModelReady()
        
        // Register BroadcastReceiver để nhận stop từ notification
        val filter = IntentFilter(RecordingForegroundService.BROADCAST_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(stopFromNotificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(stopFromNotificationReceiver, filter)
        }
        
        // ⚠️ CRITICAL: Auto-start timer and restore currentRecording if recording is already active (e.g., after navigation)
        // This ensures UI updates correctly when navigating back to RecordScreen
        viewModelScope.launch {
            recordingState.collect { state ->
                if (state is RecordingState.Active) {
                    // Recording is active - restore currentRecording if null
                    if (currentRecording == null) {
                        AppLogger.d(TAG_RECORDING, "Restoring currentRecording from repository state", 
                            "recordingId=${state.recordingId}, filePath=${state.filePath}")
                        currentRecording = com.yourname.smartrecorder.domain.model.Recording(
                            id = state.recordingId,
                            title = "", // Will be set when saved
                            filePath = state.filePath,
                            createdAt = state.startTimeMs,
                            durationMs = 0L, // Will be calculated when stopped
                            mode = "DEFAULT",
                            isPinned = false,
                            isArchived = false
                        )
                    }
                    
                    // Ensure timer is running
                    if (timerJob?.isActive != true) {
                        AppLogger.d(TAG_RECORDING, "Auto-starting timer - recording already active", 
                            "recordingId=${state.recordingId}, isPaused=${state.isPaused}")
                        startTimer()
                    }
                } else {
                    // Recording is idle - stop timer if running and clear currentRecording
                    timerJob?.cancel()
                    if (currentRecording != null) {
                        AppLogger.d(TAG_RECORDING, "Clearing currentRecording - recording is idle")
                        currentRecording = null
                    }
                }
            }
        }
    }
    
    private val _navigateToTranscript = MutableStateFlow<String?>(null)
    val navigateToTranscript: StateFlow<String?> = _navigateToTranscript.asStateFlow()

    private var currentRecording: com.yourname.smartrecorder.domain.model.Recording? = null
    private var timerJob: Job? = null
    
    @Volatile
    private var isStarting: Boolean = false
    
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
                    _otherUiState.update { 
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
                            _otherUiState.update { 
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
                                _otherUiState.update { 
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
        // Check if already recording
        val currentState = recordingState.value
        if (currentState is RecordingState.Active || isStarting) {
            AppLogger.w(TAG_VIEWMODEL, "RecordViewModel: Start rejected - already recording or starting")
            return
        }
        
        AppLogger.logViewModel(TAG_RECORDING, "RecordViewModel", "onStartClick", null)
        
        viewModelScope.launch {
            try {
                isStarting = true
                
                // Recovery logic: Check if AudioRecorder is in stuck state
                // This can happen if ViewModel was cleared while recording was active
                try {
                    // Try to start - if it fails with "already in progress", force reset
                    AppLogger.d(TAG_RECORDING, "Getting recordings directory")
                    val outputDir = getRecordingsDirectory()
                    AppLogger.d(TAG_RECORDING, "Starting recording -> outputDir: %s", outputDir.absolutePath)
                    
                    currentRecording = startRecording(outputDir)
                } catch (e: IllegalStateException) {
                    if (e.message?.contains("already in progress") == true) {
                        AppLogger.logRareCondition(TAG_RECORDING, 
                            "Recording stuck state detected - forcing reset", 
                            "error=${e.message}")
                        // Force reset AudioRecorder to recover from stuck state
                        try {
                            audioRecorder.forceReset()
                            AppLogger.d(TAG_RECORDING, "AudioRecorder force reset completed, retrying start")
                            // Retry after reset
                            val outputDir = getRecordingsDirectory()
                            currentRecording = startRecording(outputDir)
                        } catch (retryException: Exception) {
                            AppLogger.e(TAG_RECORDING, "Failed to start recording after force reset", retryException)
                            _otherUiState.update { it.copy(error = retryException.message) }
                            currentRecording = null
                            isStarting = false
                            return@launch
                        }
                    } else {
                        throw e
                    }
                }
                
                AppLogger.logViewModel(TAG_RECORDING, "RecordViewModel", "Recording started", 
                    "recordingId=${currentRecording?.id}")
                
                // Start foreground service to keep recording active in background
                // Service will update repository state, UI will react automatically
                val fileName = File(currentRecording!!.filePath).name
                foregroundServiceManager.startRecordingService(currentRecording!!.id, fileName)
                
                // Start auto-save - use startTimeMs from repository state
                val recordingState = recordingSessionRepository.getCurrentState()
                if (recordingState is RecordingState.Active) {
                    autoSaveManager.startAutoSave(currentRecording!!, recordingState.startTimeMs)
                }
                
                _otherUiState.update { it.copy(error = null) }
                startTimer()
            } catch (e: Exception) {
                AppLogger.e(TAG_RECORDING, "Failed to start recording", e)
                _otherUiState.update { it.copy(error = e.message) }
                currentRecording = null
            } finally {
                isStarting = false
            }
        }
    }

    fun onPauseClick() {
        val currentState = recordingState.value
        if (currentState !is RecordingState.Active) {
            AppLogger.w(TAG_VIEWMODEL, "RecordViewModel: Pause/Resume rejected - not recording")
            return
        }
        
        AppLogger.logViewModel(TAG_RECORDING, "RecordViewModel", "onPauseClick", null)
        viewModelScope.launch {
            try {
                if (currentState.isPaused) {
                    // Resume recording
                    // ⚠️ CRITICAL: Service will pause/resume AudioRecorder AND update repository
                    foregroundServiceManager.resumeRecordingService()
                    // Service will update repository, UI will react automatically
                    startTimer()  // Restart timer (will cancel old one first)
                    AppLogger.d(TAG_RECORDING, "Recording resumed -> recordingId: %s", currentState.recordingId)
                } else {
                    // Pause recording
                    // ⚠️ CRITICAL: Service will pause AudioRecorder AND update repository
                    foregroundServiceManager.pauseRecordingService()
                    // Service will update repository, UI will react automatically
                    AppLogger.d(TAG_RECORDING, "Recording paused -> recordingId: %s", currentState.recordingId)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_RECORDING, "Failed to pause/resume recording", e)
                _otherUiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun onStopClick() {
        val currentState = recordingState.value
        
        // ⚠️ CRITICAL: Allow stop even if state is already Idle (from notification stop)
        // This handles the case where service already stopped but we still need to save
        if (currentState !is RecordingState.Active && currentRecording == null) {
            AppLogger.w(TAG_VIEWMODEL, "RecordViewModel: Stop rejected - not recording and no currentRecording")
            return
        }
        
        // ⚠️ CRITICAL: Prevent multiple calls
        if (isStarting) {
            AppLogger.w(TAG_VIEWMODEL, "RecordViewModel: Stop rejected - already processing")
            return
        }
        
        AppLogger.logViewModel(TAG_RECORDING, "RecordViewModel", "onStopClick", null)
        isStarting = true
        
        viewModelScope.launch {
            try {
                timerJob?.cancel()
                
                // Get recording from repository state or currentRecording
                // ⚠️ CRITICAL: Restore currentRecording from repository state if null (e.g., after navigation)
                val recording = if (currentState is RecordingState.Active) {
                    currentRecording ?: run {
                        // currentRecording is null (e.g., ViewModel was recreated after navigation)
                        // Create Recording object from repository state
                        AppLogger.d(TAG_RECORDING, "Restoring currentRecording from repository state", 
                            "recordingId=${currentState.recordingId}, filePath=${currentState.filePath}")
                        val restoredRecording = com.yourname.smartrecorder.domain.model.Recording(
                            id = currentState.recordingId,
                            title = "", // Will be set when saved
                            filePath = currentState.filePath,
                            createdAt = currentState.startTimeMs,
                            durationMs = 0L, // Will be calculated below
                            mode = "DEFAULT",
                            isPinned = false,
                            isArchived = false
                        )
                        currentRecording = restoredRecording
                        restoredRecording
                    }
                } else {
                    // State already Idle (from notification stop), use currentRecording if available
                    currentRecording ?: run {
                        AppLogger.w(TAG_RECORDING, "Stop called but currentRecording is null and state is Idle")
                        isStarting = false
                        return@launch
                    }
                }
                
                // Calculate actual recording duration
                val durationMs = if (currentState is RecordingState.Active) {
                    currentState.getElapsedMs()
                } else {
                    // State already Idle, use a default or calculate from file
                    recording.durationMs.takeIf { it > 0 } ?: 0L
                }
                
                AppLogger.d(TAG_RECORDING, "Stopping recording -> recordingId: %s, duration: %d ms", 
                    recording.id, durationMs)
                
                // Stop foreground service (if not already stopped)
                if (currentState is RecordingState.Active) {
                    foregroundServiceManager.stopRecordingService()
                }
                
                // Stop auto-save and force final save
                autoSaveManager.forceSaveNow()
                autoSaveManager.stopAutoSave()
                
                // ⚠️ CRITICAL: Stop AudioRecorder and save
                // Service doesn't stop AudioRecorder, so we always need to stop it here
                val saved = stopRecordingAndSave(recording, durationMs)
                
                // ⚠️ CRITICAL: Update repository state to Idle AFTER saving
                recordingSessionRepository.setIdle()
                
                AppLogger.logViewModel(TAG_RECORDING, "RecordViewModel", "Recording saved", 
                    "recordingId=${saved.id}, title=${saved.title}, duration=${saved.durationMs}ms")
                
                currentRecording = null
                isStarting = false
                _navigateToTranscript.value = saved.id
                
                AppLogger.d(TAG_RECORDING, "Navigation triggered -> transcriptId: %s", saved.id)
            } catch (e: Exception) {
                AppLogger.e(TAG_RECORDING, "Failed to stop recording", e)
                isStarting = false
                _otherUiState.update { it.copy(error = e.message) }
                // Still update repository to Idle on error
                recordingSessionRepository.setIdle()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            try {
                while (true) {
                    delay(50) // Update more frequently for waveform
                    val currentState = recordingState.value
                    
                    // Stop timer only if no active recording
                    if (currentState !is RecordingState.Active) {
                        break
                    }
                    
                    val elapsed = currentState.getElapsedMs()
                    if (currentState.isPaused) {
                        // Paused: no amplitude
                        _otherUiState.update { it.copy(amplitude = 0) }
                    } else {
                        // Recording: get amplitude for waveform visualization
                        val amplitude = try {
                            audioRecorder.getAmplitude()
                        } catch (e: Exception) {
                            0
                        }
                        _otherUiState.update { it.copy(amplitude = amplitude) }
                        
                        // Update foreground service notification every second
                        if (elapsed % 1000 < 50) {
                            foregroundServiceManager.updateRecordingNotification(elapsed, false)
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
        val currentState = recordingState.value
        if (currentState !is RecordingState.Active || currentState.isPaused) {
            return
        }
        
        val recording = currentRecording ?: return
        
        val timestampMs = currentState.getElapsedMs()
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
                _otherUiState.update { it.copy(error = "Failed to add bookmark: ${e.message}") }
            }
        }
    }
    
    fun clearError() {
        AppLogger.d(TAG_RECORDING, "[RecordViewModel] Error cleared by user")
        _otherUiState.update { it.copy(error = null) }
    }
    
    fun onLiveTranscribeClick() {
        AppLogger.logViewModel(TAG_REALTIME, "RecordViewModel", "onLiveTranscribeClick", null)
        if (_otherUiState.value.isLiveTranscribeMode) {
            // Stop live transcribe
            stopLiveTranscribe()
        } else {
            // Start live transcribe
            startLiveTranscribe()
        }
    }
    
    private fun startLiveTranscribe() {
        AppLogger.d(TAG_REALTIME, "Starting live transcribe mode")
        _otherUiState.update { 
            it.copy(
                isLiveTranscribeMode = true,
                liveText = "",
                partialText = "",
                error = null
            )
        }
        
        // Start ASR
        realtimeTranscript.start { text ->
            AppLogger.d(TAG_REALTIME, "Received transcript update: %s", text)
            _otherUiState.update { currentState ->
                when {
                    text.startsWith("PARTIAL:") -> {
                        val partialText = text.removePrefix("PARTIAL:")
                        currentState.copy(partialText = partialText)
                    }
                    text.startsWith("FINAL:") -> {
                        val finalText = text.removePrefix("FINAL:")
                        currentState.copy(
                            liveText = finalText,
                            partialText = ""  // Clear partial when we have final
                        )
                    }
                    else -> {
                        if (text.startsWith("[") || text.startsWith("Error:")) {
                            currentState.copy(error = text)
                        } else {
                            currentState.copy(liveText = text, partialText = "")
                        }
                    }
                }
            }
        }
    }
    
    private fun stopLiveTranscribe() {
        AppLogger.d(TAG_REALTIME, "Stopping live transcribe mode")
        realtimeTranscript.stop()
        _otherUiState.update { 
            it.copy(
                isLiveTranscribeMode = false,
                partialText = ""
            )
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        
        // Unregister BroadcastReceiver
        try {
            context.unregisterReceiver(stopFromNotificationReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
        
        // Cleanup if recording was active
        val currentState = recordingState.value
        if (currentState is RecordingState.Active && currentRecording != null) {
            AppLogger.logRareCondition(TAG_RECORDING, 
                "ViewModel cleared while recording active", 
                "recordingId=${currentRecording?.id}, isPaused=${currentState.isPaused}")
            
            // Try to save recording first (if possible)
            viewModelScope.launch {
                try {
                    // Stop service and auto-save first
                    foregroundServiceManager.stopRecordingService()
                    autoSaveManager.stopAutoSave()
                    
                    // Force reset AudioRecorder to prevent stuck state
                    // This is critical: AudioRecorder is a singleton and will keep state
                    // even after ViewModel is cleared, causing "already in progress" errors
                    audioRecorder.forceReset()
                    AppLogger.d(TAG_RECORDING, "AudioRecorder force reset completed in onCleared()")
                } catch (e: Exception) {
                    AppLogger.e(TAG_RECORDING, "Error during cleanup in onCleared()", e)
                    // Still try to force reset even if other cleanup fails
                    try {
                        audioRecorder.forceReset()
                    } catch (resetException: Exception) {
                        AppLogger.e(TAG_RECORDING, "Failed to force reset AudioRecorder in onCleared()", resetException)
                    }
                }
            }
        } else {
            // Even if not recording, check if AudioRecorder is in stuck state
            // This can happen if previous recording wasn't properly cleaned up
            viewModelScope.launch {
                try {
                    // Try to start - if it fails silently, AudioRecorder might be stuck
                    // We'll detect this in onStartClick() recovery logic
                    AppLogger.d(TAG_RECORDING, "ViewModel cleared - no active recording, skipping AudioRecorder reset")
                } catch (e: Exception) {
                    AppLogger.w(TAG_RECORDING, "Unexpected error in onCleared() cleanup", e)
                }
            }
        }
        
        // Stop live transcribe if active
        if (_otherUiState.value.isLiveTranscribeMode) {
            stopLiveTranscribe()
        }
    }
}

