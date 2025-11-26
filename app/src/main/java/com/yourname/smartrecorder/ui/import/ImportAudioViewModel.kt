package com.yourname.smartrecorder.ui.import

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

data class ImportUiState(
    val isImporting: Boolean = false,
    val progress: Int = 0,
    val error: String? = null,
    val importedRecordingId: String? = null
)

@HiltViewModel
class ImportAudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun importAudioFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isImporting = true, progress = 0, error = null) }
                
                val recordingsDir = File(context.filesDir, "recordings")
                recordingsDir.mkdirs()
                
                // Copy file to app storage
                val recordingId = UUID.randomUUID().toString()
                val extension = fileName.substringAfterLast('.', "mp3")
                val outputFile = File(recordingsDir, "imported_${recordingId}.$extension")
                
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    outputFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw IllegalStateException("Failed to open file")
                
                _uiState.update { it.copy(progress = 50) }
                
                // Create recording entity
                val recording = Recording(
                    id = recordingId,
                    title = fileName.substringBeforeLast('.'),
                    filePath = outputFile.absolutePath,
                    createdAt = System.currentTimeMillis(),
                    durationMs = 0L, // TODO: Get actual duration
                    mode = "IMPORTED",
                    isPinned = false,
                    isArchived = false
                )
                
                recordingRepository.insertRecording(recording)
                
                _uiState.update { 
                    it.copy(
                        isImporting = false,
                        progress = 100,
                        importedRecordingId = recordingId
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isImporting = false,
                        error = e.message
                    )
                }
            }
        }
    }
    
    fun onImportHandled() {
        _uiState.update { it.copy(importedRecordingId = null) }
    }
}

