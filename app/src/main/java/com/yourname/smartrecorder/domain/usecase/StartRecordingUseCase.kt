package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject

class StartRecordingUseCase @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val recordingRepository: RecordingRepository
) {
    suspend operator fun invoke(outputDir: File): Recording {
        val startTime = System.currentTimeMillis()
        val recordingId = UUID.randomUUID().toString()
        
        // Find next available file number
        val nextFileNumber = getNextFileNumber(outputDir)
        val outputFile = File(outputDir, "recording_%03d.3gp".format(nextFileNumber))
        
        AppLogger.logUseCase(TAG_USECASE, "StartRecordingUseCase", "Starting", 
            mapOf("recordingId" to recordingId, "outputDir" to outputDir.absolutePath))
        
        audioRecorder.startRecording(outputFile)
        
        val recording = Recording(
            id = recordingId,
            title = "",
            filePath = outputFile.absolutePath,
            createdAt = System.currentTimeMillis(),
            durationMs = 0L,
            mode = "DEFAULT",
            isPinned = false,
            isArchived = false
        )
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_USECASE, "StartRecordingUseCase", "Completed", 
            mapOf("recordingId" to recordingId, "duration" to "${duration}ms", "fileName" to outputFile.name))
        AppLogger.logPerformance(TAG_USECASE, "StartRecordingUseCase", duration)
        
        return recording
    }
    
    private suspend fun getNextFileNumber(outputDir: File): Int {
        // Get all existing recording files
        val existingFiles = outputDir.listFiles { file ->
            file.name.startsWith("recording_") && file.name.endsWith(".3gp")
        } ?: emptyArray()
        
        // Extract numbers from filenames
        val numbers = existingFiles.mapNotNull { file ->
            val name = file.name
            val numberStr = name.removePrefix("recording_").removeSuffix(".3gp")
            numberStr.toIntOrNull()
        }
        
        // Find next available number
        return if (numbers.isEmpty()) {
            1
        } else {
            (numbers.maxOrNull() ?: 0) + 1
        }
    }
}

