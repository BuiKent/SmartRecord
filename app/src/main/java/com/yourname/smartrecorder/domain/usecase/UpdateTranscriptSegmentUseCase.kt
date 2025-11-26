package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import com.yourname.smartrecorder.domain.repository.TranscriptRepository
import javax.inject.Inject

class UpdateTranscriptSegmentUseCase @Inject constructor(
    private val transcriptRepository: TranscriptRepository
) {
    suspend operator fun invoke(segment: TranscriptSegment): Result<Unit> {
        return try {
            AppLogger.d(TAG_USECASE, "Updating transcript segment -> segmentId: %d, text: %s", 
                segment.id, segment.text.take(50))
            
            transcriptRepository.updateSegment(segment)
            
            AppLogger.d(TAG_USECASE, "Transcript segment updated successfully -> segmentId: %d", segment.id)
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG_USECASE, "Failed to update transcript segment", e, 
                "segmentId=${segment.id}")
            Result.failure(e)
        }
    }
}

