package com.yourname.smartrecorder.domain.usecase

import android.content.Context
import android.net.Uri
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class ImportAudioFileUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository
) {
    suspend operator fun invoke(uri: Uri, fileName: String): Recording = withContext(Dispatchers.IO) {
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
        recording
    }
}

