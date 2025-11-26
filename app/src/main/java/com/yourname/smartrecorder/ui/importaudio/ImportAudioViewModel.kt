package com.yourname.smartrecorder.ui.importaudio

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.usecase.ImportAudioFileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.jvm.Volatile

data class ImportUiState(
    val isImporting: Boolean = false,
    val progress: Int = 0,
    val error: String? = null,
    val importedRecordingId: String? = null
)

@HiltViewModel
class ImportAudioViewModel @Inject constructor(
    private val importAudioFileUseCase: ImportAudioFileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()
    
    @Volatile
    private var isImporting: Boolean = false

    fun importAudioFile(uri: Uri, fileName: String) {
        if (isImporting) {
            return // Prevent concurrent imports
        }
        
        viewModelScope.launch {
            try {
                isImporting = true
                _uiState.update { it.copy(isImporting = true, progress = 0, error = null) }
                
                _uiState.update { it.copy(progress = 50) }
                
                val recording: Recording = importAudioFileUseCase(uri, fileName)
                
                _uiState.update { 
                    it.copy(
                        isImporting = false,
                        progress = 100,
                        importedRecordingId = recording.id
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isImporting = false,
                        error = e.message ?: "Failed to import audio file"
                    )
                }
            } finally {
                isImporting = false
            }
        }
    }
    
    fun onImportHandled() {
        _uiState.update { it.copy(importedRecordingId = null) }
    }
}

