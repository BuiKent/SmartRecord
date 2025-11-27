package com.yourname.smartrecorder.domain.usecase

import android.net.Uri
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.data.stt.WhisperAudioTranscriber
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import com.yourname.smartrecorder.domain.repository.TranscriptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Detect speakers in transcript segments based on "Speaker one/two/three..." markers.
 * This replaces heuristic-based detection with marker-based detection for accuracy.
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

/**
 * Use case for generating transcript from audio file using Whisper.
 */
class GenerateTranscriptUseCase @Inject constructor(
    private val transcriptRepository: TranscriptRepository,
    private val transcriber: WhisperAudioTranscriber
) {
    suspend operator fun invoke(
        recording: Recording,
        onProgress: (Int) -> Unit = {}
    ): List<TranscriptSegment> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "GenerateTranscriptUseCase", "Starting", 
            mapOf("recordingId" to recording.id, "filePath" to recording.filePath))
        
        val audioFile = File(recording.filePath)
        if (!audioFile.exists()) {
            AppLogger.e(TAG_TRANSCRIPT, "Audio file not found -> path: %s", null, recording.filePath)
            throw IllegalStateException("Audio file not found: ${recording.filePath}")
        }
        
        val fileSize = audioFile.length()
        val estimatedDurationMs = recording.durationMs
        AppLogger.d(TAG_TRANSCRIPT, "Audio file info -> size: %d bytes, duration: %d ms", 
            fileSize, estimatedDurationMs)
        
        try {
            // Convert File path to Uri
            val uri = Uri.fromFile(audioFile)
            
            // Transcribe using Whisper
            val progressLogger = AppLogger.ProgressLogger(TAG_TRANSCRIPT, "[GenerateTranscriptUseCase] Transcription")
            val whisperSegments = transcriber.transcribeFileToSegments(uri) { progress ->
                onProgress(progress)
                // Only log at milestones (every 20%) to reduce log spam
                progressLogger.logProgress(progress)
            }
            
            // Convert WhisperEngine.WhisperSegment to TranscriptSegment
            val rawSegments = whisperSegments.mapIndexed { index, whisperSegment ->
                TranscriptSegment(
                    id = index.toLong(),
                    recordingId = recording.id,
                    startTimeMs = (whisperSegment.start * 1000).toLong(),
                    endTimeMs = (whisperSegment.end * 1000).toLong(),
                    text = whisperSegment.text,
                    isQuestion = whisperSegment.text.trim().endsWith("?")
                )
            }
            
            // Apply speaker detection
            val segments = detectSpeakers(rawSegments)
        
            AppLogger.d(TAG_TRANSCRIPT, "Generated %d transcript segments with speaker detection", segments.size)
            
            // Save to repository
            AppLogger.d(TAG_TRANSCRIPT, "Saving transcript segments to database")
            transcriptRepository.saveTranscriptSegments(recording.id, segments)
            
            onProgress(100)
            
            val duration = System.currentTimeMillis() - startTime
            AppLogger.logUseCase(TAG_USECASE, "GenerateTranscriptUseCase", "Completed", 
                mapOf("recordingId" to recording.id, "segments" to segments.size, "duration" to "${duration}ms"))
            AppLogger.logPerformance(TAG_TRANSCRIPT, "GenerateTranscriptUseCase", duration, 
                "segments=${segments.size}, fileSize=${fileSize}bytes")
            
            segments
            
        } catch (e: Exception) {
            AppLogger.e(TAG_TRANSCRIPT, "Transcription failed", e)
            throw e
        }
    }
}

