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
 * Detect speakers in transcript segments using question-based and time-gap heuristics.
 * Similar logic to WhisperPostProcessor.processWithTimestamps() but works with TranscriptSegment.
 */
private fun detectSpeakers(segments: List<TranscriptSegment>): List<TranscriptSegment> {
    if (segments.isEmpty()) return segments
    
    val speakerAssignments = mutableListOf<Int>()
    var currentSpeaker = 1
    var lastEndTime = 0L
    
    segments.forEachIndexed { index, segment ->
        val isQuestion = segment.isQuestion || segment.text.trim().endsWith("?")
        val prevSegment = segments.getOrNull(index - 1)
        val prevIsQuestion = prevSegment?.isQuestion ?: false
        
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
        } else if (isLongPause && !prevIsQuestion) {
            // Priority 2: Time gap > 1.5s (only if not after question)
            shouldChangeSpeaker = true
        } else if (prevIsQuestion && !isQuestion) {
            // After question, next sentence is not question → back to speaker 1
            shouldChangeSpeaker = true
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
    
    // Return segments with speaker info (only if multiple speakers detected)
    return if (hasMultipleSpeakers) {
        segments.mapIndexed { index, segment ->
            segment.copy(speaker = speakerAssignments[index])
        }
    } else {
        // Single speaker - no speaker labels needed
        segments
    }
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

