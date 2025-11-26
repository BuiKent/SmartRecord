package com.yourname.smartrecorder.data.repository

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
        transcriptDao.insertSegments(segments.map { it.toEntity() })
    }
    
    override fun getTranscriptSegments(recordingId: String): Flow<List<TranscriptSegment>> {
        return transcriptDao.getTranscriptSegments(recordingId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getTranscriptSegmentsSync(recordingId: String): List<TranscriptSegment> {
        return transcriptDao.getTranscriptSegmentsSync(recordingId).map { it.toDomain() }
    }
    
    override suspend fun deleteSegmentsByRecordingId(recordingId: String) {
        transcriptDao.deleteSegmentsByRecordingId(recordingId)
    }
    
    override fun getQuestions(recordingId: String): Flow<List<TranscriptSegment>> {
        return transcriptDao.getQuestions(recordingId).map { entities ->
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

