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
    
    /**
     * Full-text search in transcript segments.
     * Uses FTS4 virtual table for fast text search.
     */
    @Query("""
        SELECT DISTINCT s.* FROM transcript_segments s
        INNER JOIN transcript_segments_fts fts ON s.rowid = fts.rowid
        WHERE fts.text MATCH :query
        ORDER BY s.startTimeMs ASC
    """)
    suspend fun searchTranscripts(query: String): List<TranscriptSegmentEntity>
    
    /**
     * Full-text search in transcript segments for a specific recording.
     */
    @Query("""
        SELECT DISTINCT s.* FROM transcript_segments s
        INNER JOIN transcript_segments_fts fts ON s.rowid = fts.rowid
        WHERE s.recordingId = :recordingId AND fts.text MATCH :query
        ORDER BY s.startTimeMs ASC
    """)
    suspend fun searchTranscriptsInRecording(recordingId: String, query: String): List<TranscriptSegmentEntity>
    
    /**
     * Full-text search across all recordings.
     * Returns recordings that contain matching transcript segments.
     */
    @Query("""
        SELECT DISTINCT r.* FROM recordings r
        INNER JOIN transcript_segments s ON r.id = s.recordingId
        INNER JOIN transcript_segments_fts fts ON s.rowid = fts.rowid
        WHERE fts.text MATCH :query
        ORDER BY r.createdAt DESC
    """)
    suspend fun searchRecordingsByTranscript(query: String): List<com.yourname.smartrecorder.data.local.entity.RecordingEntity>
}

