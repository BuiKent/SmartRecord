package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Flashcard
import com.yourname.smartrecorder.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class GetFlashcardsUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository
) {
    operator fun invoke(recordingId: String): Flow<List<Flashcard>> {
        AppLogger.logUseCase(TAG_USECASE, "GetFlashcardsUseCase", "Invoked", 
            mapOf("recordingId" to recordingId))
        return flashcardRepository.getFlashcardsByRecordingId(recordingId)
            .onEach { flashcards ->
                AppLogger.logUseCase(TAG_USECASE, "GetFlashcardsUseCase", "Emitted", 
                    mapOf("recordingId" to recordingId, "count" to flashcards.size))
            }
    }
    
    suspend fun getForReview(limit: Int = 20): List<Flashcard> {
        AppLogger.logUseCase(TAG_USECASE, "GetFlashcardsUseCase", "getForReview", 
            mapOf("limit" to limit))
        return flashcardRepository.getFlashcardsForReview(limit)
    }
}

