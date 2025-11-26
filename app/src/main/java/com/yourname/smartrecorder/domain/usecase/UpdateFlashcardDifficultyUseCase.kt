package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Flashcard
import com.yourname.smartrecorder.domain.repository.FlashcardRepository
import javax.inject.Inject

class UpdateFlashcardDifficultyUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(
        flashcard: Flashcard,
        difficulty: Int, // 0 = new, 1 = easy, 2 = medium, 3 = hard
        isCorrect: Boolean
    ) {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "UpdateFlashcardDifficultyUseCase", "Starting", 
            mapOf("flashcardId" to flashcard.id, "difficulty" to difficulty, "isCorrect" to isCorrect))
        
        val updatedFlashcard = flashcard.copy(
            difficulty = difficulty,
            lastReviewed = System.currentTimeMillis(),
            reviewCount = flashcard.reviewCount + 1
        )
        
        flashcardRepository.updateFlashcard(updatedFlashcard)
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_USECASE, "UpdateFlashcardDifficultyUseCase", "Completed", 
            mapOf("flashcardId" to flashcard.id, "duration" to "${duration}ms"))
    }
}

