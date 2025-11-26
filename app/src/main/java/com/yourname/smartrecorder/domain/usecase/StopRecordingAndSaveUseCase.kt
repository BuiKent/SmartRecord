package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import javax.inject.Inject

class StopRecordingAndSaveUseCase @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val recordingRepository: RecordingRepository,
    private val generateAutoTitle: GenerateAutoTitleUseCase
) {
    suspend operator fun invoke(recording: Recording, durationMs: Long): Recording {
        val file = audioRecorder.stopRecording()
        
        // Generate auto title if empty
        val title = if (recording.title.isBlank()) {
            // For now, use simple title. Later can use transcript to generate better title
            "Recording ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(recording.createdAt))}"
        } else {
            recording.title
        }
        
        val finalRecording = recording.copy(
            filePath = file.absolutePath,
            durationMs = durationMs,
            title = title
        )
        
        recordingRepository.insertRecording(finalRecording)
        
        return finalRecording
    }
}

