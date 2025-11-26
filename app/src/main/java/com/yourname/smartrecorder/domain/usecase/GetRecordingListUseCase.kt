package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRecordingListUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository
) {
    operator fun invoke(): Flow<List<Recording>> {
        return recordingRepository.getRecordingsFlow()
    }
}

