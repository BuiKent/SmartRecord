package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import javax.inject.Inject

class GetRecordingDetailUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository
) {
    suspend operator fun invoke(recordingId: String): Recording? {
        return recordingRepository.getRecording(recordingId)
    }
}

