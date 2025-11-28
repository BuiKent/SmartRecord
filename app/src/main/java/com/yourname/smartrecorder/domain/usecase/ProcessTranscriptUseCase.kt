package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import com.yourname.smartrecorder.domain.repository.TranscriptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for processing transcript segments in background.
 * This applies speaker detection and other enhancements to raw Whisper segments.
 * 
 * Flow:
 * 1. Load raw segments from database
 * 2. Apply speaker detection based on "Speaker one/two/three..." markers
 * 3. Update segments with speaker assignments
 * 4. Save processed segments back to database
 */
class ProcessTranscriptUseCase @Inject constructor(
    private val transcriptRepository: TranscriptRepository
) {
    suspend operator fun invoke(
        recordingId: String
    ): List<TranscriptSegment> = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "ProcessTranscriptUseCase", "Starting", 
            mapOf("recordingId" to recordingId))
        
        try {
            // Load raw segments from database (sync - not Flow)
            val rawSegmentsFromDb = transcriptRepository.getTranscriptSegmentsSync(recordingId)
            
            if (rawSegmentsFromDb.isEmpty()) {
                AppLogger.w(TAG_TRANSCRIPT, "No segments found for processing -> recordingId: %s", recordingId)
                return@withContext emptyList()
            }
            
            // Filter out BLANK_AUDIO segments (in case they exist in DB from before filter was added)
            val rawSegments = rawSegmentsFromDb.filter { segment ->
                val textUpper = segment.text.trim().uppercase()
                textUpper != "BLANK_AUDIO" && textUpper.isNotBlank()
            }
            
            // Log comparison: raw from DB vs filtered
            if (rawSegmentsFromDb.size != rawSegments.size) {
                val filteredCount = rawSegmentsFromDb.size - rawSegments.size
                AppLogger.d(TAG_TRANSCRIPT, "Filtered %d BLANK_AUDIO/blank segments from DB (total: %d -> filtered: %d)", 
                    filteredCount, rawSegmentsFromDb.size, rawSegments.size)
            }
            
            if (rawSegments.isEmpty()) {
                AppLogger.w(TAG_TRANSCRIPT, "No valid segments after filtering -> recordingId: %s", recordingId)
                return@withContext emptyList()
            }
            
            // Check if already processed (all segments have speaker assigned)
            val alreadyProcessed = rawSegments.all { it.speaker != null }
            if (alreadyProcessed) {
                AppLogger.d(TAG_TRANSCRIPT, "Segments already processed -> recordingId: %s", recordingId)
                return@withContext rawSegments
            }
            
            AppLogger.d(TAG_TRANSCRIPT, "Processing %d raw segments -> recordingId: %s", 
                rawSegments.size, recordingId)
            
            // Log raw segments count before processing
            AppLogger.d(TAG_TRANSCRIPT, "=== [RAW_SEGMENTS_BEFORE_PROCESSING] ===")
            AppLogger.d(TAG_TRANSCRIPT, "Total raw segments: %d", rawSegments.size)
            rawSegments.take(5).forEachIndexed { index, segment ->
                AppLogger.d(TAG_TRANSCRIPT, "RAW[%d] time=%.2fs-%.2fs text=\"%s\"", 
                    index, segment.startTimeMs / 1000.0, segment.endTimeMs / 1000.0, 
                    segment.text.take(50))
            }
            if (rawSegments.size > 5) {
                AppLogger.d(TAG_TRANSCRIPT, "... and %d more raw segments", rawSegments.size - 5)
            }
            
            // Apply speaker detection
            val processedSegments = detectSpeakers(rawSegments)
            
            // Log comparison: raw vs processed
            AppLogger.d(TAG_TRANSCRIPT, "=== [SEGMENT_COUNT_COMPARISON] ===")
            AppLogger.d(TAG_TRANSCRIPT, "Raw segments: %d, Processed segments: %d", 
                rawSegments.size, processedSegments.size)
            if (rawSegments.size != processedSegments.size) {
                AppLogger.w(TAG_TRANSCRIPT, "WARNING: Segment count mismatch! Raw: %d, Processed: %d", 
                    rawSegments.size, processedSegments.size)
            }
            
            AppLogger.d(TAG_TRANSCRIPT, "Processed %d segments with speaker detection", processedSegments.size)
            
            // Log processed text (first 5 segments as sample)
            logProcessedText(processedSegments)
            
            // Final comparison log
            AppLogger.d(TAG_TRANSCRIPT, "=== [FINAL_SEGMENT_COMPARISON] ===")
            AppLogger.d(TAG_TRANSCRIPT, "Raw from DB: %d, After filter: %d, After processing: %d", 
                rawSegmentsFromDb.size, rawSegments.size, processedSegments.size)
            
            // Update segments in database with speaker assignments
            AppLogger.d(TAG_TRANSCRIPT, "Updating processed segments in database")
            transcriptRepository.updateTranscriptSegments(recordingId, processedSegments)
            
            val duration = System.currentTimeMillis() - startTime
            AppLogger.logUseCase(TAG_USECASE, "ProcessTranscriptUseCase", "Completed", 
                mapOf("recordingId" to recordingId, "segments" to processedSegments.size, "duration" to "${duration}ms"))
            AppLogger.logPerformance(TAG_TRANSCRIPT, "ProcessTranscriptUseCase", duration, 
                "segments=${processedSegments.size}")
            
            processedSegments
            
        } catch (e: Exception) {
            AppLogger.e(TAG_TRANSCRIPT, "Transcript processing failed", e)
            throw e
        }
    }
    
    /**
     * Detect speakers in transcript segments.
     * Strategy (priority order):
     * 1. Try marker-based detection first (if "Speaker one/two/three..." markers exist) - highest priority
     * 2. Try number-based pattern detection (if "Number one/two/three..." patterns exist) - medium priority
     * 3. Fallback to heuristic-based detection (question marks, time gaps) - lowest priority
     */
    private fun detectSpeakers(segments: List<TranscriptSegment>): List<TranscriptSegment> {
        if (segments.isEmpty()) return segments
        
        // Step 1: Log raw Whisper segments
        SpeakerSegmentationHelper.logWhisperRaw(segments)
        
        // Step 2: Try marker-based detection first (highest priority)
        val markers = SpeakerSegmentationHelper.detectSpeakerMarkers(segments)
        SpeakerSegmentationHelper.logSpeakerMarkers(markers)
        
        if (markers.isNotEmpty()) {
            // Use marker-based detection (more accurate if markers exist)
            AppLogger.d(TAG_TRANSCRIPT, "Using marker-based speaker detection (found %d markers)", markers.size)
            
            val blocks = SpeakerSegmentationHelper.buildSpeakerBlocks(segments, markers)
            SpeakerSegmentationHelper.logSpeakerBlocks(blocks)
            
            val segmentsWithSpeakers = SpeakerSegmentationHelper.assignSpeakersToSegments(segments, blocks)
            SpeakerSegmentationHelper.logFinalSegments(segmentsWithSpeakers)
            
            return segmentsWithSpeakers
        }
        
        // Step 3: Try number-based pattern detection (medium priority)
        AppLogger.d(TAG_TRANSCRIPT, "=== [NUMBER_BASED_DETECTION_START] ===")
        AppLogger.d(TAG_TRANSCRIPT, "No speaker markers found, trying number-based pattern detection on %d segments", segments.size)
        
        val numberSections = NumberBasedSegmentationHelper.detectSections(segments)
        
        if (numberSections.isNotEmpty() && numberSections.size >= 2) {
            // Use number-based detection (found valid sections)
            AppLogger.d(TAG_TRANSCRIPT, "=== [NUMBER_BASED_DETECTION_SUCCESS] ===")
            AppLogger.d(TAG_TRANSCRIPT, "Using number-based speaker detection (found %d sections)", numberSections.size)
            
            numberSections.forEachIndexed { index, section ->
                AppLogger.d(TAG_TRANSCRIPT, 
                    "Section[%d]: number=%d, pattern=%s, start=%.2fs, end=%.2fs, heading=\"%s\"",
                    index, section.number, section.patternType.name, 
                    section.startMs / 1000.0, section.endMs / 1000.0, section.headingText)
            }
            
            val segmentsWithSpeakers = NumberBasedSegmentationHelper.assignSpeakersFromSections(numberSections, segments)
            
            // Log assignment statistics
            val speakerCounts = segmentsWithSpeakers.groupingBy { it.speaker ?: 0 }.eachCount()
            AppLogger.d(TAG_TRANSCRIPT, "Speaker assignment: %s", speakerCounts.entries.joinToString(", ") { 
                "Speaker ${it.key}=${it.value} segments" 
            })
            
            SpeakerSegmentationHelper.logFinalSegments(segmentsWithSpeakers)
            AppLogger.d(TAG_TRANSCRIPT, "=== [NUMBER_BASED_DETECTION_COMPLETE] ===")
            
            return segmentsWithSpeakers
        } else {
            AppLogger.d(TAG_TRANSCRIPT, "=== [NUMBER_BASED_DETECTION_SKIPPED] ===")
            if (numberSections.isEmpty()) {
                AppLogger.d(TAG_TRANSCRIPT, "No number-based sections detected")
            } else {
                AppLogger.d(TAG_TRANSCRIPT, "Only %d section(s) detected (need at least 2 for speaker segmentation)", numberSections.size)
            }
        }
        
        // Step 4: Fallback to heuristic-based detection (question marks, time gaps)
        AppLogger.d(TAG_TRANSCRIPT, "No number patterns found, using heuristic-based speaker detection (question/time-gap)")
        return detectSpeakersWithHeuristics(segments)
    }
    
    /**
     * Greeting words that appear at the START of a sentence (opening of new speaker)
     * These indicate: previous unassigned segments → Speaker A, segments from greeting → Speaker B
     */
    private val greetingWordsAtStart = setOf(
        "hi", "hello", "hey", "greetings",
        "good morning", "good afternoon", "good evening", "good night",
        "morning", "afternoon", "evening",
        "howdy", "hi there", "hello there", "hey there",
        "greetings", "salutations", "good day", "good day to you",
        "hey everyone", "hi everyone", "hello everyone",
        "hey guys", "hi guys", "hello guys",
        "hey all", "hi all", "hello all"
    )
    
    /**
     * Greeting phrases that appear at the END of a sentence (closing of current speaker)
     * These indicate: current segment → Speaker A, next segment → Speaker B
     */
    private val greetingPhrasesAtEnd = setOf(
        "nice to meet you", "pleased to meet you",
        "what's up", "whats up", "sup",
        "nice meeting you", "pleasure to meet you",
        "great to meet you", "good to meet you",
        "nice talking to you", "pleasure talking to you",
        "talk to you later", "speak to you later",
        "catch you later", "see you later"
    )
    
    /**
     * Check if segment starts with a greeting (opening greeting)
     */
    private fun isGreetingAtStart(segment: TranscriptSegment): Boolean {
        val textLower = segment.text.trim().lowercase()
        
        // Check if segment starts with greeting (first few words)
        val firstWords = textLower.split(Regex("\\s+")).take(4).joinToString(" ")
        
        return greetingWordsAtStart.any { greeting ->
            firstWords.startsWith(greeting) || textLower.startsWith("$greeting ")
        }
    }
    
    /**
     * Check if segment ends with a greeting phrase (closing greeting)
     */
    private fun isGreetingAtEnd(segment: TranscriptSegment): Boolean {
        val textLower = segment.text.trim().lowercase()
        
        // Check if segment ends with greeting phrase (last few words)
        val words = textLower.split(Regex("\\s+"))
        if (words.isEmpty()) return false
        
        // Check last 4 words for greeting phrases
        val lastWords = words.takeLast(4).joinToString(" ")
        
        return greetingPhrasesAtEnd.any { greeting ->
            lastWords.contains(greeting) || textLower.endsWith(" $greeting") || textLower.endsWith(greeting)
        }
    }
    
    /**
     * Detect speakers using heuristics: question marks, time gaps, and greetings.
     * Logic:
     * - Priority 1: Question mark (?) → change speaker
     * - Priority 2: Greeting words (hi, hello, good morning...) → change speaker
     * - Priority 3: Time gap > 1.5s → change speaker
     * - After question, next non-question → back to speaker 1
     */
    private fun detectSpeakersWithHeuristics(segments: List<TranscriptSegment>): List<TranscriptSegment> {
        if (segments.isEmpty()) return segments
        
        val speakerAssignments = mutableListOf<Int>()
        var currentSpeaker = 1
        var lastEndTime = 0L
        
        segments.forEachIndexed { index, segment ->
            // Check if segment is a question (ends with ? or ?")
            val textTrimmed = segment.text.trim()
            val isQuestion = segment.isQuestion || 
                textTrimmed.endsWith("?") || 
                textTrimmed.endsWith("?\"") ||
                textTrimmed.endsWith("?'")
            
            val prevSegment = segments.getOrNull(index - 1)
            val prevTextTrimmed = prevSegment?.text?.trim() ?: ""
            val prevIsQuestion = prevSegment?.isQuestion ?: false ||
                prevTextTrimmed.endsWith("?") ||
                prevTextTrimmed.endsWith("?\"") ||
                prevTextTrimmed.endsWith("?'")
            
            // Check if segment has greeting at start or end
            val isGreetingAtStart = isGreetingAtStart(segment)
            val isGreetingAtEnd = isGreetingAtEnd(segment)
            val prevIsGreetingAtEnd = prevSegment?.let { isGreetingAtEnd(it) } ?: false
            
            // Calculate time gap (silence) in seconds
            val silenceGap = if (index > 0) {
                (segment.startTimeMs - lastEndTime) / 1000.0
            } else {
                0.0
            }
            val isLongPause = silenceGap > 1.5
            
            // Logic: Priority 1 = question mark, Priority 2 = greeting at start, Priority 3 = greeting at end (previous), Priority 4 = time gap
            var shouldChangeSpeaker = false
            var changeReason = ""
            
            if (isQuestion) {
                // Priority 1: Question → change speaker
                shouldChangeSpeaker = true
                changeReason = "question"
                AppLogger.d(TAG_TRANSCRIPT, "Segment #%d is question -> changing speaker", index)
            } else if (isGreetingAtStart) {
                // Priority 2: Greeting at start → change speaker (previous unassigned → Speaker A, from here → Speaker B)
                shouldChangeSpeaker = true
                changeReason = "greeting at start"
                AppLogger.d(TAG_TRANSCRIPT, "Segment #%d starts with greeting (\"%s\") -> changing speaker", index, 
                    textTrimmed.take(50))
            } else if (prevIsGreetingAtEnd) {
                // Priority 3: Previous segment ended with greeting → current segment is new speaker
                shouldChangeSpeaker = true
                changeReason = "after greeting at end"
                AppLogger.d(TAG_TRANSCRIPT, "Segment #%d is after greeting at end -> changing speaker", index)
            } else if (isLongPause && !prevIsQuestion) {
                // Priority 4: Time gap > 1.5s (only if not after question)
                shouldChangeSpeaker = true
                changeReason = "time gap"
                AppLogger.d(TAG_TRANSCRIPT, "Segment #%d has long pause (%.2fs) -> changing speaker", index, silenceGap)
            } else if (prevIsQuestion && !isQuestion) {
                // After question, next sentence is not question → back to speaker 1
                shouldChangeSpeaker = true
                changeReason = "answer after question"
                AppLogger.d(TAG_TRANSCRIPT, "Segment #%d is answer after question -> changing speaker", index)
            }
            
            // Change speaker if needed
            if (shouldChangeSpeaker && index > 0) {
                currentSpeaker = if (currentSpeaker == 1) 2 else 1
            }
            
            speakerAssignments.add(currentSpeaker)
            lastEndTime = segment.endTimeMs
        }
        
        // Apply greeting-based assignment: 
        // - If greeting at start found, assign all previous unassigned segments to Speaker A
        // - Only apply to segments that don't already have speaker from other logic
        val result = segments.mapIndexed { index, segment ->
            // Check if this segment starts with greeting
            if (isGreetingAtStart(segment) && index > 0) {
                // Find all previous segments that don't have speaker yet
                // and assign them to opposite speaker (Speaker A)
                val oppositeSpeaker = if (speakerAssignments[index] == 1) 2 else 1
                
                // Assign previous unassigned segments (only if they don't have speaker from other logic)
                // Note: In heuristic detection, we assign all segments, so this is mainly for future use
                // when combining with other detection methods
            }
            
            segment.copy(speaker = speakerAssignments[index])
        }
        
        // Check if we have multiple speakers
        val uniqueSpeakers = speakerAssignments.distinct()
        val hasMultipleSpeakers = uniqueSpeakers.size > 1
        
        AppLogger.d(TAG_TRANSCRIPT, "Heuristic detection result: %d unique speakers detected", uniqueSpeakers.size)
        
        // Log greeting detection statistics
        val greetingAtStartCount = segments.count { isGreetingAtStart(it) }
        val greetingAtEndCount = segments.count { isGreetingAtEnd(it) }
        if (greetingAtStartCount > 0 || greetingAtEndCount > 0) {
            AppLogger.d(TAG_TRANSCRIPT, "Greeting detection: %d at start, %d at end", 
                greetingAtStartCount, greetingAtEndCount)
        }
        
        // Return segments with speaker info (only if multiple speakers detected)
        return if (hasMultipleSpeakers) {
            SpeakerSegmentationHelper.logFinalSegments(result)
            result
        } else {
            // Single speaker - keep segments as is (no speaker assignment)
            AppLogger.d(TAG_TRANSCRIPT, "Single speaker detected, no speaker assignment needed")
            segments
        }
    }
    
    /**
     * Log processed text after speaker detection (sample of first 3 segments).
     */
    private fun logProcessedText(segments: List<TranscriptSegment>) {
        if (segments.isEmpty()) {
            AppLogger.d(TAG_TRANSCRIPT, "=== [PROCESSED_TEXT_AFTER_SPEAKER_DETECTION] ===")
            AppLogger.d(TAG_TRANSCRIPT, "No processed segments")
            return
        }
        
        AppLogger.d(TAG_TRANSCRIPT, "=== [PROCESSED_TEXT_AFTER_SPEAKER_DETECTION] ===")
        AppLogger.d(TAG_TRANSCRIPT, "Total processed segments: %d", segments.size)
        val sampleSize = minOf(5, segments.size)
        segments.take(sampleSize).forEachIndexed { index, segment ->
            val textPreview = if (segment.text.length > 80) {
                segment.text.take(80) + "..."
            } else {
                segment.text
            }
            val speakerLabel = if (segment.speaker != null) "Speaker ${segment.speaker}" else "No speaker"
            AppLogger.d(TAG_TRANSCRIPT, "PROCESSED[%d] speaker=%s time=%.2fs-%.2fs text=\"%s\"", 
                index, speakerLabel, segment.startTimeMs / 1000.0, segment.endTimeMs / 1000.0, textPreview)
        }
        if (segments.size > sampleSize) {
            AppLogger.d(TAG_TRANSCRIPT, "... and %d more processed segments", segments.size - sampleSize)
        }
        
        // Check for BLANK_AUDIO in processed segments
        val blankAudioCount = segments.count { 
            it.text.trim().uppercase() == "BLANK_AUDIO" 
        }
        if (blankAudioCount > 0) {
            AppLogger.w(TAG_TRANSCRIPT, "WARNING: Found %d BLANK_AUDIO segments in processed results (should be filtered)", blankAudioCount)
        }
    }
}

