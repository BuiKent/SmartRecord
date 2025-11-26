package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.domain.model.TranscriptSegment
import com.yourname.smartrecorder.domain.repository.TranscriptRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTranscriptUseCase @Inject constructor(
    private val transcriptRepository: TranscriptRepository
) {
    operator fun invoke(recordingId: String): Flow<List<TranscriptSegment>> {
        return transcriptRepository.getTranscriptSegments(recordingId)
    }
    
    suspend fun invokeSync(recordingId: String): List<TranscriptSegment> {
        return transcriptRepository.getTranscriptSegmentsSync(recordingId)
    }
}

