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
            val rawSegments = transcriptRepository.getTranscriptSegmentsSync(recordingId)
            
            if (rawSegments.isEmpty()) {
                AppLogger.w(TAG_TRANSCRIPT, "No segments found for processing -> recordingId: %s", recordingId)
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
            
            // Apply speaker detection
            val processedSegments = detectSpeakers(rawSegments)
            
            AppLogger.d(TAG_TRANSCRIPT, "Processed %d segments with speaker detection", processedSegments.size)
            
            // Log processed text (first 3 segments as sample)
            logProcessedText(processedSegments)
            
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
        AppLogger.d(TAG_TRANSCRIPT, "No speaker markers found, trying number-based pattern detection")
        val numberSections = NumberBasedSegmentationHelper.detectSections(segments)
        
        if (numberSections.isNotEmpty() && numberSections.size >= 2) {
            // Use number-based detection (found valid sections)
            AppLogger.d(TAG_TRANSCRIPT, "Using number-based speaker detection (found %d sections)", numberSections.size)
            
            val segmentsWithSpeakers = NumberBasedSegmentationHelper.assignSpeakersFromSections(numberSections, segments)
            SpeakerSegmentationHelper.logFinalSegments(segmentsWithSpeakers)
            
            return segmentsWithSpeakers
        }
        
        // Step 4: Fallback to heuristic-based detection (question marks, time gaps)
        AppLogger.d(TAG_TRANSCRIPT, "No number patterns found, using heuristic-based speaker detection (question/time-gap)")
        return detectSpeakersWithHeuristics(segments)
    }
    
    /**
     * Detect speakers using heuristics: question marks and time gaps.
     * Logic:
     * - Priority 1: Question mark (?) → change speaker
     * - Priority 2: Time gap > 1.5s → change speaker
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
            
            // Calculate time gap (silence) in seconds
            val silenceGap = if (index > 0) {
                (segment.startTimeMs - lastEndTime) / 1000.0
            } else {
                0.0
            }
            val isLongPause = silenceGap > 1.5
            
            // Logic: Priority 1 = question mark, Priority 2 = time gap
            var shouldChangeSpeaker = false
            
            if (isQuestion) {
                // Priority 1: Question → change speaker
                shouldChangeSpeaker = true
                AppLogger.d(TAG_TRANSCRIPT, "Segment #%d is question -> changing speaker", index)
            } else if (isLongPause && !prevIsQuestion) {
                // Priority 2: Time gap > 1.5s (only if not after question)
                shouldChangeSpeaker = true
                AppLogger.d(TAG_TRANSCRIPT, "Segment #%d has long pause (%.2fs) -> changing speaker", index, silenceGap)
            } else if (prevIsQuestion && !isQuestion) {
                // After question, next sentence is not question → back to speaker 1
                shouldChangeSpeaker = true
                AppLogger.d(TAG_TRANSCRIPT, "Segment #%d is answer after question -> changing speaker", index)
            }
            
            // Change speaker if needed
            if (shouldChangeSpeaker && index > 0) {
                currentSpeaker = if (currentSpeaker == 1) 2 else 1
            }
            
            speakerAssignments.add(currentSpeaker)
            lastEndTime = segment.endTimeMs
        }
        
        // Check if we have multiple speakers
        val uniqueSpeakers = speakerAssignments.distinct()
        val hasMultipleSpeakers = uniqueSpeakers.size > 1
        
        AppLogger.d(TAG_TRANSCRIPT, "Heuristic detection result: %d unique speakers detected", uniqueSpeakers.size)
        
        // Return segments with speaker info (only if multiple speakers detected)
        return if (hasMultipleSpeakers) {
            val result = segments.mapIndexed { index, segment ->
                segment.copy(speaker = speakerAssignments[index])
            }
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
        if (segments.isEmpty()) return
        
        AppLogger.d(TAG_TRANSCRIPT, "=== [PROCESSED_TEXT_AFTER_SPEAKER_DETECTION] ===")
        val sampleSize = minOf(3, segments.size)
        segments.take(sampleSize).forEachIndexed { index, segment ->
            val textPreview = if (segment.text.length > 100) {
                segment.text.take(100) + "..."
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
    }
}

