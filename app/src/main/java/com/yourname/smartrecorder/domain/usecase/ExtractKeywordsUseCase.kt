package com.yourname.smartrecorder.domain.usecase

import javax.inject.Inject

class ExtractKeywordsUseCase @Inject constructor() {
    operator fun invoke(fullText: String, topN: Int = 10): List<String> {
        // Simple keyword extraction - remove stopwords and get top frequent words
        val stopwords = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
            "been", "have", "has", "had", "do", "does", "did", "will", "would",
            "should", "could", "may", "might", "must", "can", "this", "that",
            "these", "those", "i", "you", "he", "she", "it", "we", "they",
            "me", "him", "her", "us", "them", "my", "your", "his", "her", "its",
            "our", "their", "what", "which", "who", "whom", "whose", "where",
            "when", "why", "how", "all", "each", "every", "both", "few", "more",
            "most", "other", "some", "such", "no", "nor", "not", "only", "own",
            "same", "so", "than", "too", "very", "just", "now"
        )
        
        val words = fullText.lowercase()
            .split(Regex("[^a-zA-Z]+"))
            .filter { it.length > 3 && it !in stopwords }
        
        val wordFreq = words.groupingBy { it }.eachCount()
        
        return wordFreq.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { it.key }
    }
}

