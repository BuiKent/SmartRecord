package com.yourname.smartrecorder.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.domain.usecase.GetRecordingsDirectoryUseCase
import com.yourname.smartrecorder.domain.usecase.StartRecordingUseCase
import com.yourname.smartrecorder.domain.usecase.StopRecordingAndSaveUseCase
import com.yourname.smartrecorder.ui.screens.RecordUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.jvm.Volatile

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val getRecordingsDirectory: GetRecordingsDirectoryUseCase,
    private val startRecording: StartRecordingUseCase,
    private val stopRecordingAndSave: StopRecordingAndSaveUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()
    
    private val _navigateToTranscript = MutableStateFlow<String?>(null)
    val navigateToTranscript: StateFlow<String?> = _navigateToTranscript.asStateFlow()

    private var currentRecording: com.yourname.smartrecorder.domain.model.Recording? = null
    private var timerJob: Job? = null
    private var startTimeMs: Long = 0L
    
    @Volatile
    private var isStarting: Boolean = false
    
    fun onNavigationHandled() {
        _navigateToTranscript.value = null
    }

    fun onStartClick() {
        if (isStarting || _uiState.value.isRecording) {
            return // Prevent concurrent starts
        }
        
        viewModelScope.launch {
            try {
                isStarting = true
                val outputDir = getRecordingsDirectory()
                currentRecording = startRecording(outputDir)
                startTimeMs = System.currentTimeMillis()
                
                _uiState.update { it.copy(isRecording = true, durationMs = 0L, error = null) }
                startTimer()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isRecording = false) }
                currentRecording = null
            } finally {
                isStarting = false
            }
        }
    }

    fun onPauseClick() {
        viewModelScope.launch {
            try {
                timerJob?.cancel()
                // TODO: Implement pause recording - call audioRecorder.pause()
                _uiState.update { it.copy(isRecording = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun onStopClick() {
        viewModelScope.launch {
            try {
                timerJob?.cancel()
                val recording = currentRecording ?: return@launch
                val durationMs = System.currentTimeMillis() - startTimeMs
                
                val saved = stopRecordingAndSave(recording, durationMs)
                _uiState.update { 
                    it.copy(
                        isRecording = false,
                        durationMs = 0L
                    )
                }
                currentRecording = null
                _navigateToTranscript.value = saved.id
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            try {
                while (true) {
                    delay(100)
                    if (!_uiState.value.isRecording) {
                        break
                    }
                    val elapsed = System.currentTimeMillis() - startTimeMs
                    _uiState.update { it.copy(durationMs = elapsed) }
                }
            } catch (e: Exception) {
                // Timer cancelled or error
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        // Note: AudioRecorder cleanup is handled by singleton lifecycle
    }
}

