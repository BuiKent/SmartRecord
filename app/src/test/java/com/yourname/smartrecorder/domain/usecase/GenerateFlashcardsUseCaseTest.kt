package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.domain.model.Flashcard
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import com.yourname.smartrecorder.domain.repository.FlashcardRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.flow.flowOf

/**
 * Unit tests for GenerateFlashcardsUseCase
 * Note: This test uses mocking for FlashcardRepository
 */
class GenerateFlashcardsUseCaseTest {
    
    @Test
    fun `generate flashcards from questions`() = runBlocking {
        val mockRepository = mock<FlashcardRepository>()
        whenever(mockRepository.insertFlashcard(any())).thenReturn(1L)
        val useCase = GenerateFlashcardsUseCase(mockRepository)
        
        val segments = listOf(
            TranscriptSegment(
                id = 1,
                recordingId = "test-id",
                startTimeMs = 0,
                endTimeMs = 5000,
                text = "What is machine learning?",
                isQuestion = true
            ),
            TranscriptSegment(
                id = 2,
                recordingId = "test-id",
                startTimeMs = 5000,
                endTimeMs = 10000,
                text = "Machine learning is a subset of AI",
                isQuestion = false
            )
        )
        
        val flashcards = useCase("test-id", segments)
        
        assertTrue("Should generate flashcards from questions", flashcards.isNotEmpty())
        assertTrue("Should contain question text", 
            flashcards.any { it.question.contains("machine learning", ignoreCase = true) })
        
        // Verify repository was called
        verify(mockRepository, atLeastOnce()).insertFlashcard(any())
    }
    
    @Test
    fun `generate flashcards from segments ending with question mark`() = runBlocking {
        val mockRepository = mock<FlashcardRepository>()
        whenever(mockRepository.insertFlashcard(any())).thenReturn(1L)
        val useCase = GenerateFlashcardsUseCase(mockRepository)
        
        val segments = listOf(
            TranscriptSegment(
                id = 1,
                recordingId = "test-id",
                startTimeMs = 0,
                endTimeMs = 5000,
                text = "What is AI?",
                isQuestion = false // Not marked as question but ends with ?
            )
        )
        
        val flashcards = useCase("test-id", segments)
        
        assertTrue("Should generate flashcard from question mark", flashcards.isNotEmpty())
    }
    
    @Test
    fun `find answer for question in next segment`() = runBlocking {
        val mockRepository = mock<FlashcardRepository>()
        whenever(mockRepository.insertFlashcard(any())).thenReturn(1L)
        val useCase = GenerateFlashcardsUseCase(mockRepository)
        
        val segments = listOf(
            TranscriptSegment(
                id = 1,
                recordingId = "test-id",
                startTimeMs = 0,
                endTimeMs = 5000,
                text = "What is AI?",
                isQuestion = true
            ),
            TranscriptSegment(
                id = 2,
                recordingId = "test-id",
                startTimeMs = 5000,
                endTimeMs = 10000,
                text = "AI stands for Artificial Intelligence",
                isQuestion = false
            )
        )
        
        val flashcards = useCase("test-id", segments)
        
        assertTrue("Should find answer in next segment", 
            flashcards.any { it.answer.contains("Artificial Intelligence", ignoreCase = true) })
    }
    
    @Test
    fun `handle question without answer`() = runBlocking {
        val mockRepository = mock<FlashcardRepository>()
        whenever(mockRepository.insertFlashcard(any())).thenReturn(1L)
        val useCase = GenerateFlashcardsUseCase(mockRepository)
        
        val segments = listOf(
            TranscriptSegment(
                id = 1,
                recordingId = "test-id",
                startTimeMs = 0,
                endTimeMs = 5000,
                text = "What is AI?",
                isQuestion = true
            )
        )
        
        val flashcards = useCase("test-id", segments)
        
        assertTrue("Should generate flashcard even without answer", flashcards.isNotEmpty())
        assertTrue("Should have default answer message", 
            flashcards.any { it.answer.contains("not found", ignoreCase = true) })
    }
    
    @Test
    fun `handle empty segments`() = runBlocking {
        val mockRepository = mock<FlashcardRepository>()
        val useCase = GenerateFlashcardsUseCase(mockRepository)
        
        val flashcards = useCase("test-id", emptyList())
        
        assertTrue("Should return empty list for empty segments", flashcards.isEmpty())
        verify(mockRepository, never()).insertFlashcard(any())
    }
    
    @Test
    fun `flashcards have correct recording ID`() = runBlocking {
        val mockRepository = mock<FlashcardRepository>()
        whenever(mockRepository.insertFlashcard(any())).thenReturn(1L)
        val useCase = GenerateFlashcardsUseCase(mockRepository)
        
        val segments = listOf(
            TranscriptSegment(
                id = 1,
                recordingId = "test-id",
                startTimeMs = 0,
                endTimeMs = 5000,
                text = "What is AI?",
                isQuestion = true
            )
        )
        
        val flashcards = useCase("test-id", segments)
        
        flashcards.forEach { flashcard ->
            assertEquals("Flashcard should have correct recording ID", "test-id", flashcard.recordingId)
        }
    }
    
    @Test
    fun `flashcards have correct segment ID and timestamp`() = runBlocking {
        val mockRepository = mock<FlashcardRepository>()
        whenever(mockRepository.insertFlashcard(any())).thenReturn(1L)
        val useCase = GenerateFlashcardsUseCase(mockRepository)
        
        val segments = listOf(
            TranscriptSegment(
                id = 1,
                recordingId = "test-id",
                startTimeMs = 1000,
                endTimeMs = 5000,
                text = "What is AI?",
                isQuestion = true
            )
        )
        
        val flashcards = useCase("test-id", segments)
        
        flashcards.forEach { flashcard ->
            assertEquals("Flashcard should have correct segment ID", 1L, flashcard.segmentId)
            assertEquals("Flashcard should have correct timestamp", 1000L, flashcard.timestampMs)
        }
    }
}

