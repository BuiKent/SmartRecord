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
    val transcriptText: String = "",  // Accumulated final text
    val partialText: String = "",      // Current partial (real-time) text
    val error: String? = null,
    val isRestarting: Boolean = false
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
                        partialText = "",
                        error = null,
                        isRestarting = false
                    )
                }
                
                // Start realtime transcription
                realtimeTranscript.start { text ->
                    AppLogger.d(TAG_REALTIME, "Received transcript update: %s", text)
                    _uiState.update { currentState ->
                        // Parse the text to determine if it's partial or final
                        when {
                            text.startsWith("PARTIAL:") -> {
                                // This is a partial result
                                val partialText = text.removePrefix("PARTIAL:")
                                currentState.copy(partialText = partialText)
                            }
                            text.startsWith("FINAL:") -> {
                                // This is accumulated final text
                                val finalText = text.removePrefix("FINAL:")
                                currentState.copy(
                                    transcriptText = finalText,
                                    partialText = ""  // Clear partial when we have final
                                )
                            }
                            else -> {
                                // Fallback for old format or error messages
                                if (text.startsWith("[") || text.startsWith("Error:")) {
                                    // Error message
                                    currentState.copy(error = text)
                                } else {
                                    // Assume it's final text (backward compatibility)
                                    currentState.copy(transcriptText = text, partialText = "")
                                }
                            }
                        }
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
                _uiState.update { 
                    it.copy(
                        isRecording = false,
                        partialText = "",
                        isRestarting = false
                    )
                }
                AppLogger.d(TAG_REALTIME, "Realtime transcription stopped")
            } catch (e: Exception) {
                AppLogger.e(TAG_REALTIME, "Failed to stop realtime transcription", e)
                _uiState.update { 
                    it.copy(
                        isRecording = false,
                        partialText = "",
                        error = "Failed to stop: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Get the current display text (final + partial).
     */
    fun getDisplayText(): String {
        val state = _uiState.value
        return if (state.partialText.isNotEmpty()) {
            // Show final text + partial text
            if (state.transcriptText.isNotEmpty()) {
                "${state.transcriptText} ${state.partialText}"
            } else {
                state.partialText
            }
        } else {
            state.transcriptText
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

