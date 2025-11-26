package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Flashcard
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import com.yourname.smartrecorder.domain.repository.FlashcardRepository
import javax.inject.Inject

/**
 * Generate flashcards from transcript segments.
 * Uses rule-based approach to detect questions and create flashcards.
 */
class GenerateFlashcardsUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(
        recordingId: String,
        segments: List<TranscriptSegment>
    ): List<Flashcard> {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "GenerateFlashcardsUseCase", "Starting", 
            mapOf("recordingId" to recordingId, "segments" to segments.size))
        
        val flashcards = mutableListOf<Flashcard>()
        
        // Rule 1: Questions in transcript become flashcards
        segments.filter { it.isQuestion || it.text.trim().endsWith("?") }.forEach { segment ->
            val question = segment.text.trim()
            // Try to find answer in next segments (simple heuristic)
            val answer = findAnswerForQuestion(segments, segment)
            
            val flashcard = Flashcard(
                recordingId = recordingId,
                question = question,
                answer = answer,
                segmentId = segment.id,
                timestampMs = segment.startTimeMs
            )
            
            flashcards.add(flashcard)
            flashcardRepository.insertFlashcard(flashcard)
        }
        
        // Rule 2: Key concepts (sentences with important keywords)
        // This is a simplified version - can be enhanced
        val keywords = extractImportantKeywords(segments)
        segments.forEach { segment ->
            if (containsKeywords(segment.text, keywords)) {
                val question = "What is ${extractKeyConcept(segment.text)}?"
                val answer = segment.text
                
                val flashcard = Flashcard(
                    recordingId = recordingId,
                    question = question,
                    answer = answer,
                    segmentId = segment.id,
                    timestampMs = segment.startTimeMs
                )
                
                flashcards.add(flashcard)
                flashcardRepository.insertFlashcard(flashcard)
            }
        }
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_USECASE, "GenerateFlashcardsUseCase", "Completed", 
            mapOf("recordingId" to recordingId, "flashcards" to flashcards.size, "duration" to "${duration}ms"))
        AppLogger.logPerformance(TAG_USECASE, "GenerateFlashcardsUseCase", duration, 
            "flashcards=${flashcards.size}")
        
        return flashcards
    }
    
    private fun findAnswerForQuestion(segments: List<TranscriptSegment>, questionSegment: TranscriptSegment): String {
        // Simple heuristic: find the next segment after the question
        val questionIndex = segments.indexOfFirst { it.id == questionSegment.id }
        if (questionIndex >= 0 && questionIndex < segments.size - 1) {
            return segments[questionIndex + 1].text
        }
        return "Answer not found in transcript"
    }
    
    private fun extractImportantKeywords(segments: List<TranscriptSegment>): List<String> {
        val allText = segments.joinToString(" ") { it.text }
        val words = allText.lowercase()
            .split(Regex("[^a-zA-Z]+"))
            .filter { it.length > 4 }
        
        return words.groupingBy { it }.eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }
    
    private fun containsKeywords(text: String, keywords: List<String>): Boolean {
        val lowerText = text.lowercase()
        return keywords.any { keyword -> lowerText.contains(keyword) }
    }
    
    private fun extractKeyConcept(text: String): String {
        // Simple extraction: take first 5-10 words
        return text.split(Regex("\\s+"))
            .take(8)
            .joinToString(" ")
            .trim()
    }
}

