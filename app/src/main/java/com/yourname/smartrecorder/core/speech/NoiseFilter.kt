package com.yourname.smartrecorder.core.speech

/**
 * Filters noise, filler words, and low-confidence tokens from recognition results.
 */
object NoiseFilter {
    private val fillerWords = setOf(
        "uh", "um", "er", "ah", "oh", "mm", "hmm", "mhm",
        "the the", "a a", "and and",
        "huh", "hah", "eh"
    )
    
    private const val MIN_CONFIDENCE_THRESHOLD = 0.35f
    
    fun clean(tokens: List<RecognizedToken>): List<RecognizedToken> {
        return tokens
            .let { filterTokens(it) }
            .let { removeRepeatedTokens(it) }
    }
    
    private fun filterTokens(tokens: List<RecognizedToken>): List<RecognizedToken> {
        return tokens.filter { token ->
            val normalized = token.text.lowercase().trim()
            
            // Remove filler words
            if (normalized in fillerWords) return@filter false
            
            // Remove very short tokens with low confidence
            if (normalized.length <= 1 && token.confidence < 0.5f) return@filter false
            
            // Remove low confidence tokens
            if (token.confidence < MIN_CONFIDENCE_THRESHOLD) return@filter false
            
            // Remove punctuation-only tokens
            if (normalized.replace(Regex("[^a-z0-9]"), "").isEmpty()) return@filter false
            
            true
        }
    }
    
    private fun removeRepeatedTokens(tokens: List<RecognizedToken>): List<RecognizedToken> {
        if (tokens.isEmpty()) return tokens
        
        val result = mutableListOf<RecognizedToken>()
        var lastText: String? = null
        
        for (token in tokens) {
            val currentText = token.text.lowercase().trim()
            if (currentText != lastText) {
                result.add(token)
                lastText = currentText
            }
        }
        
        return result
    }
}

