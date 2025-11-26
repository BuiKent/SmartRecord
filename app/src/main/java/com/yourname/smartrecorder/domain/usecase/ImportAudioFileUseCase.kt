package com.yourname.smartrecorder.domain.usecase

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_IMPORT
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class ImportAudioFileUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository
) {
    suspend operator fun invoke(uri: Uri, fileName: String): Recording = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_IMPORT, "ImportAudioFileUseCase", "Starting", 
            mapOf("uri" to uri.toString(), "fileName" to fileName))
        
        val recordingsDir = File(context.filesDir, "recordings")
        recordingsDir.mkdirs()
        AppLogger.d(TAG_IMPORT, "Recordings directory prepared: %s", recordingsDir.absolutePath)
        
        // Copy file to app storage
        val recordingId = UUID.randomUUID().toString()
        val extension = fileName.substringAfterLast('.', "mp3")
        val outputFile = File(recordingsDir, "imported_${recordingId}.$extension")
        
        AppLogger.d(TAG_IMPORT, "Copying file -> from: %s, to: %s", uri.toString(), outputFile.absolutePath)
        
        val copyStartTime = System.currentTimeMillis()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            outputFile.outputStream().use { outputStream ->
                val bytesCopied = inputStream.copyTo(outputStream)
                val copyDuration = System.currentTimeMillis() - copyStartTime
                AppLogger.d(TAG_IMPORT, "File copied successfully -> bytes: %d, duration: %d ms", 
                    bytesCopied, copyDuration)
            }
        } ?: throw IllegalStateException("Failed to open file")
        
        val fileSize = outputFile.length()
        AppLogger.d(TAG_IMPORT, "File saved -> size: %d bytes, path: %s", fileSize, outputFile.absolutePath)
        
        // Get audio duration using MediaMetadataRetriever
        var durationMs = 0L
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(outputFile.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L
            retriever.release()
            AppLogger.d(TAG_IMPORT, "Audio duration extracted -> %d ms", durationMs)
        } catch (e: Exception) {
            AppLogger.e(TAG_IMPORT, "Failed to extract audio duration", e)
            durationMs = 0L
        }
        
        // Create recording entity
        val recording = Recording(
            id = recordingId,
            title = fileName.substringBeforeLast('.'),
            filePath = outputFile.absolutePath,
            createdAt = System.currentTimeMillis(),
            durationMs = durationMs,
            mode = "IMPORTED",
            isPinned = false,
            isArchived = false
        )
        
        AppLogger.d(TAG_IMPORT, "Saving recording to database -> id: %s, title: %s", 
            recording.id, recording.title)
        
        recordingRepository.insertRecording(recording)
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_IMPORT, "ImportAudioFileUseCase", "Completed", 
            mapOf("recordingId" to recordingId, "duration" to "${duration}ms"))
        AppLogger.logPerformance(TAG_IMPORT, "ImportAudioFileUseCase", duration, 
            "fileSize=${fileSize}bytes")
        
        recording
    }
}

