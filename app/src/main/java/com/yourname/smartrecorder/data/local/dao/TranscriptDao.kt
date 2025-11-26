package com.yourname.smartrecorder.data.local.dao

import androidx.room.*
import com.yourname.smartrecorder.data.local.entity.TranscriptSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcript_segments WHERE recordingId = :recordingId ORDER BY startTimeMs ASC")
    fun getTranscriptSegments(recordingId: String): Flow<List<TranscriptSegmentEntity>>
    
    @Query("SELECT * FROM transcript_segments WHERE recordingId = :recordingId ORDER BY startTimeMs ASC")
    suspend fun getTranscriptSegmentsSync(recordingId: String): List<TranscriptSegmentEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: TranscriptSegmentEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)
    
    @Query("DELETE FROM transcript_segments WHERE recordingId = :recordingId")
    suspend fun deleteSegmentsByRecordingId(recordingId: String)
    
    @Query("SELECT * FROM transcript_segments WHERE recordingId = :recordingId AND isQuestion = 1 ORDER BY startTimeMs ASC")
    fun getQuestions(recordingId: String): Flow<List<TranscriptSegmentEntity>>
}

