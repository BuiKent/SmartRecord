package com.yourname.smartrecorder.domain.repository

import com.yourname.smartrecorder.domain.model.Flashcard
import kotlinx.coroutines.flow.Flow

interface FlashcardRepository {
    fun getFlashcardsByRecordingId(recordingId: String): Flow<List<Flashcard>>
    suspend fun getFlashcardsByRecordingIdSync(recordingId: String): List<Flashcard>
    suspend fun getFlashcardsForReview(limit: Int = 20): List<Flashcard>
    suspend fun getFlashcardsByDifficulty(difficulty: Int, limit: Int = 20): List<Flashcard>
    suspend fun insertFlashcard(flashcard: Flashcard): Long
    suspend fun updateFlashcard(flashcard: Flashcard)
    suspend fun deleteFlashcard(flashcard: Flashcard)
    suspend fun deleteFlashcardById(flashcardId: Long)
    suspend fun deleteFlashcardsByRecordingId(recordingId: String)
    suspend fun getFlashcardCount(recordingId: String): Int
}

