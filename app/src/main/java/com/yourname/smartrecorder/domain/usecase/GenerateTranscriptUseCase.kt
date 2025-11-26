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
            val whisperSegments = transcriber.transcribeFileToSegments(uri) { progress ->
                onProgress(progress)
                AppLogger.d(TAG_TRANSCRIPT, "Transcription progress: %d%%", progress)
            }
            
            // Convert WhisperEngine.WhisperSegment to TranscriptSegment
            val segments = whisperSegments.mapIndexed { index, whisperSegment ->
                TranscriptSegment(
                    id = index.toLong(),
                    recordingId = recording.id,
                    startTimeMs = (whisperSegment.start * 1000).toLong(),
                    endTimeMs = (whisperSegment.end * 1000).toLong(),
                    text = whisperSegment.text,
                    isQuestion = whisperSegment.text.trim().endsWith("?")
                )
            }
        
            AppLogger.d(TAG_TRANSCRIPT, "Generated %d transcript segments", segments.size)
            
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

