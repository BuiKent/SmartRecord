package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import javax.inject.Inject

class UpdateRecordingTitleUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository
) {
    suspend operator fun invoke(recording: Recording, newTitle: String): Recording {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "UpdateRecordingTitleUseCase", "Starting", 
            mapOf("recordingId" to recording.id, "newTitle" to newTitle))
        
        val updatedRecording = recording.copy(title = newTitle.trim())
        recordingRepository.updateRecording(updatedRecording)
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_USECASE, "UpdateRecordingTitleUseCase", "Completed", 
            mapOf("recordingId" to recording.id, "duration" to "${duration}ms"))
        
        return updatedRecording
    }
}

