# Number-Based Speaker Segmentation - Implementation Plan

## Executive Summary

This document outlines a comprehensive plan for implementing intelligent speaker segmentation based on "Number one/two/three..." patterns in transcripts. The algorithm uses a scoring-based approach to distinguish between actual section headers and false positives (e.g., "my phone number is one").

**Status**: Planning Phase  
**Priority**: High  
**Estimated Complexity**: Medium-High  
**Dependencies**: Word-level timestamps (may need Whisper enhancement)

---

## 1. Algorithm Overview

### 1.1 Core Concept

The algorithm identifies section headers by:
1. Finding candidate phrases like "number one", "number two", etc.
2. Scoring each candidate based on multiple features
3. Filtering candidates by confidence threshold
4. Using validated headers to segment the transcript

### 1.2 Key Advantages

- **Context-aware**: Distinguishes headers from content using multiple signals
- **Extensible**: Can support multiple patterns (number, speaker, part, section)
- **Robust**: Handles edge cases with negative context detection
- **Confidence-based**: Only uses high-confidence detections

### 1.3 Limitations & Challenges

- **Word-level timestamps required**: Current Whisper segments only have segment-level timestamps
- **Ambiguity**: Cannot be 100% accurate (e.g., "I'm number one" vs "Number one. Let me explain...")
- **Language-dependent**: Patterns may vary across languages
- **Threshold tuning**: Requires testing to find optimal score thresholds

---

## 2. Detailed Algorithm Design

### 2.1 Data Structures

```kotlin
/**
 * Represents a word with timestamp (if available from Whisper)
 */
data class Word(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val charOffset: Int = 0  // Character offset in original segment
)

/**
 * Candidate heading detected in transcript
 */
data class HeadingCandidate(
    val indexStart: Int,           // Word index where pattern starts
    val indexEnd: Int,             // Word index where pattern ends
    val number: Int,               // 1, 2, 3, ...
    val startMs: Long,             // Timestamp of candidate
    val endMs: Long,
    val patternType: PatternType,  // NUMBER, SPEAKER, PART, SECTION
    val matchText: String,         // Original matched text
    var score: Int = 0,            // Confidence score
    var features: FeatureScores = FeatureScores()  // Detailed scoring breakdown
)

enum class PatternType {
    NUMBER,    // "number one", "number 1"
    SPEAKER,   // "speaker one", "speaker 1"
    PART,      // "part one", "part 1"
    SECTION,   // "section one", "section 1"
    FIRST,     // "first point", "first part"
    SECOND,    // "second point", "second part"
    // ... extensible
}

/**
 * Detailed feature scores for debugging
 */
data class FeatureScores(
    val positionScore: Int = 0,        // At start of sentence/segment
    val pauseAfterScore: Int = 0,       // Long pause after candidate
    val punctuationScore: Int = 0,      // Heading-style punctuation
    val negativeContextScore: Int = 0,   // Penalty for negative context
    val sequenceScore: Int = 0,          // Part of valid sequence
    val contentLengthScore: Int = 0      // Long content after candidate
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
```

### 2.2 Step 1: Extract Words from Segments

**Challenge**: Whisper currently provides segment-level timestamps, not word-level.

**Solution Options**:
1. **Option A (Recommended)**: Use segment-level approximation
   - Split segment text by whitespace
   - Distribute segment duration evenly across words
   - Less accurate but works with current infrastructure

2. **Option B (Future Enhancement)**: Request word-level timestamps from Whisper
   - Modify Whisper JNI to return word-level timestamps
   - More accurate but requires native code changes

**Implementation (Option A)**:
```kotlin
fun extractWordsFromSegments(segments: List<TranscriptSegment>): List<Word> {
    val words = mutableListOf<Word>()
    var charOffset = 0
    
    segments.forEach { segment ->
        val segmentText = segment.text
        val segmentDuration = segment.endTimeMs - segment.startTimeMs
        val wordTokens = segmentText.split(Regex("\\s+"))
        
        if (wordTokens.isEmpty()) return@forEach
        
        val avgWordDuration = segmentDuration / wordTokens.size
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
    
    return words
}
```

### 2.3 Step 2: Find Candidate Patterns

```kotlin
/**
 * Pattern definitions
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
    PatternType.FIRST to Regex(
        """\b(first|1st)\s+(point|part|section|item)\b""",
        RegexOption.IGNORE_CASE
    ),
    PatternType.SECOND to Regex(
        """\b(second|2nd)\s+(point|part|section|item)\b""",
        RegexOption.IGNORE_CASE
    )
)

/**
 * Normalize number word to integer
 */
private fun normalizeNumberWord(token: String): Int? {
    return when (token.lowercase().trim('.', ',', ':', '-', '?', '!')) {
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
        else -> token.trim('.', ',', ':', '-', '?', '!').toIntOrNull()
    }
}

/**
 * Find all candidate headings in words
 */
fun findCandidateHeadings(words: List<Word>): List<HeadingCandidate> {
    val candidates = mutableListOf<HeadingCandidate>()
    
    // Build full text for regex matching
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
    
    return candidates.sortedBy { it.startMs }
}
```

### 2.4 Step 3: Score Candidates

#### Feature 1: Position Score (At Start of Sentence/Segment)

```kotlin
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
    
    if (candidateSegment != prevSegment) {
        return 1  // New segment = potential new section
    }
    
    return 0
}
```

#### Feature 2: Pause After Score

```kotlin
fun calculatePauseAfterScore(
    candidate: HeadingCandidate,
    words: List<Word>
): Int {
    val nextWord = words.getOrNull(candidate.indexEnd + 1) ?: return 0
    
    val pauseAfter = nextWord.startMs - candidate.endMs
    
    return when {
        pauseAfter >= 800 -> 2  // Long pause (high confidence)
        pauseAfter >= 500 -> 1  // Medium pause
        else -> 0
    }
}
```

#### Feature 3: Punctuation Score (Heading Style)

```kotlin
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
```

#### Feature 4: Negative Context Score (Penalty)

```kotlin
private val contextBlacklist = listOf(
    "phone", "phonenumber", "address", "time", "code", "password",
    "room", "chapter", "lesson", "level",
    "my", "your", "his", "her", "their", "our",
    "i'm", "im", "you're", "youre", "we're", "were",
    "is", "are", "was", "were", "has", "have"
)

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
```

#### Feature 5: Sequence Score

```kotlin
fun calculateSequenceScore(
    candidates: List<HeadingCandidate>
): Map<HeadingCandidate, Int> {
    val scores = mutableMapOf<HeadingCandidate, Int>()
    
    // Sort by time
    val sorted = candidates.sortedBy { it.startMs }
    
    // Check for valid sequences (1, 2, 3, ...)
    for (i in 1 until sorted.size) {
        val prev = sorted[i - 1]
        val cur = sorted[i]
        
        if (cur.number == prev.number + 1) {
            val gap = cur.startMs - prev.endMs
            
            // Valid sequence: numbers increase, reasonable time gap
            if (gap in 5_000L..3_600_000L) {  // 5s to 1 hour
                scores[prev] = (scores[prev] ?: 0) + 1
                scores[cur] = (scores[cur] ?: 0) + 1
            }
        }
    }
    
    return scores
}
```

#### Feature 6: Content Length Score

```kotlin
fun calculateContentLengthScore(
    candidate: HeadingCandidate,
    words: List<Word>,
    nextCandidate: HeadingCandidate?
): Int {
    val contentEnd = nextCandidate?.startMs ?: words.lastOrNull()?.endMs ?: candidate.endMs
    val contentDuration = contentEnd - candidate.endMs
    
    // Long content after heading = more likely to be a real section
    return if (contentDuration >= 15_000L) {  // >= 15 seconds
        1
    } else {
        0
    }
}
```

#### Complete Scoring Function

```kotlin
fun scoreCandidates(
    candidates: List<HeadingCandidate>,
    words: List<Word>,
    segments: List<TranscriptSegment>
): List<HeadingCandidate> {
    val scored = candidates.map { candidate ->
        val features = FeatureScores(
            positionScore = calculatePositionScore(candidate, words, segments),
            pauseAfterScore = calculatePauseAfterScore(candidate, words),
            punctuationScore = calculatePunctuationScore(candidate, words),
            negativeContextScore = calculateNegativeContextScore(candidate, words),
            contentLengthScore = calculateContentLengthScore(
                candidate, 
                words, 
                candidates.find { it.startMs > candidate.startMs }
            )
        )
        
        // Sequence score is calculated across all candidates
        val sequenceScore = calculateSequenceScore(candidates)[candidate] ?: 0
        features.copy(sequenceScore = sequenceScore)
        
        // Calculate total score
        val totalScore = features.positionScore +
                features.pauseAfterScore +
                features.punctuationScore +
                features.negativeContextScore +  // Negative, so subtracts
                features.sequenceScore +
                features.contentLengthScore
        
        candidate.copy(
            score = totalScore,
            features = features.copy(sequenceScore = sequenceScore)
        )
    }
    
    return scored
}
```

### 2.5 Step 4: Filter Valid Headings

```kotlin
/**
 * Configuration for scoring thresholds
 */
data class SegmentationConfig(
    val minScore: Int = 2,              // Minimum score to be considered heading
    val speakerPatternAutoAccept: Boolean = true,  // "Speaker X" always accepted
    val requireSequence: Boolean = false,  // Require at least 2 candidates in sequence
    val minSequenceLength: Int = 2        // Minimum sequence length if requireSequence=true
)

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
    
    return filtered.sortedBy { it.startMs }
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
```

### 2.6 Step 5: Segment Transcript

```kotlin
fun segmentTranscript(
    headings: List<HeadingCandidate>,
    words: List<Word>,
    segments: List<TranscriptSegment>
): List<Section> {
    if (headings.isEmpty()) {
        // No headings found, return single section
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
    
    return sections
}
```

### 2.7 Step 6: Map Sections to Speaker Assignments

```kotlin
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
```

---

## 3. Integration with Existing Code

### 3.1 File Structure

```
app/src/main/java/com/yourname/smartrecorder/domain/usecase/
├── SpeakerSegmentationHelper.kt          (existing - extend)
├── NumberBasedSegmentationHelper.kt      (new)
└── ProcessTranscriptUseCase.kt           (existing - modify)
```

### 3.2 Integration Points

1. **ProcessTranscriptUseCase.kt**:
   - Add new detection method: `detectSpeakersWithNumberPatterns()`
   - Call after marker-based detection, before heuristic-based detection
   - Priority: Marker-based > Number-based > Heuristic-based

2. **SpeakerSegmentationHelper.kt**:
   - Keep existing "Speaker X" detection
   - Add unified interface for all pattern types

### 3.3 Modified Flow

```kotlin
// In ProcessTranscriptUseCase.detectSpeakers()

private fun detectSpeakers(segments: List<TranscriptSegment>): List<TranscriptSegment> {
    // Step 1: Try explicit "Speaker X" markers (highest priority)
    val speakerMarkers = SpeakerSegmentationHelper.detectSpeakerMarkers(segments)
    if (speakerMarkers.isNotEmpty()) {
        return useMarkerBasedDetection(segments, speakerMarkers)
    }
    
    // Step 2: Try number-based pattern detection (new)
    val numberSections = NumberBasedSegmentationHelper.detectSections(segments)
    if (numberSections.isNotEmpty() && numberSections.size >= 2) {
        return useNumberBasedDetection(segments, numberSections)
    }
    
    // Step 3: Fallback to heuristic-based (question marks, time gaps)
    return detectSpeakersWithHeuristics(segments)
}
```

---

## 4. Implementation Phases

### Phase 1: Core Infrastructure (Week 1)
- [ ] Create `NumberBasedSegmentationHelper.kt`
- [ ] Implement word extraction from segments
- [ ] Implement candidate pattern detection
- [ ] Basic scoring (position, pause, punctuation)
- [ ] Unit tests for core functions

### Phase 2: Advanced Scoring (Week 2)
- [ ] Implement negative context detection
- [ ] Implement sequence validation
- [ ] Implement content length scoring
- [ ] Add configuration for thresholds
- [ ] Unit tests for scoring logic

### Phase 3: Integration (Week 3)
- [ ] Integrate with `ProcessTranscriptUseCase`
- [ ] Add logging for debugging
- [ ] Test with real transcripts
- [ ] Tune thresholds based on test results

### Phase 4: Enhancement (Week 4)
- [ ] Support additional patterns (first/second/third point)
- [ ] Add confidence metrics
- [ ] Performance optimization
- [ ] Documentation

---

## 5. Testing Strategy

### 5.1 Unit Tests

```kotlin
class NumberBasedSegmentationHelperTest {
    @Test
    fun testCandidateDetection() {
        // Test: "Number one" detected correctly
    }
    
    @Test
    fun testNegativeContext() {
        // Test: "my phone number is one" rejected
    }
    
    @Test
    fun testSequenceValidation() {
        // Test: 1, 2, 3 sequence validated
    }
    
    @Test
    fun testScoring() {
        // Test: Scoring logic produces expected results
    }
}
```

### 5.2 Integration Tests

- Test with real transcripts containing "Number X" patterns
- Test with false positives ("my phone number is one")
- Test with mixed patterns (some valid, some invalid)
- Test edge cases (single candidate, no candidates, overlapping patterns)

### 5.3 Performance Tests

- Measure processing time for long transcripts
- Optimize if processing time > 1 second for typical transcript

---

## 6. Configuration & Tuning

### 6.1 Configurable Parameters

```kotlin
data class SegmentationConfig(
    // Scoring thresholds
    val minScore: Int = 2,
    val positionScoreWeight: Int = 2,
    val pauseAfterScoreWeight: Int = 2,
    val punctuationScoreWeight: Int = 2,
    val negativeContextPenalty: Int = -3,
    val sequenceScoreWeight: Int = 1,
    val contentLengthScoreWeight: Int = 1,
    
    // Pause thresholds (ms)
    val longPauseThreshold: Long = 800,
    val mediumPauseThreshold: Long = 500,
    
    // Content length threshold (ms)
    val minContentLength: Long = 15_000,
    
    // Sequence validation
    val minSequenceGap: Long = 5_000,      // 5 seconds
    val maxSequenceGap: Long = 3_600_000,  // 1 hour
    
    // Pattern preferences
    val speakerPatternAutoAccept: Boolean = true,
    val requireSequence: Boolean = false,
    val minSequenceLength: Int = 2
)
```

### 6.2 Tuning Process

1. Collect test transcripts with known "Number X" patterns
2. Run algorithm with default config
3. Analyze false positives and false negatives
4. Adjust thresholds based on results
5. Repeat until acceptable accuracy (target: 80-90%)

---

## 7. Extended Pattern Support & Advanced Features

### 7.1 Extended Pattern Types

#### 7.1.1 Complete Pattern Enum

```kotlin
enum class PatternType {
    // Number-based patterns
    NUMBER,        // "number one", "number 1", "no. one", "num one"
    NUMBER_DOT,    // "number 1.", "no. 1."
    
    // Speaker-based patterns (highest confidence)
    SPEAKER,       // "speaker one", "speaker 1"
    SPEAKER_DOT,   // "speaker 1."
    
    // Part/Section patterns
    PART,          // "part one", "part 1"
    SECTION,       // "section one", "section 1"
    CHAPTER,        // "chapter one", "chapter 1"
    
    // Point/Item patterns
    POINT,          // "point one", "point 1"
    ITEM,           // "item one", "item 1"
    TOPIC,          // "topic one", "topic 1"
    
    // Ordinal patterns
    FIRST,          // "first point", "first part", "1st point"
    SECOND,         // "second point", "second part", "2nd point"
    THIRD,          // "third point", "third part", "3rd point"
    FOURTH,         // "fourth point", "4th point"
    FIFTH,          // "fifth point", "5th point"
    
    // Transitional patterns (positive context)
    NOW_NUMBER,     // "now number one", "now, number one"
    LETS_MOVE,      // "let's move to number two", "let's go to part three"
    NEXT_POINT,     // "next point is", "next, number two"
    
    // Custom user-defined patterns (future)
    CUSTOM
}
```

#### 7.1.2 Extended Pattern Regexes

```kotlin
private val extendedPatternRegexes = mapOf(
    // Number patterns (expanded)
    PatternType.NUMBER to listOf(
        Regex("""\b(number|no|num|#)\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bno\.\s*(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b#\s*(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE)
    ),
    
    // Speaker patterns
    PatternType.SPEAKER to listOf(
        Regex("""\bspeaker\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bspeaker\s*#?\s*(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE)
    ),
    
    // Part/Section patterns
    PatternType.PART to listOf(
        Regex("""\bpart\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpart\s*#?\s*(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE)
    ),
    
    PatternType.SECTION to listOf(
        Regex("""\bsection\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE)
    ),
    
    PatternType.CHAPTER to listOf(
        Regex("""\bchapter\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE)
    ),
    
    // Point/Item/Topic patterns
    PatternType.POINT to listOf(
        Regex("""\bpoint\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpoint\s*#?\s*(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE)
    ),
    
    PatternType.ITEM to listOf(
        Regex("""\bitem\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE)
    ),
    
    PatternType.TOPIC to listOf(
        Regex("""\btopic\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE)
    ),
    
    // Ordinal patterns
    PatternType.FIRST to listOf(
        Regex("""\b(first|1st)\s+(point|part|section|item|topic|chapter)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bthe\s+first\s+(point|part|section|item|topic|chapter)\b""", RegexOption.IGNORE_CASE)
    ),
    
    PatternType.SECOND to listOf(
        Regex("""\b(second|2nd)\s+(point|part|section|item|topic|chapter)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bthe\s+second\s+(point|part|section|item|topic|chapter)\b""", RegexOption.IGNORE_CASE)
    ),
    
    PatternType.THIRD to listOf(
        Regex("""\b(third|3rd)\s+(point|part|section|item|topic|chapter)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bthe\s+third\s+(point|part|section|item|topic|chapter)\b""", RegexOption.IGNORE_CASE)
    ),
    
    // Transitional patterns (positive context)
    PatternType.NOW_NUMBER to listOf(
        Regex("""\bnow[,]?\s+number\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnow[,]?\s+let['']?s?\s+move\s+to\s+number\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE)
    ),
    
    PatternType.LETS_MOVE to listOf(
        Regex("""\blet['']?s?\s+move\s+to\s+(number|part|section|point)\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\blet['']?s?\s+go\s+to\s+(number|part|section|point)\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE)
    ),
    
    PatternType.NEXT_POINT to listOf(
        Regex("""\bnext[,]?\s+(point|part|section|number)\s+is\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnext[,]?\s+number\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""", RegexOption.IGNORE_CASE)
    )
)
```

### 7.2 Positive Context Patterns (Score Boost)

#### 7.2.1 Positive Context Detection

```kotlin
/**
 * Patterns that indicate a heading is likely (boost confidence)
 */
private val positiveContextPatterns = mapOf(
    // Transitional phrases
    "now" to listOf(
        Regex("""\bnow[,]?\s+(number|part|section|point|let['']?s)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnow[,]?\s+let['']?s?\s+move\b""", RegexOption.IGNORE_CASE)
    ),
    
    // Introduction phrases
    "first" to listOf(
        Regex("""\bthe\s+first\s+(point|part|section|item|topic)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bfirst[,]?\s+let\s+me\b""", RegexOption.IGNORE_CASE)
    ),
    
    // Transition phrases
    "next" to listOf(
        Regex("""\bnext[,]?\s+(point|part|section|number)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmoving\s+to\s+(number|part|section)\b""", RegexOption.IGNORE_CASE)
    ),
    
    // Enumeration phrases
    "let's" to listOf(
        Regex("""\blet['']?s?\s+move\s+to\b""", RegexOption.IGNORE_CASE),
        Regex("""\blet['']?s?\s+go\s+to\b""", RegexOption.IGNORE_CASE),
        Regex("""\blet['']?s?\s+talk\s+about\b""", RegexOption.IGNORE_CASE)
    )
)

/**
 * Calculate positive context score (boost)
 */
fun calculatePositiveContextScore(
    candidate: HeadingCandidate,
    words: List<Word>
): Int {
    val contextStart = maxOf(0, candidate.indexStart - 5)  // Check 5 words before
    val contextEnd = minOf(words.size - 1, candidate.indexEnd + 2)  // Check 2 words after
    
    val contextText = words.subList(contextStart, contextEnd + 1)
        .joinToString(" ") { it.text.lowercase() }
    
    var boostScore = 0
    
    positiveContextPatterns.forEach { (key, patterns) ->
        patterns.forEach { pattern ->
            if (pattern.containsMatchIn(contextText)) {
                boostScore += 1
                return@forEach
            }
        }
    }
    
    return boostScore.coerceAtMost(2)  // Max +2 boost
}
```

### 7.3 Advanced Scoring Features

#### 7.3.1 Capitalization Analysis

```kotlin
/**
 * Check if candidate starts with capital letter (heading indicator)
 */
fun calculateCapitalizationScore(
    candidate: HeadingCandidate,
    words: List<Word>
): Int {
    val firstWord = words.getOrNull(candidate.indexStart) ?: return 0
    val firstChar = firstWord.text.firstOrNull() ?: return 0
    
    return if (firstChar.isUpperCase() && firstWord.text.length > 1) {
        1  // Capitalized = more likely heading
    } else {
        0
    }
}
```

#### 7.3.2 Segment Length Analysis

```kotlin
/**
 * Check if candidate is in a short segment (heading indicator)
 */
fun calculateSegmentLengthScore(
    candidate: HeadingCandidate,
    segments: List<TranscriptSegment>
): Int {
    val segment = findSegmentForWord(candidate.indexStart, words, segments) ?: return 0
    val segmentDuration = segment.endTimeMs - segment.startTimeMs
    val segmentWordCount = segment.text.split(Regex("\\s+")).size
    
    // Short segments (< 3 seconds, < 10 words) are more likely to be headings
    return when {
        segmentDuration < 3000 && segmentWordCount < 10 -> 2
        segmentDuration < 5000 && segmentWordCount < 15 -> 1
        else -> 0
    }
}
```

#### 7.3.3 Repetition Pattern Detection

```kotlin
/**
 * Detect if pattern repeats in transcript (list structure indicator)
 */
fun calculateRepetitionScore(
    candidate: HeadingCandidate,
    allCandidates: List<HeadingCandidate>
): Int {
    val samePattern = allCandidates.filter { 
        it.patternType == candidate.patternType && 
        it.number != candidate.number 
    }
    
    // If same pattern type appears multiple times, it's likely a list
    return when {
        samePattern.size >= 2 -> 2  // Strong indicator
        samePattern.size == 1 -> 1  // Weak indicator
        else -> 0
    }
}
```

### 7.4 Multi-Language Support

#### 7.4.1 Language Detection & Mapping

```kotlin
enum class SupportedLanguage {
    ENGLISH,
    VIETNAMESE,
    SPANISH,
    FRENCH,
    GERMAN,
    CHINESE,
    JAPANESE
}

/**
 * Language-specific number word mappings
 */
private val languageNumberMappings = mapOf(
    SupportedLanguage.ENGLISH to mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12,
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5
    ),
    
    SupportedLanguage.VIETNAMESE to mapOf(
        "một" to 1, "hai" to 2, "ba" to 3, "bốn" to 4, "năm" to 5,
        "sáu" to 6, "bảy" to 7, "tám" to 8, "chín" to 9, "mười" to 10,
        "mười một" to 11, "mười hai" to 12,
        "thứ nhất" to 1, "thứ hai" to 2, "thứ ba" to 3, "thứ tư" to 4, "thứ năm" to 5,
        "phần một" to 1, "phần hai" to 2, "phần ba" to 3
    ),
    
    SupportedLanguage.SPANISH to mapOf(
        "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        "once" to 11, "doce" to 12,
        "primero" to 1, "segundo" to 2, "tercero" to 3, "cuarto" to 4, "quinto" to 5,
        "número uno" to 1, "número dos" to 2
    )
)

/**
 * Language-specific pattern regexes
 */
private val languagePatternRegexes = mapOf(
    SupportedLanguage.ENGLISH to extendedPatternRegexes,  // Use English patterns
    
    SupportedLanguage.VIETNAMESE to mapOf(
        PatternType.NUMBER to listOf(
            Regex("""\b(số|phần)\s+(một|hai|ba|bốn|năm|sáu|bảy|tám|chín|mười|mười một|mười hai|[0-9]+)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bphần\s+(một|hai|ba|bốn|năm|sáu|bảy|tám|chín|mười|mười một|mười hai|[0-9]+)\b""", RegexOption.IGNORE_CASE)
        ),
        PatternType.FIRST to listOf(
            Regex("""\b(thứ nhất|điểm thứ nhất|phần thứ nhất)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bđiểm\s+(một|đầu tiên)\b""", RegexOption.IGNORE_CASE)
        )
    ),
    
    SupportedLanguage.SPANISH to mapOf(
        PatternType.NUMBER to listOf(
            Regex("""\b(número|num|#)\s+(uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|[0-9]+)\b""", RegexOption.IGNORE_CASE)
        ),
        PatternType.FIRST to listOf(
            Regex("""\b(primero|primera)\s+(punto|parte|sección)\b""", RegexOption.IGNORE_CASE)
        )
    )
)

/**
 * Language-specific negative context lists
 */
private val languageNegativeContext = mapOf(
    SupportedLanguage.ENGLISH to contextBlacklist,  // Use existing English list
    
    SupportedLanguage.VIETNAMESE to listOf(
        "số điện thoại", "địa chỉ", "mã số", "mật khẩu",
        "của tôi", "của bạn", "của anh", "của chị",
        "tôi là", "bạn là", "anh là", "chị là"
    ),
    
    SupportedLanguage.SPANISH to listOf(
        "número de teléfono", "dirección", "código", "contraseña",
        "mi", "tu", "su", "nuestro",
        "soy", "eres", "es", "somos"
    )
)
```

#### 7.4.2 Auto Language Detection

```kotlin
/**
 * Detect language from transcript content
 */
fun detectLanguage(segments: List<TranscriptSegment>): SupportedLanguage {
    val sampleText = segments.take(10)
        .joinToString(" ") { it.text }
        .lowercase()
    
    // Simple keyword-based detection (can be enhanced with ML)
    val languageScores = mutableMapOf<SupportedLanguage, Int>()
    
    // Vietnamese indicators
    val vietnameseChars = sampleText.count { it in "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ" }
    if (vietnameseChars > 5) {
        languageScores[SupportedLanguage.VIETNAMESE] = vietnameseChars
    }
    
    // Spanish indicators
    val spanishWords = listOf("el", "la", "los", "las", "es", "son", "un", "una", "de", "en")
    val spanishCount = spanishWords.count { sampleText.contains(it) }
    if (spanishCount > 3) {
        languageScores[SupportedLanguage.SPANISH] = spanishCount
    }
    
    // Default to English if no strong indicators
    return languageScores.maxByOrNull { it.value }?.key ?: SupportedLanguage.ENGLISH
}
```

### 7.5 Confidence Levels System

#### 7.5.1 Confidence Level Enum

```kotlin
enum class ConfidenceLevel {
    VERY_HIGH,  // score >= 6, or "Speaker X" pattern, or part of strong sequence
    HIGH,       // score 4-5, or part of valid sequence
    MEDIUM,     // score 2-3, single candidate with good features
    LOW,        // score 1-2, weak indicators
    VERY_LOW    // score < 1, or strong negative context
}

/**
 * Calculate confidence level from score and context
 */
fun calculateConfidenceLevel(
    candidate: HeadingCandidate,
    allCandidates: List<HeadingCandidate>
): ConfidenceLevel {
    // Auto high confidence for "Speaker X"
    if (candidate.patternType == PatternType.SPEAKER) {
        return ConfidenceLevel.VERY_HIGH
    }
    
    // Check if part of strong sequence
    val isInSequence = isPartOfValidSequence(candidate, allCandidates)
    if (isInSequence && candidate.score >= 3) {
        return ConfidenceLevel.VERY_HIGH
    }
    
    // Score-based confidence
    return when {
        candidate.score >= 6 -> ConfidenceLevel.VERY_HIGH
        candidate.score >= 4 -> ConfidenceLevel.HIGH
        candidate.score >= 2 -> ConfidenceLevel.MEDIUM
        candidate.score >= 1 -> ConfidenceLevel.LOW
        else -> ConfidenceLevel.VERY_LOW
    }
}

/**
 * Filter candidates by minimum confidence level
 */
fun filterByConfidence(
    candidates: List<HeadingCandidate>,
    minConfidence: ConfidenceLevel = ConfidenceLevel.MEDIUM
): List<HeadingCandidate> {
    return candidates.filter { candidate ->
        val confidence = calculateConfidenceLevel(candidate, candidates)
        confidence.ordinal >= minConfidence.ordinal
    }
}
```

#### 7.5.2 Confidence-Based UI Display

```kotlin
/**
 * UI representation with confidence indicator
 */
data class SectionWithConfidence(
    val section: Section,
    val confidence: ConfidenceLevel,
    val confidenceScore: Int,
    val features: FeatureScores
)

/**
 * Map sections to UI-friendly format with confidence
 */
fun mapSectionsWithConfidence(
    sections: List<Section>,
    candidates: List<HeadingCandidate>
): List<SectionWithConfidence> {
    return sections.map { section ->
        val candidate = candidates.find { 
            it.number == section.number && 
            it.patternType == section.patternType 
        }
        
        SectionWithConfidence(
            section = section,
            confidence = candidate?.let { calculateConfidenceLevel(it, candidates) } 
                ?: ConfidenceLevel.LOW,
            confidenceScore = candidate?.score ?: 0,
            features = candidate?.features ?: FeatureScores()
        )
    }
}
```

### 7.6 User Feedback Loop

#### 7.6.1 Feedback Data Model

```kotlin
/**
 * User feedback on segmentation accuracy
 */
data class SegmentationFeedback(
    val recordingId: String,
    val sectionIndex: Int,
    val isCorrect: Boolean,  // User confirms section is correct
    val isIncorrect: Boolean, // User marks section as incorrect
    val correctNumber: Int? = null,  // If incorrect, what should it be?
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Aggregate feedback for learning
 */
data class FeedbackAggregate(
    val patternType: PatternType,
    val averageScore: Double,
    val correctCount: Int,
    val incorrectCount: Int,
    val commonIssues: List<String>
)
```

#### 7.6.2 Adaptive Threshold Adjustment

```kotlin
/**
 * Adjust thresholds based on user feedback
 */
class AdaptiveThresholdManager {
    private val feedbackHistory = mutableListOf<SegmentationFeedback>()
    
    fun adjustThresholds(
        feedback: List<SegmentationFeedback>,
        currentConfig: SegmentationConfig
    ): SegmentationConfig {
        // Analyze feedback patterns
        val falsePositives = feedback.count { it.isIncorrect }
        val truePositives = feedback.count { it.isCorrect }
        val total = feedback.size
        
        if (total == 0) return currentConfig
        
        val accuracy = truePositives.toDouble() / total
        
        // If accuracy < 80%, increase threshold
        return if (accuracy < 0.8 && falsePositives > truePositives) {
            currentConfig.copy(
                minScore = currentConfig.minScore + 1,
                requireSequence = true  // Require sequence for higher confidence
            )
        } else if (accuracy > 0.95 && falsePositives < truePositives / 2) {
            // If accuracy > 95%, can lower threshold slightly
            currentConfig.copy(
                minScore = maxOf(1, currentConfig.minScore - 1)
            )
        } else {
            currentConfig
        }
    }
    
    fun learnFromFeedback(
        candidate: HeadingCandidate,
        userFeedback: SegmentationFeedback
    ) {
        // Store feedback for pattern learning
        feedbackHistory.add(userFeedback)
        
        // Update negative context list if pattern consistently fails
        if (userFeedback.isIncorrect) {
            // Could add words around candidate to negative context
        }
    }
}
```

### 7.7 Hybrid Audio Features (Future Enhancement)

#### 7.7.1 Audio-Based Diarization Integration

```kotlin
/**
 * Audio features for speaker diarization
 */
data class AudioFeatures(
    val segmentIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val speakerEmbedding: FloatArray?,  // Voice embedding if available
    val pitch: Float?,
    val energy: Float?,
    val spectralCentroid: Float?
)

/**
 * Combine text-based and audio-based segmentation
 */
fun hybridSegmentation(
    textSections: List<Section>,
    audioFeatures: List<AudioFeatures>
): List<Section> {
    // If audio diarization available, use it to validate/refine text-based sections
    // Merge sections that have same audio speaker but different text numbers
    // Split sections that have different audio speakers but same text number
    
    // This requires audio diarization library integration (future work)
    return textSections
}
```

### 7.8 Performance Optimizations

#### 7.8.1 Caching Strategy

```kotlin
/**
 * Cache word extraction results
 */
class WordExtractionCache {
    private val cache = mutableMapOf<String, List<Word>>()
    
    fun getOrExtract(
        recordingId: String,
        segments: List<TranscriptSegment>,
        extractor: (List<TranscriptSegment>) -> List<Word>
    ): List<Word> {
        val cacheKey = "${recordingId}_${segments.hashCode()}"
        return cache.getOrPut(cacheKey) {
            extractor(segments)
        }
    }
    
    fun clear(recordingId: String) {
        cache.keys.removeAll { it.startsWith(recordingId) }
    }
}

/**
 * Cache pattern matching results
 */
class PatternMatchingCache {
    private val cache = mutableMapOf<String, List<HeadingCandidate>>()
    
    fun getOrMatch(
        cacheKey: String,
        words: List<Word>,
        matcher: (List<Word>) -> List<HeadingCandidate>
    ): List<HeadingCandidate> {
        return cache.getOrPut(cacheKey) {
            matcher(words)
        }
    }
}
```

#### 7.8.2 Parallel Processing

```kotlin
/**
 * Process multiple candidates in parallel
 */
suspend fun scoreCandidatesParallel(
    candidates: List<HeadingCandidate>,
    words: List<Word>,
    segments: List<TranscriptSegment>
): List<HeadingCandidate> = withContext(Dispatchers.Default) {
    candidates.map { candidate ->
        async {
            scoreCandidate(candidate, words, segments)
        }
    }.awaitAll()
}
```

### 7.9 Additional Advanced Features

#### 7.9.1 Custom User Patterns

```kotlin
/**
 * User-defined custom patterns
 */
data class CustomPattern(
    val id: String,
    val name: String,
    val regex: Regex,
    val patternType: PatternType,
    val autoAccept: Boolean = false,
    val priority: Int = 0  // Higher priority = checked first
)

class CustomPatternManager {
    private val customPatterns = mutableListOf<CustomPattern>()
    
    fun addPattern(pattern: CustomPattern) {
        customPatterns.add(pattern)
        customPatterns.sortByDescending { it.priority }
    }
    
    fun findCustomMatches(words: List<Word>): List<HeadingCandidate> {
        val candidates = mutableListOf<HeadingCandidate>()
        
        customPatterns.forEach { customPattern ->
            val fullText = words.joinToString(" ") { it.text }
            customPattern.regex.findAll(fullText.lowercase()).forEach { match ->
                // Create candidate from custom pattern match
                // Similar to standard pattern matching
            }
        }
        
        return candidates
    }
}
```

#### 7.9.2 Pattern Priority System

```kotlin
/**
 * Pattern priority (higher = checked first, more confidence)
 */
private val patternPriority = mapOf(
    PatternType.SPEAKER to 100,      // Highest priority
    PatternType.NOW_NUMBER to 90,
    PatternType.LETS_MOVE to 85,
    PatternType.NEXT_POINT to 80,
    PatternType.FIRST to 75,
    PatternType.SECOND to 75,
    PatternType.THIRD to 75,
    PatternType.NUMBER to 70,
    PatternType.PART to 65,
    PatternType.SECTION to 60,
    PatternType.POINT to 55,
    PatternType.ITEM to 50,
    PatternType.TOPIC to 45
)

/**
 * Sort candidates by priority
 */
fun sortByPriority(candidates: List<HeadingCandidate>): List<HeadingCandidate> {
    return candidates.sortedByDescending { 
        patternPriority[it.patternType] ?: 0 
    }
}
```

---

## 8. Future Enhancements (Updated)

### 8.1 Word-Level Timestamps

If Whisper can provide word-level timestamps:
- More accurate pause detection
- Better position scoring
- Improved overall accuracy
- **Implementation**: Modify Whisper JNI to return word-level timestamps

### 8.2 Machine Learning Approach

For future consideration:
- Train a classifier to distinguish headers from content
- Use features as input to ML model
- Potentially higher accuracy but requires training data
- **Framework**: TensorFlow Lite or ONNX Runtime for mobile

### 8.3 Real-Time Processing

- Process segments as they arrive (streaming)
- Update sections incrementally
- Lower latency for user feedback

### 8.4 Advanced NLP Features

- Named Entity Recognition (NER) to identify false positives
- Dependency parsing to understand sentence structure
- Sentiment analysis to detect conversational vs. formal content

---

## 8. Risk Assessment

### 8.1 Technical Risks

| Risk | Impact | Mitigation |
|------|-------|------------|
| Word-level timestamps not available | Medium | Use segment-level approximation |
| False positives too high | High | Tune thresholds, add more negative context |
| Performance issues | Low | Optimize scoring, cache results |
| Edge cases not covered | Medium | Extensive testing, iterative improvement |

### 8.2 Business Risks

| Risk | Impact | Mitigation |
|------|-------|------------|
| User confusion if wrong segmentation | Medium | Show confidence indicators, allow manual correction |
| Not compatible with all transcript types | Low | Fallback to existing heuristics |

---

## 9. Success Metrics

- **Accuracy**: 80-90% correct segmentation on test transcripts
- **False Positive Rate**: < 10% (incorrectly identified as headers)
- **False Negative Rate**: < 15% (missed actual headers)
- **Performance**: Processing time < 1 second for typical transcript
- **User Satisfaction**: Positive feedback on segmentation quality

---

## 10. Conclusion

This algorithm provides a robust, extensible solution for number-based speaker segmentation. While it cannot achieve 100% accuracy due to inherent ambiguity in natural language, the scoring-based approach with multiple features should achieve 80-90% accuracy, which is acceptable for most use cases.

The implementation is designed to be:
- **Modular**: Easy to extend with new patterns
- **Configurable**: Thresholds can be tuned based on real-world data
- **Maintainable**: Clear separation of concerns, well-documented
- **Testable**: Comprehensive unit and integration tests

**Next Steps**:
1. Review and approve this plan
2. Begin Phase 1 implementation
3. Collect test transcripts for validation
4. Iterate based on feedback

---

## Appendix A: Example Scenarios

### Scenario 1: Valid Headers
```
Transcript: "Number one. This is the first point I want to make. 
Number two. Here's the second important thing. 
Number three. Finally, let me conclude."
```
**Expected**: 3 sections detected (Number 1, 2, 3)

### Scenario 2: False Positive
```
Transcript: "My phone number is one two three four five."
```
**Expected**: No sections detected (negative context: "phone number")

### Scenario 3: Mixed Content
```
Transcript: "I'm number one in my class. Number one. Let me explain the first concept."
```
**Expected**: Only second "Number one" detected (first has negative context "I'm")

### Scenario 4: Sequence Validation
```
Transcript: "Number one. Content here. Number three. More content."
```
**Expected**: May or may not detect (missing Number 2 breaks sequence)

---

---

## 11. Extended Implementation Roadmap

### Phase 5: Extended Patterns (Week 5-6)
- [ ] Implement extended pattern types (POINT, ITEM, TOPIC, CHAPTER)
- [ ] Add ordinal patterns (FIRST, SECOND, THIRD)
- [ ] Implement transitional patterns (NOW_NUMBER, LETS_MOVE, NEXT_POINT)
- [ ] Add positive context detection
- [ ] Unit tests for all pattern types

### Phase 6: Multi-Language Support (Week 7-8)
- [ ] Implement language detection
- [ ] Add Vietnamese pattern support
- [ ] Add Spanish pattern support
- [ ] Language-specific negative context lists
- [ ] Language-specific number mappings
- [ ] Test with multi-language transcripts

### Phase 7: Confidence & UI (Week 9-10)
- [ ] Implement confidence level system
- [ ] Add confidence indicators in UI
- [ ] User feedback collection mechanism
- [ ] Adaptive threshold adjustment
- [ ] Performance optimizations (caching, parallel processing)

### Phase 8: Advanced Features (Week 11-12)
- [ ] Custom user-defined patterns
- [ ] Pattern priority system
- [ ] Capitalization analysis
- [ ] Segment length analysis
- [ ] Repetition pattern detection
- [ ] Integration with audio features (if available)

---

## 12. Complete Feature Matrix

| Feature | Phase | Priority | Complexity | Status |
|---------|-------|----------|------------|--------|
| Basic number pattern detection | 1 | High | Medium | Planned |
| Scoring system (6 features) | 1-2 | High | High | Planned |
| Negative context detection | 2 | High | Medium | Planned |
| Sequence validation | 2 | High | Medium | Planned |
| Extended patterns (point/item/topic) | 5 | Medium | Medium | Planned |
| Ordinal patterns (first/second) | 5 | Medium | Low | Planned |
| Transitional patterns | 5 | Low | Medium | Planned |
| Positive context detection | 5 | Medium | Medium | Planned |
| Multi-language (Vietnamese) | 6 | Medium | High | Planned |
| Multi-language (Spanish) | 6 | Low | High | Planned |
| Confidence levels | 7 | High | Low | Planned |
| User feedback loop | 7 | Medium | High | Planned |
| Adaptive thresholds | 7 | Medium | High | Planned |
| Custom user patterns | 8 | Low | Medium | Planned |
| Pattern priority system | 8 | Low | Low | Planned |
| Audio feature integration | 8 | Low | Very High | Future |

---

**Document Version**: 2.0 (Extended)  
**Last Updated**: 2024  
**Author**: AI Assistant  
**Status**: Comprehensive Plan Ready for Implementation  
**Total Estimated Time**: 12 weeks (3 months)  
**Total Estimated Complexity**: High (with all extensions)

