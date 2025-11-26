package com.yourname.smartrecorder.domain.usecase

import javax.inject.Inject

class GenerateSummaryUseCase @Inject constructor() {
    operator fun invoke(fullText: String): String {
        // Simple rule-based summary
        val sentences = fullText.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        if (sentences.isEmpty()) return ""
        
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
            importantSentences.take(3)
        } else {
            sentences.take(3)
        }
        
        return selectedSentences.joinToString(". ") + if (selectedSentences.isNotEmpty()) "." else ""
    }
}

