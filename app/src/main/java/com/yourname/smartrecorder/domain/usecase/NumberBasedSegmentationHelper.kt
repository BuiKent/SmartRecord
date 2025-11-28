package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import com.yourname.smartrecorder.domain.model.TranscriptSegment

/**
 * Helper class for number-based speaker segmentation.
 * Detects section headers based on patterns like "Number one", "Number two", etc.
 * Uses a scoring-based approach to distinguish actual headers from false positives.
 */
object NumberBasedSegmentationHelper {
    
    /**
     * Represents a word with timestamp (approximated from segment)
     */
    data class Word(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val charOffset: Int = 0  // Character offset in original segment
    )
    
    /**
     * Pattern types for heading detection
     */
    enum class PatternType {
        NUMBER,    // "number one", "number 1"
        SPEAKER,   // "speaker one", "speaker 1" (highest confidence)
        PART,      // "part one", "part 1"
        SECTION,   // "section one", "section 1"
        POINT,     // "point one", "point 1"
        ITEM,      // "item one", "item 1"
        TOPIC,     // "topic one", "topic 1"
        FIRST,     // "first point", "first part"
        SECOND,    // "second point", "second part"
        THIRD      // "third point", "third part"
    }
    
    /**
     * Candidate heading detected in transcript
     */
    data class HeadingCandidate(
        val indexStart: Int,           // Word index where pattern starts
        val indexEnd: Int,             // Word index where pattern ends
        val number: Int,               // 1, 2, 3, ...
        val startMs: Long,             // Timestamp of candidate
        val endMs: Long,
        val patternType: PatternType,
        val matchText: String,         // Original matched text
        var score: Int = 0,            // Confidence score
        var features: FeatureScores = FeatureScores()  // Detailed scoring breakdown
    )
    
    /**
     * Detailed feature scores for debugging
     */
    data class FeatureScores(
        val positionScore: Int = 0,        // At start of sentence/segment
        val pauseAfterScore: Int = 0,      // Long pause after candidate
        val punctuationScore: Int = 0,     // Heading-style punctuation
        val negativeContextScore: Int = 0,  // Penalty for negative context
        val sequenceScore: Int = 0,         // Part of valid sequence
        val contentLengthScore: Int = 0     // Long content after candidate
    )
    
    /**
     * Final section after segmentation
     */
    data class Section(
        val index: Int,              // 1, 2, 3, ...
        val number: Int,             // From heading (1, 2, 3, ...)
        val startMs: Long,
        val endMs: Long,
        val wordStartIndex: Int,
        val wordEndIndex: Int,
        val patternType: PatternType,
        val headingText: String     // Original heading text
    )
    
    /**
     * Configuration for scoring thresholds
     */
    data class SegmentationConfig(
        val minScore: Int = 2,              // Minimum score to be considered heading
        val speakerPatternAutoAccept: Boolean = true,  // "Speaker X" always accepted
        val requireSequence: Boolean = false,  // Require at least 2 candidates in sequence
        val minSequenceLength: Int = 2,        // Minimum sequence length if requireSequence=true
        val longPauseThreshold: Long = 800,    // Long pause threshold in ms
        val mediumPauseThreshold: Long = 500, // Medium pause threshold in ms
        val minContentLength: Long = 15_000,  // Minimum content length in ms
        val minSequenceGap: Long = 5_000,     // Minimum gap between sequence items (5s)
        val maxSequenceGap: Long = 3_600_000  // Maximum gap between sequence items (1h)
    )
    
    /**
     * Extract words from segments with approximated timestamps.
     * Since Whisper only provides segment-level timestamps, we distribute
     * the segment duration evenly across words.
     */
    fun extractWordsFromSegments(segments: List<TranscriptSegment>): List<Word> {
        val words = mutableListOf<Word>()
        var charOffset = 0
        
        segments.forEach { segment ->
            val segmentText = segment.text.trim()
            if (segmentText.isEmpty()) return@forEach
            
            val segmentDuration = segment.endTimeMs - segment.startTimeMs
            val wordTokens = segmentText.split(Regex("\\s+")).filter { it.isNotBlank() }
            
            if (wordTokens.isEmpty()) return@forEach
            
            val avgWordDuration = if (wordTokens.size > 1) {
                segmentDuration / wordTokens.size
            } else {
                segmentDuration
            }
            
            var currentTime = segment.startTimeMs
            
            wordTokens.forEachIndexed { index, token ->
                val wordStart = currentTime
                val wordEnd = if (index < wordTokens.size - 1) {
                    currentTime + avgWordDuration
                } else {
                    segment.endTimeMs  // Last word ends at segment end
                }
                
                words.add(
                    Word(
                        text = token.trim(),
                        startMs = wordStart,
                        endMs = wordEnd,
                        charOffset = charOffset
                    )
                )
                
                charOffset += token.length + 1  // +1 for space
                currentTime = wordEnd
            }
        }
        
        AppLogger.d(TAG_TRANSCRIPT, "[NumberBasedSegmentation] Extracted %d words from %d segments", 
            words.size, segments.size)
        
        return words
    }
    
    /**
     * Normalize number word to integer
     */
    private fun normalizeNumberWord(token: String): Int? {
        val cleaned = token.lowercase().trim('.', ',', ':', '-', '?', '!', '"', '\'')
        return when (cleaned) {
            "one", "1st", "first" -> 1
            "two", "2nd", "second" -> 2
            "three", "3rd", "third" -> 3
            "four", "4th", "fourth" -> 4
            "five", "5th", "fifth" -> 5
            "six", "6th", "sixth" -> 6
            "seven", "7th", "seventh" -> 7
            "eight", "8th", "eighth" -> 8
            "nine", "9th", "ninth" -> 9
            "ten", "10th", "tenth" -> 10
            "eleven", "11th", "eleventh" -> 11
            "twelve", "12th", "twelfth" -> 12
            else -> cleaned.toIntOrNull()
        }
    }
    
    /**
     * Pattern regexes for detection
     */
    private val patternRegexes = mapOf(
        PatternType.NUMBER to Regex(
            """\b(number|no|num)\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""",
            RegexOption.IGNORE_CASE
        ),
        PatternType.SPEAKER to Regex(
            """\bspeaker\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""",
            RegexOption.IGNORE_CASE
        ),
        PatternType.PART to Regex(
            """\bpart\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""",
            RegexOption.IGNORE_CASE
        ),
        PatternType.SECTION to Regex(
            """\bsection\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""",
            RegexOption.IGNORE_CASE
        ),
        PatternType.POINT to Regex(
            """\bpoint\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""",
            RegexOption.IGNORE_CASE
        ),
        PatternType.ITEM to Regex(
            """\bitem\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""",
            RegexOption.IGNORE_CASE
        ),
        PatternType.TOPIC to Regex(
            """\btopic\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""",
            RegexOption.IGNORE_CASE
        ),
        PatternType.FIRST to Regex(
            """\b(first|1st)\s+(point|part|section|item)\b""",
            RegexOption.IGNORE_CASE
        ),
        PatternType.SECOND to Regex(
            """\b(second|2nd)\s+(point|part|section|item)\b""",
            RegexOption.IGNORE_CASE
        ),
        PatternType.THIRD to Regex(
            """\b(third|3rd)\s+(point|part|section|item)\b""",
            RegexOption.IGNORE_CASE
        )
    )
    
    /**
     * Find all candidate headings in words
     */
    fun findCandidateHeadings(words: List<Word>): List<HeadingCandidate> {
        if (words.isEmpty()) return emptyList()
        
        val candidates = mutableListOf<HeadingCandidate>()
        val fullText = words.joinToString(" ") { it.text }
        
        patternRegexes.forEach { (patternType, regex) ->
            regex.findAll(fullText.lowercase()).forEach { match ->
                val matchText = match.value
                val numberToken = match.groupValues.getOrNull(2) ?: return@forEach
                
                val number = normalizeNumberWord(numberToken) ?: return@forEach
                
                // Find word indices for this match
                val charStart = match.range.first
                val charEnd = match.range.last + 1
                
                // Map character positions to word indices
                var currentChar = 0
                var wordStartIndex = -1
                var wordEndIndex = -1
                
                words.forEachIndexed { index, word ->
                    val wordStart = currentChar
                    val wordEnd = currentChar + word.text.length
                    
                    if (wordStartIndex == -1 && charStart >= wordStart && charStart < wordEnd) {
                        wordStartIndex = index
                    }
                    if (charEnd > wordStart && charEnd <= wordEnd) {
                        wordEndIndex = index
                    }
                    
                    currentChar = wordEnd + 1  // +1 for space
                }
                
                if (wordStartIndex >= 0 && wordEndIndex >= 0) {
                    val startWord = words[wordStartIndex]
                    val endWord = words[wordEndIndex]
                    
                    candidates.add(
                        HeadingCandidate(
                            indexStart = wordStartIndex,
                            indexEnd = wordEndIndex,
                            number = number,
                            startMs = startWord.startMs,
                            endMs = endWord.endMs,
                            patternType = patternType,
                            matchText = matchText
                        )
                    )
                }
            }
        }
        
        AppLogger.d(TAG_TRANSCRIPT, "[NumberBasedSegmentation] Found %d candidate headings", candidates.size)
        
        return candidates.sortedBy { it.startMs }
    }
    
    /**
     * Find segment containing a word index
     */
    private fun findSegmentForWord(
        wordIndex: Int,
        words: List<Word>,
        segments: List<TranscriptSegment>
    ): TranscriptSegment? {
        if (wordIndex < 0 || wordIndex >= words.size) return null
        
        val word = words[wordIndex]
        return segments.find { segment ->
            word.startMs >= segment.startTimeMs && word.endMs <= segment.endTimeMs
        }
    }
    
    /**
     * Calculate position score (at start of sentence/segment)
     */
    fun calculatePositionScore(
        candidate: HeadingCandidate,
        words: List<Word>,
        segments: List<TranscriptSegment>
    ): Int {
        val prevWord = words.getOrNull(candidate.indexStart - 1)
        
        // At start of file
        if (prevWord == null) {
            return 2
        }
        
        // After sentence-ending punctuation
        val prevText = prevWord.text.trim()
        if (prevText.endsWith(".") || prevText.endsWith("?") || prevText.endsWith("!")) {
            return 1
        }
        
        // Check if previous word is in different segment (pause indicator)
        val candidateSegment = findSegmentForWord(candidate.indexStart, words, segments)
        val prevSegment = findSegmentForWord(candidate.indexStart - 1, words, segments)
        
        if (candidateSegment != null && prevSegment != null && candidateSegment != prevSegment) {
            return 1  // New segment = potential new section
        }
        
        return 0
    }
    
    /**
     * Calculate pause after score
     */
    fun calculatePauseAfterScore(
        candidate: HeadingCandidate,
        words: List<Word>,
        config: SegmentationConfig = SegmentationConfig()
    ): Int {
        val nextWord = words.getOrNull(candidate.indexEnd + 1) ?: return 0
        
        val pauseAfter = nextWord.startMs - candidate.endMs
        
        return when {
            pauseAfter >= config.longPauseThreshold -> 2  // Long pause (high confidence)
            pauseAfter >= config.mediumPauseThreshold -> 1  // Medium pause
            else -> 0
        }
    }
    
    /**
     * Calculate punctuation score (heading style)
     */
    fun calculatePunctuationScore(
        candidate: HeadingCandidate,
        words: List<Word>
    ): Int {
        val endWord = words.getOrNull(candidate.indexEnd) ?: return 0
        val endText = endWord.text.trim()
        
        return when {
            endText.endsWith(":") || endText.endsWith("-") -> 2  // Strong heading indicator
            endText.endsWith(".") || endText.endsWith(",") -> 1  // Weak heading indicator
            else -> 0
        }
    }
    
    /**
     * Negative context words that indicate false positive
     */
    private val contextBlacklist = listOf(
        "phone", "phonenumber", "address", "time", "code", "password",
        "room", "chapter", "lesson", "level",
        "my", "your", "his", "her", "their", "our",
        "i'm", "im", "you're", "youre", "we're", "were",
        "is", "are", "was", "were", "has", "have"
    )
    
    /**
     * Calculate negative context score (penalty)
     */
    fun calculateNegativeContextScore(
        candidate: HeadingCandidate,
        words: List<Word>
    ): Int {
        val contextStart = maxOf(0, candidate.indexStart - 3)
        val contextEnd = minOf(words.size - 1, candidate.indexEnd + 3)
        
        for (i in contextStart..contextEnd) {
            val wordText = words[i].text.lowercase()
                .trim('.', ',', ':', '-', '?', '!', '"', '\'')
            
            if (contextBlacklist.contains(wordText)) {
                return -3  // Strong penalty
            }
        }
        
        return 0
    }
    
    /**
     * Calculate sequence score (part of valid sequence 1, 2, 3, ...)
     */
    fun calculateSequenceScore(
        candidates: List<HeadingCandidate>,
        config: SegmentationConfig = SegmentationConfig()
    ): Map<HeadingCandidate, Int> {
        val scores = mutableMapOf<HeadingCandidate, Int>()
        val sorted = candidates.sortedBy { it.startMs }
        
        // Check for valid sequences (1, 2, 3, ...)
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val cur = sorted[i]
            
            if (cur.number == prev.number + 1) {
                val gap = cur.startMs - prev.endMs
                
                // Valid sequence: numbers increase, reasonable time gap
                if (gap in config.minSequenceGap..config.maxSequenceGap) {
                    scores[prev] = (scores[prev] ?: 0) + 1
                    scores[cur] = (scores[cur] ?: 0) + 1
                }
            }
        }
        
        return scores
    }
    
    /**
     * Calculate content length score (long content after heading)
     */
    fun calculateContentLengthScore(
        candidate: HeadingCandidate,
        words: List<Word>,
        nextCandidate: HeadingCandidate?,
        config: SegmentationConfig = SegmentationConfig()
    ): Int {
        val contentEnd = nextCandidate?.startMs ?: words.lastOrNull()?.endMs ?: candidate.endMs
        val contentDuration = contentEnd - candidate.endMs
        
        // Long content after heading = more likely to be a real section
        return if (contentDuration >= config.minContentLength) {
            1
        } else {
            0
        }
    }
    
    /**
     * Score all candidates
     */
    fun scoreCandidates(
        candidates: List<HeadingCandidate>,
        words: List<Word>,
        segments: List<TranscriptSegment>,
        config: SegmentationConfig = SegmentationConfig()
    ): List<HeadingCandidate> {
        // Calculate sequence scores first (needs all candidates)
        val sequenceScores = calculateSequenceScore(candidates, config)
        
        val scored = candidates.map { candidate ->
            val features = FeatureScores(
                positionScore = calculatePositionScore(candidate, words, segments),
                pauseAfterScore = calculatePauseAfterScore(candidate, words, config),
                punctuationScore = calculatePunctuationScore(candidate, words),
                negativeContextScore = calculateNegativeContextScore(candidate, words),
                sequenceScore = sequenceScores[candidate] ?: 0,
                contentLengthScore = calculateContentLengthScore(
                    candidate,
                    words,
                    candidates.find { it.startMs > candidate.startMs },
                    config
                )
            )
            
            // Calculate total score
            val totalScore = features.positionScore +
                    features.pauseAfterScore +
                    features.punctuationScore +
                    features.negativeContextScore +  // Negative, so subtracts
                    features.sequenceScore +
                    features.contentLengthScore
            
            candidate.copy(
                score = totalScore,
                features = features
            )
        }
        
        AppLogger.d(TAG_TRANSCRIPT, "[NumberBasedSegmentation] Scored %d candidates", scored.size)
        scored.forEach { candidate ->
            AppLogger.d(TAG_TRANSCRIPT, 
                "[NumberBasedSegmentation] Candidate: %s (number=%d, score=%d, pos=%d, pause=%d, punct=%d, neg=%d, seq=%d, len=%d)",
                candidate.matchText, candidate.number, candidate.score,
                candidate.features.positionScore, candidate.features.pauseAfterScore,
                candidate.features.punctuationScore, candidate.features.negativeContextScore,
                candidate.features.sequenceScore, candidate.features.contentLengthScore)
        }
        
        return scored
    }
    
    /**
     * Find valid sequences (1, 2, 3, ...) in candidates
     */
    private fun findValidSequences(
        candidates: List<HeadingCandidate>
    ): List<List<HeadingCandidate>> {
        val sequences = mutableListOf<List<HeadingCandidate>>()
        val sorted = candidates.sortedBy { it.startMs }
        
        var currentSequence = mutableListOf<HeadingCandidate>()
        
        for (i in sorted.indices) {
            val candidate = sorted[i]
            
            if (currentSequence.isEmpty()) {
                currentSequence.add(candidate)
            } else {
                val last = currentSequence.last()
                if (candidate.number == last.number + 1) {
                    currentSequence.add(candidate)
                } else {
                    if (currentSequence.size >= 2) {
                        sequences.add(currentSequence.toList())
                    }
                    currentSequence = mutableListOf(candidate)
                }
            }
        }
        
        if (currentSequence.size >= 2) {
            sequences.add(currentSequence)
        }
        
        return sequences
    }
    
    /**
     * Filter valid headings based on score and configuration
     */
    fun filterValidHeadings(
        candidates: List<HeadingCandidate>,
        config: SegmentationConfig = SegmentationConfig()
    ): List<HeadingCandidate> {
        val filtered = candidates.filter { candidate ->
            // Auto-accept "Speaker X" patterns (high confidence)
            if (config.speakerPatternAutoAccept && candidate.patternType == PatternType.SPEAKER) {
                return@filter true
            }
            
            // Check minimum score
            if (candidate.score < config.minScore) {
                return@filter false
            }
            
            // Check negative context (reject if strong negative context)
            if (candidate.features.negativeContextScore < -2) {
                return@filter false
            }
            
            true
        }
        
        // If requireSequence, only keep candidates that are part of a valid sequence
        if (config.requireSequence) {
            val sequences = findValidSequences(filtered)
            return filtered.filter { candidate ->
                sequences.any { sequence -> sequence.contains(candidate) }
            }
        }
        
        AppLogger.d(TAG_TRANSCRIPT, "[NumberBasedSegmentation] Filtered to %d valid headings (from %d candidates)",
            filtered.size, candidates.size)
        
        return filtered.sortedBy { it.startMs }
    }
    
    /**
     * Segment transcript into sections based on headings
     */
    fun segmentTranscript(
        headings: List<HeadingCandidate>,
        words: List<Word>,
        segments: List<TranscriptSegment>
    ): List<Section> {
        if (headings.isEmpty()) {
            // No headings found, return single section
            AppLogger.d(TAG_TRANSCRIPT, "[NumberBasedSegmentation] No headings found, returning single section")
            return listOf(
                Section(
                    index = 1,
                    number = 1,
                    startMs = segments.firstOrNull()?.startTimeMs ?: 0L,
                    endMs = segments.lastOrNull()?.endTimeMs ?: 0L,
                    wordStartIndex = 0,
                    wordEndIndex = words.size - 1,
                    patternType = PatternType.NUMBER,
                    headingText = ""
                )
            )
        }
        
        val sections = mutableListOf<Section>()
        
        for (i in headings.indices) {
            val heading = headings[i]
            val nextHeading = headings.getOrNull(i + 1)
            
            // Content starts after heading ends
            val wordStartIndex = heading.indexEnd + 1
            val wordEndIndex = nextHeading?.let { it.indexStart - 1 } ?: (words.size - 1)
            
            val startMs = if (wordStartIndex < words.size) {
                words[wordStartIndex].startMs
            } else {
                heading.endMs
            }
            
            val endMs = if (wordEndIndex >= 0 && wordEndIndex < words.size) {
                words[wordEndIndex].endMs
            } else {
                nextHeading?.startMs ?: (words.lastOrNull()?.endMs ?: heading.endMs)
            }
            
            sections.add(
                Section(
                    index = i + 1,
                    number = heading.number,
                    startMs = startMs,
                    endMs = endMs,
                    wordStartIndex = wordStartIndex.coerceAtLeast(0),
                    wordEndIndex = wordEndIndex.coerceAtMost(words.size - 1),
                    patternType = heading.patternType,
                    headingText = heading.matchText
                )
            )
        }
        
        AppLogger.d(TAG_TRANSCRIPT, "[NumberBasedSegmentation] Created %d sections from %d headings",
            sections.size, headings.size)
        
        return sections
    }
    
    /**
     * Assign speakers to segments based on sections
     */
    fun assignSpeakersFromSections(
        sections: List<Section>,
        segments: List<TranscriptSegment>
    ): List<TranscriptSegment> {
        return segments.map { segment ->
            // Find which section this segment belongs to
            val section = sections.find { section ->
                segment.startTimeMs >= section.startMs &&
                segment.endTimeMs <= section.endMs
            }
            
            segment.copy(
                speaker = section?.number ?: 1  // Default to speaker 1 if no section found
            )
        }
    }
    
    /**
     * Main entry point: Detect sections from segments
     */
    fun detectSections(
        segments: List<TranscriptSegment>,
        config: SegmentationConfig = SegmentationConfig()
    ): List<Section> {
        if (segments.isEmpty()) {
            AppLogger.w(TAG_TRANSCRIPT, "[NumberBasedSegmentation] No segments provided")
            return emptyList()
        }
        
        AppLogger.d(TAG_TRANSCRIPT, "[NumberBasedSegmentation] Starting detection on %d segments", segments.size)
        
        // Step 1: Extract words
        val words = extractWordsFromSegments(segments)
        if (words.isEmpty()) {
            AppLogger.w(TAG_TRANSCRIPT, "[NumberBasedSegmentation] No words extracted")
            return emptyList()
        }
        
        // Step 2: Find candidates
        val candidates = findCandidateHeadings(words)
        if (candidates.isEmpty()) {
            AppLogger.d(TAG_TRANSCRIPT, "[NumberBasedSegmentation] No candidates found")
            return emptyList()
        }
        
        // Step 3: Score candidates
        val scoredCandidates = scoreCandidates(candidates, words, segments, config)
        
        // Step 4: Filter valid headings
        val validHeadings = filterValidHeadings(scoredCandidates, config)
        if (validHeadings.isEmpty()) {
            AppLogger.d(TAG_TRANSCRIPT, "[NumberBasedSegmentation] No valid headings after filtering")
            return emptyList()
        }
        
        // Step 5: Segment transcript
        val sections = segmentTranscript(validHeadings, words, segments)
        
        AppLogger.d(TAG_TRANSCRIPT, "[NumberBasedSegmentation] Detection complete: %d sections found",
            sections.size)
        
        return sections
    }
}

