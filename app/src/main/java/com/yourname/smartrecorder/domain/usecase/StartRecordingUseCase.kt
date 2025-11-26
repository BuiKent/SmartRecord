package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Recording
import java.io.File
import java.util.UUID
import javax.inject.Inject

class StartRecordingUseCase @Inject constructor(
    private val audioRecorder: AudioRecorder
) {
    suspend operator fun invoke(outputDir: File): Recording {
        val startTime = System.currentTimeMillis()
        val recordingId = UUID.randomUUID().toString()
        val outputFile = File(outputDir, "recording_${recordingId}.3gp")
        
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
            mapOf("recordingId" to recordingId, "duration" to "${duration}ms"))
        AppLogger.logPerformance(TAG_USECASE, "StartRecordingUseCase", duration)
        
        return recording
    }
}

