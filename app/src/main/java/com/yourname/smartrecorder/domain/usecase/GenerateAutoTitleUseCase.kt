package com.yourname.smartrecorder.domain.usecase

import javax.inject.Inject

class GenerateAutoTitleUseCase @Inject constructor(
    private val extractKeywords: ExtractKeywordsUseCase
) {
    operator fun invoke(fullText: String, timestamp: Long): String {
        if (fullText.isBlank()) {
            return "Recording ${formatDate(timestamp)}"
        }
        
        val keywords = extractKeywords(fullText, topN = 5)
        val dateStr = formatDate(timestamp)
        
        return if (keywords.isNotEmpty()) {
            keywords.take(3).joinToString(" - ") + " - $dateStr"
        } else {
            "Recording - $dateStr"
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

