package com.yourname.smartrecorder.data.repository

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_REPOSITORY
import com.yourname.smartrecorder.data.local.dao.TranscriptDao
import com.yourname.smartrecorder.data.local.entity.TranscriptSegmentEntity
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import com.yourname.smartrecorder.domain.repository.TranscriptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptRepositoryImpl @Inject constructor(
    private val transcriptDao: TranscriptDao
) : TranscriptRepository {
    
    override suspend fun saveTranscriptSegments(
        recordingId: String,
        segments: List<TranscriptSegment>
    ) {
        val startTime = System.currentTimeMillis()
        AppLogger.logDatabase(TAG_REPOSITORY, "INSERT", "transcript_segments", 
            "recordingId=$recordingId, count=${segments.size}")
        
        transcriptDao.insertSegments(segments.map { it.toEntity() })
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logDatabase(TAG_REPOSITORY, "INSERT_COMPLETE", "transcript_segments", 
            "recordingId=$recordingId, count=${segments.size}, duration=${duration}ms")
    }
    
    override fun getTranscriptSegments(recordingId: String): Flow<List<TranscriptSegment>> {
        AppLogger.logFlow(TAG_REPOSITORY, "getTranscriptSegments", "Subscribed", "recordingId=$recordingId")
        return transcriptDao.getTranscriptSegments(recordingId).map { entities ->
            AppLogger.logFlow(TAG_REPOSITORY, "getTranscriptSegments", "Emitted", 
                "recordingId=$recordingId, count=${entities.size}")
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getTranscriptSegmentsSync(recordingId: String): List<TranscriptSegment> {
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT", "transcript_segments", "recordingId=$recordingId (sync)")
        val result = transcriptDao.getTranscriptSegmentsSync(recordingId).map { it.toDomain() }
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT_COMPLETE", "transcript_segments", 
            "recordingId=$recordingId, count=${result.size}")
        return result
    }
    
    override suspend fun deleteSegmentsByRecordingId(recordingId: String) {
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE", "transcript_segments", "recordingId=$recordingId")
        transcriptDao.deleteSegmentsByRecordingId(recordingId)
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE_COMPLETE", "transcript_segments", "recordingId=$recordingId")
    }
    
    override fun getQuestions(recordingId: String): Flow<List<TranscriptSegment>> {
        AppLogger.logFlow(TAG_REPOSITORY, "getQuestions", "Subscribed", "recordingId=$recordingId")
        return transcriptDao.getQuestions(recordingId).map { entities ->
            AppLogger.logFlow(TAG_REPOSITORY, "getQuestions", "Emitted", 
                "recordingId=$recordingId, count=${entities.size}")
            entities.map { it.toDomain() }
        }
    }
    
    private fun TranscriptSegment.toEntity(): TranscriptSegmentEntity {
        return TranscriptSegmentEntity(
            id = id,
            recordingId = recordingId,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            text = text,
            isQuestion = isQuestion
        )
    }
    
    private fun TranscriptSegmentEntity.toDomain(): TranscriptSegment {
        return TranscriptSegment(
            id = id,
            recordingId = recordingId,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            text = text,
            isQuestion = isQuestion
        )
    }
}

