package com.yourname.smartrecorder.data.stt

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT

/**
 * WhisperPostProcessor: Post-processing for Whisper output
 * 
 * Features:
 * - English Heuristics: Text cleaning, formatting
 * - Question-based speaker detection: After question (?) → change speaker
 * - Time-gap speaker detection: Silence > 1.5s → change speaker
 * - Voice commands processing
 */
object WhisperPostProcessor {
    private const val TAG = "WhisperPostProcessor"
    
    // Filler words to remove
    private val FILLER_WORDS = listOf(
        "um", "uh", "er", "ah", "oh", "hum", "hmm", "hm", "eh", "huh", "mm", "mmm"
    )
    
    /**
     * Process Whisper output with English heuristics
     */
    fun processEnglishHeuristics(text: String): String {
        var processed = text.trim()
        if (processed.isBlank()) return processed
        
        // Step 1: Remove filler words
        val fillerRegex = Regex("\\b(${FILLER_WORDS.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
        processed = fillerRegex.replace(processed, "")
            .replace(Regex("\\s+"), " ") // Remove extra spaces
        
        // Step 2: Remove repeated words (stuttering)
        // "the the car" → "the car"
        processed = processed.replace(Regex("\\b(\\w+)\\s+\\1\\b", RegexOption.IGNORE_CASE), "$1")
        
        // Step 3: Grammar fixes
        // Fix "i" → "I"
        processed = processed.replace(Regex("\\bi\\b"), "I")
        processed = processed.replace(Regex("\\bi'(m|ll|ve|d)\\b"), "I'$1")
        
        // Step 4: Normalize units & currency
        processed = processed.replace(Regex("(\\d+)\\s+percent", RegexOption.IGNORE_CASE), "$1%")
        processed = processed.replace(Regex("(\\d+)\\s+dollars", RegexOption.IGNORE_CASE), "$$$1")
        processed = processed.replace(Regex("(\\d+)\\s+pounds", RegexOption.IGNORE_CASE), "£$1")
        
        return processed.trim()
    }
    
    /**
     * Process voice commands
     */
    fun processVoiceCommands(text: String): String {
        val voiceCommands = mapOf(
            "new line" to "\n",
            "comma" to ",",
            "period" to ".",
            "question mark" to "?",
            "exclamation mark" to "!",
            "colon" to ":",
            "semicolon" to ";"
        )
        
        var processed = text
        voiceCommands.forEach { (command, replacement) ->
            val regex = Regex("\\b$command\\b", RegexOption.IGNORE_CASE)
            processed = regex.replace(processed, replacement)
        }
        
        return processed
    }
    
    /**
     * Process with timestamps: Combine question rule (priority) and time gap (fallback)
     * Logic: OR condition - either question mark OR time gap > 1.5s → change speaker
     */
    fun processWithTimestamps(
        segments: List<WhisperEngine.WhisperSegment>,
        options: PostProcessingOptions = PostProcessingOptions()
    ): String {
        if (segments.isEmpty()) return ""
        
        // Step 1: Process each segment text with heuristics
        val processedSegments = segments.map { segment ->
            var processedText = segment.text.trim()
            
            // Voice commands
            if (options.processVoiceCommands) {
                processedText = processVoiceCommands(processedText)
            }
            
            // English heuristics
            processedText = processEnglishHeuristics(processedText)
            
            if (processedText.isBlank()) null
            else ProcessedSegment(processedText, segment.start, segment.end)
        }.filterNotNull()
        
        if (processedSegments.isEmpty()) return ""
        
        // Step 2: Determine speakers for each segment
        val speakerAssignments = mutableListOf<Int>()
        var currentSpeaker = 1
        var lastEndTime = 0.0
        
        processedSegments.forEachIndexed { index, segment ->
            val isQuestion = segment.text.trim().endsWith("?")
            val prevSegment = processedSegments.getOrNull(index - 1)
            val prevIsQuestion = prevSegment?.text?.trim()?.endsWith("?") ?: false
            
            // Calculate time gap (silence)
            val silenceGap = if (index > 0) segment.start - lastEndTime else 0.0
            val isLongPause = silenceGap > 1.5
            
            // Logic OR: Priority 1 = question mark, Priority 2 = time gap
            var shouldChangeSpeaker = false
            
            if (options.useQuestionRule && isQuestion) {
                // Priority 1: Question → change speaker
                shouldChangeSpeaker = true
            } else if (options.useTimeGap && isLongPause && !prevIsQuestion) {
                // Priority 2: Time gap > 1.5s (only if not after question)
                shouldChangeSpeaker = true
            } else if (options.useQuestionRule && prevIsQuestion && !isQuestion) {
                // After question, next sentence is not question → back to speaker 1
                shouldChangeSpeaker = true
            }
            
            // Change speaker if needed
            if (shouldChangeSpeaker && index > 0) {
                currentSpeaker = if (currentSpeaker == 1) 2 else 1
            }
            
            speakerAssignments.add(currentSpeaker)
            lastEndTime = segment.end
        }
        
        // Step 3: Build result
        val uniqueSpeakers = speakerAssignments.distinct()
        val hasMultipleSpeakers = uniqueSpeakers.size > 1
        val result = StringBuilder()
        
        if (hasMultipleSpeakers) {
            // Multiple speakers → add labels and line breaks
            var prevSpeaker = -1
            
            processedSegments.forEachIndexed { index, segment ->
                val speaker = speakerAssignments[index]
                
                if (speaker != prevSpeaker) {
                    if (index > 0) {
                        result.append("\n\n")
                    }
                    result.append("[Speaker $speaker]: ")
                } else if (index > 0) {
                    result.append("\n")
                }
                
                result.append(segment.text.trim())
                prevSpeaker = speaker
            }
        } else {
            // Single speaker → join all segments, no labels
            processedSegments.forEachIndexed { index, segment ->
                if (index > 0) {
                    result.append(" ")
                }
                result.append(segment.text.trim())
            }
        }
        
        return result.toString().trim()
    }
    
    private data class ProcessedSegment(
        val text: String,
        val start: Double,
        val end: Double
    )
}

/**
 * Post-processing options
 */
data class PostProcessingOptions(
    val useQuestionRule: Boolean = true,        // After question (?) → change speaker (priority)
    val useTimeGap: Boolean = true,             // Time gap > 1.5s → change speaker (fallback)
    val processVoiceCommands: Boolean = true,    // Process voice commands
    val removeFillers: Boolean = true            // Remove filler words (integrated in heuristics)
)

