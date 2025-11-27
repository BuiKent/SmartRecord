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
     * Detect speakers in transcript segments based on "Speaker one/two/three..." markers.
     */
    private fun detectSpeakers(segments: List<TranscriptSegment>): List<TranscriptSegment> {
        if (segments.isEmpty()) return segments
        
        // Step 1: Log raw Whisper segments
        SpeakerSegmentationHelper.logWhisperRaw(segments)
        
        // Step 2: Detect "Speaker X" markers in transcript
        val markers = SpeakerSegmentationHelper.detectSpeakerMarkers(segments)
        SpeakerSegmentationHelper.logSpeakerMarkers(markers)
        
        // Step 3: Build speaker blocks from markers
        val blocks = SpeakerSegmentationHelper.buildSpeakerBlocks(segments, markers)
        SpeakerSegmentationHelper.logSpeakerBlocks(blocks)
        
        // Step 4: Assign speakers to original segments based on blocks
        val segmentsWithSpeakers = SpeakerSegmentationHelper.assignSpeakersToSegments(segments, blocks)
        SpeakerSegmentationHelper.logFinalSegments(segmentsWithSpeakers)
        
        return segmentsWithSpeakers
    }
}

