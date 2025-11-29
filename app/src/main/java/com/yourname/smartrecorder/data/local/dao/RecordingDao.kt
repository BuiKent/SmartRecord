package com.yourname.smartrecorder.data.local.dao

import androidx.room.*
import com.yourname.smartrecorder.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>
    
    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: String): RecordingEntity?
    
    @Query("SELECT * FROM recordings WHERE isPinned = 1 AND isArchived = 0 ORDER BY createdAt DESC")
    fun getPinnedRecordings(): Flow<List<RecordingEntity>>
    
    @Query("SELECT * FROM recordings WHERE isArchived = 1 ORDER BY createdAt DESC")
    fun getArchivedRecordings(): Flow<List<RecordingEntity>>
    
    @Query("SELECT * FROM recordings WHERE title LIKE '%' || :query || '%' OR id IN (SELECT DISTINCT recordingId FROM transcript_segments WHERE text LIKE '%' || :query || '%')")
    fun searchRecordings(query: String): Flow<List<RecordingEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity)
    
    @Update
    suspend fun updateRecording(recording: RecordingEntity)
    
    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)
    
    @Query("UPDATE recordings SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinnedStatus(id: String, isPinned: Boolean)
    
    @Query("UPDATE recordings SET isArchived = :isArchived WHERE id = :id")
    suspend fun updateArchivedStatus(id: String, isArchived: Boolean)
    
    @Query("SELECT * FROM recordings WHERE createdAt >= :startOfDay AND createdAt < :endOfDay AND isArchived = 0")
    suspend fun getRecordingsByDate(startOfDay: Long, endOfDay: Long): List<RecordingEntity>
}

