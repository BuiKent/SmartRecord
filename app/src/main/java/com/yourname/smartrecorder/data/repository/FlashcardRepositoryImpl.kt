package com.yourname.smartrecorder.data.repository

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_REPOSITORY
import com.yourname.smartrecorder.data.local.dao.FlashcardDao
import com.yourname.smartrecorder.data.local.entity.FlashcardEntity
import com.yourname.smartrecorder.domain.model.Flashcard
import com.yourname.smartrecorder.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlashcardRepositoryImpl @Inject constructor(
    private val flashcardDao: FlashcardDao
) : FlashcardRepository {
    
    override fun getFlashcardsByRecordingId(recordingId: String): Flow<List<Flashcard>> {
        AppLogger.logFlow(TAG_REPOSITORY, "getFlashcardsByRecordingId", "Subscribed", "recordingId=$recordingId")
        return flashcardDao.getFlashcardsByRecordingId(recordingId).map { entities ->
            AppLogger.logFlow(TAG_REPOSITORY, "getFlashcardsByRecordingId", "Emitted", 
                "recordingId=$recordingId, count=${entities.size}")
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getFlashcardsByRecordingIdSync(recordingId: String): List<Flashcard> {
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT", "flashcards", "recordingId=$recordingId (sync)")
        val result = flashcardDao.getFlashcardsByRecordingIdSync(recordingId).map { it.toDomain() }
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT_COMPLETE", "flashcards", 
            "recordingId=$recordingId, count=${result.size}")
        return result
    }
    
    override suspend fun getFlashcardsForReview(limit: Int): List<Flashcard> {
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT", "flashcards", "forReview, limit=$limit")
        val result = flashcardDao.getFlashcardsForReview(limit).map { it.toDomain() }
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT_COMPLETE", "flashcards", 
            "forReview, count=${result.size}")
        return result
    }
    
    override suspend fun getFlashcardsByDifficulty(difficulty: Int, limit: Int): List<Flashcard> {
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT", "flashcards", "difficulty=$difficulty, limit=$limit")
        val result = flashcardDao.getFlashcardsByDifficulty(difficulty, limit).map { it.toDomain() }
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT_COMPLETE", "flashcards", 
            "difficulty=$difficulty, count=${result.size}")
        return result
    }
    
    override suspend fun insertFlashcard(flashcard: Flashcard): Long {
        AppLogger.logDatabase(TAG_REPOSITORY, "INSERT", "flashcards", 
            "recordingId=${flashcard.recordingId}, question=${flashcard.question.take(50)}")
        val id = flashcardDao.insertFlashcard(flashcard.toEntity())
        AppLogger.logDatabase(TAG_REPOSITORY, "INSERT_COMPLETE", "flashcards", "id=$id")
        return id
    }
    
    override suspend fun updateFlashcard(flashcard: Flashcard) {
        AppLogger.logDatabase(TAG_REPOSITORY, "UPDATE", "flashcards", "id=${flashcard.id}")
        flashcardDao.updateFlashcard(flashcard.toEntity())
        AppLogger.logDatabase(TAG_REPOSITORY, "UPDATE_COMPLETE", "flashcards", "id=${flashcard.id}")
    }
    
    override suspend fun deleteFlashcard(flashcard: Flashcard) {
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE", "flashcards", "id=${flashcard.id}")
        flashcardDao.deleteFlashcard(flashcard.toEntity())
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE_COMPLETE", "flashcards", "id=${flashcard.id}")
    }
    
    override suspend fun deleteFlashcardById(flashcardId: Long) {
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE", "flashcards", "id=$flashcardId")
        flashcardDao.deleteFlashcardById(flashcardId)
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE_COMPLETE", "flashcards", "id=$flashcardId")
    }
    
    override suspend fun deleteFlashcardsByRecordingId(recordingId: String) {
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE", "flashcards", "recordingId=$recordingId")
        flashcardDao.deleteFlashcardsByRecordingId(recordingId)
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE_COMPLETE", "flashcards", "recordingId=$recordingId")
    }
    
    override suspend fun getFlashcardCount(recordingId: String): Int {
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT", "flashcards", "count, recordingId=$recordingId")
        val count = flashcardDao.getFlashcardCount(recordingId)
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT_COMPLETE", "flashcards", 
            "recordingId=$recordingId, count=$count")
        return count
    }
    
    private fun Flashcard.toEntity(): FlashcardEntity {
        return FlashcardEntity(
            id = id,
            recordingId = recordingId,
            question = question,
            answer = answer,
            segmentId = segmentId,
            timestampMs = timestampMs,
            difficulty = difficulty,
            lastReviewed = lastReviewed,
            reviewCount = reviewCount,
            createdAt = createdAt
        )
    }
    
    private fun FlashcardEntity.toDomain(): Flashcard {
        return Flashcard(
            id = id,
            recordingId = recordingId,
            question = question,
            answer = answer,
            segmentId = segmentId,
            timestampMs = timestampMs,
            difficulty = difficulty,
            lastReviewed = lastReviewed,
            reviewCount = reviewCount,
            createdAt = createdAt
        )
    }
}

