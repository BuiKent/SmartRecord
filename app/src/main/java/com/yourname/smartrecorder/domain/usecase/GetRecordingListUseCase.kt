package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class GetRecordingListUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository
) {
    operator fun invoke(): Flow<List<Recording>> {
        AppLogger.logUseCase(TAG_USECASE, "GetRecordingListUseCase", "Invoked", null)
        return recordingRepository.getRecordingsFlow()
            .onEach { recordings ->
                AppLogger.logUseCase(TAG_USECASE, "GetRecordingListUseCase", "Emitted", 
                    mapOf("count" to recordings.size))
            }
    }
}

