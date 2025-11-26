package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.domain.model.Recording
import java.io.File
import java.util.UUID
import javax.inject.Inject

class StartRecordingUseCase @Inject constructor(
    private val audioRecorder: AudioRecorder
) {
    suspend operator fun invoke(outputDir: File): Recording {
        val recordingId = UUID.randomUUID().toString()
        val outputFile = File(outputDir, "recording_${recordingId}.3gp")
        
        audioRecorder.startRecording(outputFile)
        
        return Recording(
            id = recordingId,
            title = "",
            filePath = outputFile.absolutePath,
            createdAt = System.currentTimeMillis(),
            durationMs = 0L,
            mode = "DEFAULT",
            isPinned = false,
            isArchived = false
        )
    }
}

