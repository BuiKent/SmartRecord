package com.yourname.smartrecorder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yourname.smartrecorder.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    
    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId ORDER BY timestampMs ASC")
    fun getBookmarksByRecordingId(recordingId: String): Flow<List<BookmarkEntity>>
    
    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId ORDER BY timestampMs ASC")
    suspend fun getBookmarksByRecordingIdSync(recordingId: String): List<BookmarkEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long
    
    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
    
    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmarkById(bookmarkId: Long)
    
    @Query("DELETE FROM bookmarks WHERE recordingId = :recordingId")
    suspend fun deleteBookmarksByRecordingId(recordingId: String)
    
    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId AND timestampMs BETWEEN :startMs AND :endMs ORDER BY timestampMs ASC")
    suspend fun getBookmarksInRange(recordingId: String, startMs: Long, endMs: Long): List<BookmarkEntity>
}

