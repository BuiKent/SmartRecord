package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import javax.inject.Inject

class GetRecordingDetailUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository
) {
    suspend operator fun invoke(recordingId: String): Recording? {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "GetRecordingDetailUseCase", "Starting", 
            mapOf("recordingId" to recordingId))
        
        val recording = recordingRepository.getRecording(recordingId)
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_USECASE, "GetRecordingDetailUseCase", "Completed", 
            mapOf("recordingId" to recordingId, "found" to (recording != null), "duration" to "${duration}ms"))
        
        return recording
    }
}

