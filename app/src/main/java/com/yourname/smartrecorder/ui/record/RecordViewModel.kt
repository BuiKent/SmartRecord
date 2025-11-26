package com.yourname.smartrecorder.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecordViewModel @Inject constructor(
    application: Application,
    private val startRecording: StartRecordingUseCase,
    private val stopRecordingAndSave: StopRecordingAndSaveUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var currentRecording: com.yourname.smartrecorder.domain.model.Recording? = null
    private var timerJob: Job? = null
    private var startTimeMs: Long = 0L

    fun onStartClick() {
        viewModelScope.launch {
            try {
                val outputDir = File(getApplication<Application>().filesDir, "recordings")
                outputDir.mkdirs()
                
                currentRecording = startRecording(outputDir)
                startTimeMs = System.currentTimeMillis()
                
                _uiState.update { it.copy(isRecording = true, durationMs = 0L) }
                startTimer()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun onPauseClick() {
        timerJob?.cancel()
        _uiState.update { it.copy(isRecording = false) }
        // TODO: Implement pause recording
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
                // TODO: Navigate to transcript screen
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val elapsed = System.currentTimeMillis() - startTimeMs
                _uiState.update { it.copy(durationMs = elapsed) }
            }
        }
    }
}

