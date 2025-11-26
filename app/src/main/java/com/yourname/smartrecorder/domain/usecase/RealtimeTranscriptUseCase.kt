package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_REALTIME
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.core.speech.GoogleASRManager
import com.yourname.smartrecorder.core.speech.RecognitionState
import com.yourname.smartrecorder.core.speech.RecognizedToken
import com.yourname.smartrecorder.core.speech.SpeechRecognizerListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use case for realtime transcription during recording using Google ASR.
 * 
 * This use case integrates with GoogleASRManager to provide continuous,
 * real-time speech recognition with partial and final results.
 */
class RealtimeTranscriptUseCase @Inject constructor(
    private val googleASRManager: GoogleASRManager
) {
    
    private var currentCallback: ((String) -> Unit)? = null
    private var accumulatedText = StringBuilder()
    
    /**
     * Start realtime transcription with a callback for transcript updates.
     * 
     * @param onTranscriptUpdate Callback function that receives transcript text chunks
     */
    fun start(onTranscriptUpdate: (String) -> Unit) {
        AppLogger.logUseCase(TAG_USECASE, "RealtimeTranscriptUseCase", "Start requested", null)
        
        currentCallback = onTranscriptUpdate
        accumulatedText.clear()
        
        // Initialize recognizer with listener
        googleASRManager.initialize(object : SpeechRecognizerListener {
            override fun onReady(isReady: Boolean) {
                AppLogger.d(TAG_REALTIME, "ASR ready: $isReady")
                if (isReady) {
                    googleASRManager.startListening()
                } else {
                    onTranscriptUpdate("[Speech recognition not available]")
                }
            }
            
            override fun onStateChanged(state: RecognitionState) {
                AppLogger.d(TAG_REALTIME, "ASR state changed: $state")
            }
            
            override fun onPartialResults(tokens: List<RecognizedToken>) {
                // Convert tokens to text
                val partialText = tokens.joinToString(" ") { it.text }
                if (partialText.isNotEmpty()) {
                    AppLogger.d(TAG_REALTIME, "Partial result: $partialText")
                    // Send partial text with prefix to distinguish from final
                    // Format: "PARTIAL: <text>" so ViewModel can identify it
                    currentCallback?.invoke("PARTIAL:$partialText")
                }
            }
            
            override fun onFinalResults(tokens: List<RecognizedToken>) {
                // Convert tokens to text
                val finalText = tokens.joinToString(" ") { it.text }
                if (finalText.isNotEmpty()) {
                    AppLogger.d(TAG_REALTIME, "Final result: $finalText")
                    // Append to accumulated text
                    if (accumulatedText.isNotEmpty()) {
                        accumulatedText.append(" ")
                    }
                    accumulatedText.append(finalText)
                    // Send accumulated text with prefix to distinguish from partial
                    // Format: "FINAL: <text>" so ViewModel can identify it
                    currentCallback?.invoke("FINAL:${accumulatedText.toString()}")
                }
            }
            
            override fun onError(error: Int, isCritical: Boolean) {
                AppLogger.e(TAG_REALTIME, "ASR error: $error, critical: $isCritical")
                if (isCritical) {
                    currentCallback?.invoke("[Error: Speech recognition failed]")
                }
            }
            
            override fun onRestartStateChanged(isRestarting: Boolean) {
                AppLogger.d(TAG_REALTIME, "ASR restarting: $isRestarting")
            }
            
            override fun onBiasingDisabled() {
                AppLogger.w(TAG_REALTIME, "Biasing disabled due to errors")
            }
        })
    }
    
    /**
     * Stop realtime transcription.
     */
    fun stop() {
        AppLogger.logUseCase(TAG_USECASE, "RealtimeTranscriptUseCase", "Stop requested", null)
        
        googleASRManager.stopListening()
        currentCallback = null
        accumulatedText.clear()
    }
    
    /**
     * Start realtime transcription for a recording session.
     * Returns a Flow of transcript segments as they are generated.
     * 
     * @param recordingId The ID of the recording session
     * @return Flow of transcript text chunks
     */
    fun startRealtimeTranscription(recordingId: String): Flow<String> = flow {
        AppLogger.logUseCase(TAG_USECASE, "RealtimeTranscriptUseCase", "Start requested", 
            mapOf("recordingId" to recordingId))
        
        // This Flow-based API can be implemented if needed
        // For now, use the callback-based start() method
        emit("[Use start() method with callback instead]")
    }
    
    /**
     * Stop realtime transcription.
     */
    fun stopRealtimeTranscription(recordingId: String) {
        AppLogger.logUseCase(TAG_USECASE, "RealtimeTranscriptUseCase", "Stop requested", 
            mapOf("recordingId" to recordingId))
        stop()
    }
}

