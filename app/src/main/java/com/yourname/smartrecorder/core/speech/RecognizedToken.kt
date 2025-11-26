package com.yourname.smartrecorder.core.speech

/**
 * Represents a recognized token (word) from speech recognition with confidence and alternatives.
 */
data class RecognizedToken(
    val text: String,                    // Main text
    val confidence: Float,                // Confidence score (0.0 - 1.0)
    val alternatives: List<String> = emptyList()  // Alternative words from top-2, top-3 results
)

