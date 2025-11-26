package com.yourname.smartrecorder.ui.realtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_REALTIME
import com.yourname.smartrecorder.domain.usecase.RealtimeTranscriptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RealtimeTranscriptUiState(
    val isRecording: Boolean = false,
    val transcriptText: String = "",
    val error: String? = null
)

@HiltViewModel
class RealtimeTranscriptViewModel @Inject constructor(
    private val realtimeTranscript: RealtimeTranscriptUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(RealtimeTranscriptUiState())
    val uiState: StateFlow<RealtimeTranscriptUiState> = _uiState.asStateFlow()
    
    fun startRecording() {
        AppLogger.logViewModel(TAG_REALTIME, "RealtimeTranscriptViewModel", "startRecording", null)
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        isRecording = true,
                        transcriptText = "",
                        error = null
                    )
                }
                
                // Start realtime transcription
                realtimeTranscript.start { text ->
                    AppLogger.d(TAG_REALTIME, "Received transcript update: %s", text)
                    _uiState.update { 
                        it.copy(transcriptText = it.transcriptText + " " + text)
                    }
                }
                
                AppLogger.d(TAG_REALTIME, "Realtime transcription started")
            } catch (e: Exception) {
                AppLogger.e(TAG_REALTIME, "Failed to start realtime transcription", e)
                _uiState.update { 
                    it.copy(
                        isRecording = false,
                        error = "Failed to start: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun stopRecording() {
        AppLogger.logViewModel(TAG_REALTIME, "RealtimeTranscriptViewModel", "stopRecording", null)
        viewModelScope.launch {
            try {
                realtimeTranscript.stop()
                _uiState.update { it.copy(isRecording = false) }
                AppLogger.d(TAG_REALTIME, "Realtime transcription stopped")
            } catch (e: Exception) {
                AppLogger.e(TAG_REALTIME, "Failed to stop realtime transcription", e)
                _uiState.update { 
                    it.copy(
                        isRecording = false,
                        error = "Failed to stop: ${e.message}"
                    )
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.isRecording) {
            viewModelScope.launch {
                try {
                    realtimeTranscript.stop()
                } catch (e: Exception) {
                    AppLogger.e(TAG_REALTIME, "Error stopping transcription on clear", e)
                }
            }
        }
    }
}

