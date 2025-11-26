package com.yourname.smartrecorder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yourname.smartrecorder.data.local.entity.FlashcardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    
    @Query("SELECT * FROM flashcards WHERE recordingId = :recordingId ORDER BY createdAt DESC")
    fun getFlashcardsByRecordingId(recordingId: String): Flow<List<FlashcardEntity>>
    
    @Query("SELECT * FROM flashcards WHERE recordingId = :recordingId ORDER BY createdAt DESC")
    suspend fun getFlashcardsByRecordingIdSync(recordingId: String): List<FlashcardEntity>
    
    @Query("SELECT * FROM flashcards ORDER BY lastReviewed ASC NULLS FIRST, createdAt DESC LIMIT :limit")
    suspend fun getFlashcardsForReview(limit: Int = 20): List<FlashcardEntity>
    
    @Query("SELECT * FROM flashcards WHERE difficulty = :difficulty ORDER BY lastReviewed ASC NULLS FIRST LIMIT :limit")
    suspend fun getFlashcardsByDifficulty(difficulty: Int, limit: Int = 20): List<FlashcardEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: FlashcardEntity): Long
    
    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)
    
    @Delete
    suspend fun deleteFlashcard(flashcard: FlashcardEntity)
    
    @Query("DELETE FROM flashcards WHERE id = :flashcardId")
    suspend fun deleteFlashcardById(flashcardId: Long)
    
    @Query("DELETE FROM flashcards WHERE recordingId = :recordingId")
    suspend fun deleteFlashcardsByRecordingId(recordingId: String)
    
    @Query("SELECT COUNT(*) FROM flashcards WHERE recordingId = :recordingId")
    suspend fun getFlashcardCount(recordingId: String): Int
}

