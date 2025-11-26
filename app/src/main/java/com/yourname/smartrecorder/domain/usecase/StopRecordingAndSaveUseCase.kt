package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import javax.inject.Inject

class StopRecordingAndSaveUseCase @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val recordingRepository: RecordingRepository
) {
    suspend operator fun invoke(recording: Recording, durationMs: Long): Recording {
        val file = audioRecorder.stopRecording()
        
        val finalRecording = recording.copy(
            filePath = file.absolutePath,
            durationMs = durationMs,
            title = recording.title.ifBlank { "Recording ${System.currentTimeMillis()}" }
        )
        
        recordingRepository.insertRecording(finalRecording)
        
        return finalRecording
    }
}

