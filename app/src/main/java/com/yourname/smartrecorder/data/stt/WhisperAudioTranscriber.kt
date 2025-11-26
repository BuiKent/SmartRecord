package com.yourname.smartrecorder.data.stt

import android.content.Context
import android.net.Uri
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface AudioTranscriber {
    suspend fun transcribeFile(uri: Uri, onProgress: (Int) -> Unit): String
    suspend fun transcribeFileToSegments(uri: Uri, onProgress: (Int) -> Unit): List<WhisperEngine.WhisperSegment>
}

@Singleton
class WhisperAudioTranscriber @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val converter: AudioConverter,
    private val modelProvider: WhisperModelProvider,
    private val modelManager: WhisperModelManager,
    private val engine: WhisperEngine
) : AudioTranscriber {
    
    companion object {
        private const val TAG = "WhisperAudioTranscriber"
    }
    
    override suspend fun transcribeFile(
        uri: Uri,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        
        var tempFile: File? = null
        
        try {
            // Stage 0: Check model exists (should be downloaded on app start)
            onProgress(0)
            if (!modelProvider.isModelReady()) {
                AppLogger.w(TAG_TRANSCRIPT, "Model not found, attempting fallback download...")
                // Fallback: download if somehow missing (shouldn't happen if app initialized correctly)
                modelManager.downloadModel { downloadProgress ->
                    // Download progress: 0-10%
                    onProgress(downloadProgress / 10) // Scale to 0-10%
                }
                AppLogger.d(TAG_TRANSCRIPT, "Model download completed")
            } else {
                onProgress(5) // Model already exists
            }
            
            // Stage 1: Load model (5-15%)
            onProgress(5)
            val modelPtr = modelProvider.getModel()
            onProgress(15)
            
            // Stage 2: Convert audio to WAV (15-30%)
            tempFile = converter.convertToWav(uri) { conversionProgress ->
                onProgress(15 + (conversionProgress * 15 / 100))
            }
            onProgress(30)
            
            // Stage 3: Transcribe (30-95%)
            val segments = engine.transcribe(modelPtr, tempFile) { transcriptionProgress ->
                onProgress(30 + (transcriptionProgress * 65 / 100))
            }
            
            // Stage 4: Post-process with heuristics + timestamps (95-100%)
            onProgress(95)
            val processedTranscript = WhisperPostProcessor.processWithTimestamps(
                segments,
                PostProcessingOptions(
                    useQuestionRule = true,        // Question-based speaker detection
                    useTimeGap = true,             // Time-gap speaker detection
                    processVoiceCommands = true,    // Process voice commands
                    removeFillers = true            // Remove filler words
                )
            )
            
            onProgress(100)
            AppLogger.d(TAG_TRANSCRIPT, "Transcription completed: ${processedTranscript.length} chars")
            processedTranscript
            
        } catch (e: Exception) {
            AppLogger.e(TAG_TRANSCRIPT, "Transcription failed", e)
            throw e
        } finally {
            tempFile?.delete()
        }
    }
    
    override suspend fun transcribeFileToSegments(
        uri: Uri,
        onProgress: (Int) -> Unit
    ): List<WhisperEngine.WhisperSegment> = withContext(Dispatchers.IO) {
        
        var tempFile: File? = null
        
        try {
            // Stage 0: Check model exists (should be downloaded on app start)
            onProgress(0)
            if (!modelProvider.isModelReady()) {
                AppLogger.w(TAG_TRANSCRIPT, "Model not found, attempting fallback download...")
                // Fallback: download if somehow missing (shouldn't happen if app initialized correctly)
                modelManager.downloadModel { downloadProgress ->
                    // Download progress: 0-10%
                    onProgress(downloadProgress / 10) // Scale to 0-10%
                }
                AppLogger.d(TAG_TRANSCRIPT, "Model download completed")
            } else {
                onProgress(5) // Model already exists
            }
            
            // Stage 1: Load model (5-15%)
            onProgress(5)
            val modelPtr = modelProvider.getModel()
            onProgress(15)
            
            // Stage 2: Convert audio to WAV (15-30%)
            tempFile = converter.convertToWav(uri) { conversionProgress ->
                onProgress(15 + (conversionProgress * 15 / 100))
            }
            onProgress(30)
            
            // Stage 3: Transcribe (30-95%)
            val segments = engine.transcribe(modelPtr, tempFile) { transcriptionProgress ->
                onProgress(30 + (transcriptionProgress * 65 / 100))
            }
            
            // Stage 4: Post-process segments (95-100%)
            onProgress(95)
            val processedSegments = segments.map { segment ->
                var processedText = segment.text.trim()
                
                // Apply heuristics
                processedText = WhisperPostProcessor.processVoiceCommands(processedText)
                processedText = WhisperPostProcessor.processEnglishHeuristics(processedText)
                
                WhisperEngine.WhisperSegment(
                    text = processedText,
                    start = segment.start,
                    end = segment.end
                )
            }.filter { it.text.isNotBlank() }
            
            onProgress(100)
            AppLogger.d(TAG_TRANSCRIPT, "Transcription completed: ${processedSegments.size} segments")
            processedSegments
            
        } catch (e: Exception) {
            AppLogger.e(TAG_TRANSCRIPT, "Transcription failed", e)
            throw e
        } finally {
            tempFile?.delete()
        }
    }
}

