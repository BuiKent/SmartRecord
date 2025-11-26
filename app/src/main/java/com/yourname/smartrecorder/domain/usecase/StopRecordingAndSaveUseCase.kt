package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import javax.inject.Inject

class StopRecordingAndSaveUseCase @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val recordingRepository: RecordingRepository,
    private val generateAutoTitle: GenerateAutoTitleUseCase
) {
    suspend operator fun invoke(recording: Recording, durationMs: Long): Recording {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "StopRecordingAndSaveUseCase", "Starting", 
            mapOf("recordingId" to recording.id, "durationMs" to durationMs))
        
        val file = audioRecorder.stopRecording()
        val fileSize = file.length()
        AppLogger.d(TAG_USECASE, "Audio file saved -> size: %d bytes, path: %s", fileSize, file.absolutePath)
        
        // Generate auto title if empty
        val title = if (recording.title.isBlank()) {
            // For now, use simple title. Later can use transcript to generate better title
            val simpleTitle = "Recording ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(recording.createdAt))}"
            AppLogger.d(TAG_USECASE, "Generated auto title: %s", simpleTitle)
            simpleTitle
        } else {
            recording.title
        }
        
        val finalRecording = recording.copy(
            filePath = file.absolutePath,
            durationMs = durationMs,
            title = title
        )
        
        AppLogger.d(TAG_USECASE, "Saving recording to database -> id: %s, title: %s, duration: %d ms", 
            finalRecording.id, finalRecording.title, finalRecording.durationMs)
        
        recordingRepository.insertRecording(finalRecording)
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_USECASE, "StopRecordingAndSaveUseCase", "Completed", 
            mapOf("recordingId" to finalRecording.id, "duration" to "${duration}ms"))
        AppLogger.logPerformance(TAG_USECASE, "StopRecordingAndSaveUseCase", duration, 
            "fileSize=${fileSize}bytes, durationMs=${durationMs}ms")
        
        return finalRecording
    }
}

