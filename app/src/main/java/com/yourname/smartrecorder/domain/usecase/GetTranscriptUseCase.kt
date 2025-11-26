package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import com.yourname.smartrecorder.domain.repository.TranscriptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class GetTranscriptUseCase @Inject constructor(
    private val transcriptRepository: TranscriptRepository
) {
    operator fun invoke(recordingId: String): Flow<List<TranscriptSegment>> {
        AppLogger.logUseCase(TAG_USECASE, "GetTranscriptUseCase", "Invoked", 
            mapOf("recordingId" to recordingId))
        return transcriptRepository.getTranscriptSegments(recordingId)
            .onEach { segments ->
                AppLogger.logUseCase(TAG_USECASE, "GetTranscriptUseCase", "Emitted", 
                    mapOf("recordingId" to recordingId, "count" to segments.size))
            }
    }
    
    suspend fun invokeSync(recordingId: String): List<TranscriptSegment> {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "GetTranscriptUseCase", "Invoked (sync)", 
            mapOf("recordingId" to recordingId))
        
        val segments = transcriptRepository.getTranscriptSegmentsSync(recordingId)
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_USECASE, "GetTranscriptUseCase", "Completed (sync)", 
            mapOf("recordingId" to recordingId, "count" to segments.size, "duration" to "${duration}ms"))
        
        return segments
    }
}

