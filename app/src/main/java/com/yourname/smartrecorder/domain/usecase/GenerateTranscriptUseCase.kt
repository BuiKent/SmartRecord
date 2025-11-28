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
 * Use case for generating transcript from audio file using Whisper.
 * This saves RAW segments only (no speaker processing).
 * Speaker processing happens later in ProcessTranscriptUseCase (background).
 */
class GenerateTranscriptUseCase @Inject constructor(
    private val transcriptRepository: TranscriptRepository,
    private val transcriber: WhisperAudioTranscriber
) {
    suspend operator fun invoke(
        recording: Recording,
        onProgress: (Int) -> Unit = {},
        onRawSegmentsSaved: (String) -> Unit = {}  // Callback when raw segments are saved (for immediate navigation)
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
            val whisperSegments = transcriber.transcribeFileToSegments(uri) { progress ->
                onProgress(progress)
                // Progress logging is handled by ImportAudioViewModel to avoid duplicate logs
            }
            
            // Convert WhisperEngine.WhisperSegment to TranscriptSegment (RAW - no speaker processing)
            val rawSegments = whisperSegments
                .mapIndexed { index, whisperSegment ->
                    TranscriptSegment(
                        id = index.toLong(),
                        recordingId = recording.id,
                        startTimeMs = (whisperSegment.start * 1000).toLong(),
                        endTimeMs = (whisperSegment.end * 1000).toLong(),
                        text = whisperSegment.text,
                        isQuestion = whisperSegment.text.trim().endsWith("?"),
                        speaker = null  // Not processed yet - will be processed in background
                    )
                }
                .filter { segment ->
                    // Filter out BLANK_AUDIO segments (case-insensitive)
                    val textUpper = segment.text.trim().uppercase()
                    textUpper != "BLANK_AUDIO" && textUpper.isNotBlank()
                }
        
            AppLogger.d(TAG_TRANSCRIPT, "Generated %d RAW transcript segments (no speaker processing, filtered BLANK_AUDIO)", rawSegments.size)
            
            // Log raw text immediately after transcription (first 3 segments as sample)
            logRawText(rawSegments)
            
            // Save RAW segments to repository immediately (fast - no processing)
            AppLogger.d(TAG_TRANSCRIPT, "Saving RAW transcript segments to database (fast path)")
            transcriptRepository.saveTranscriptSegments(recording.id, rawSegments)
            
            // Notify that raw segments are saved (for immediate navigation)
            AppLogger.d(TAG_TRANSCRIPT, "Raw segments saved -> triggering navigation callback")
            onRawSegmentsSaved(recording.id)
            
            // Return raw segments (speaker processing will happen in background later)
            val segments = rawSegments
            
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
    
    /**
     * Log raw text from Whisper (sample of first 3 segments).
     * This is called immediately after transcription completes, before saving to DB.
     */
    private fun logRawText(segments: List<TranscriptSegment>) {
        if (segments.isEmpty()) return
        
        AppLogger.d(TAG_TRANSCRIPT, "=== [RAW_TEXT_FROM_WHISPER] ===")
        val sampleSize = minOf(3, segments.size)
        segments.take(sampleSize).forEachIndexed { index, segment ->
            val textPreview = if (segment.text.length > 100) {
                segment.text.take(100) + "..."
            } else {
                segment.text
            }
            AppLogger.d(TAG_TRANSCRIPT, "RAW[%d] time=%.2fs-%.2fs text=\"%s\"", 
                index, segment.startTimeMs / 1000.0, segment.endTimeMs / 1000.0, textPreview)
        }
        if (segments.size > sampleSize) {
            AppLogger.d(TAG_TRANSCRIPT, "... and %d more raw segments", segments.size - sampleSize)
        }
    }
}

