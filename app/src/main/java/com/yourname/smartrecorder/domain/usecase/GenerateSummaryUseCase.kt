package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import javax.inject.Inject

class GenerateSummaryUseCase @Inject constructor() {
    operator fun invoke(fullText: String): String {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "GenerateSummaryUseCase", "Starting", 
            mapOf("textLength" to fullText.length))
        
        // Simple rule-based summary
        val sentences = fullText.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        if (sentences.isEmpty()) {
            AppLogger.d(TAG_USECASE, "No sentences found, returning empty summary")
            return ""
        }
        
        AppLogger.d(TAG_USECASE, "Found %d sentences", sentences.size)
        
        // Look for summary cues
        val summaryCues = listOf(
            "tóm lại", "kết luận", "vì vậy", "do đó", "như vậy",
            "summary", "conclusion", "therefore", "thus", "in conclusion"
        )
        
        val importantSentences = sentences.filter { sentence ->
            summaryCues.any { cue ->
                sentence.lowercase().contains(cue.lowercase())
            }
        }
        
        // If no cues found, take first 3 sentences
        val selectedSentences = if (importantSentences.isNotEmpty()) {
            AppLogger.d(TAG_USECASE, "Using %d important sentences (found cues)", importantSentences.size)
            importantSentences.take(3)
        } else {
            AppLogger.d(TAG_USECASE, "No summary cues found, using first 3 sentences")
            sentences.take(3)
        }
        
        val summary = selectedSentences.joinToString(". ") + if (selectedSentences.isNotEmpty()) "." else ""
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_USECASE, "GenerateSummaryUseCase", "Completed", 
            mapOf("summaryLength" to summary.length, "sentences" to selectedSentences.size, "duration" to "${duration}ms"))
        AppLogger.logPerformance(TAG_USECASE, "GenerateSummaryUseCase", duration, 
            "textLength=${fullText.length}, summaryLength=${summary.length}")
        
        return summary
    }
}

