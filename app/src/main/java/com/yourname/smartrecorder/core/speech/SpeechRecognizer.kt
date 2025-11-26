package com.yourname.smartrecorder.core.speech

/**
 * Interface for speech recognition callbacks.
 */
interface SpeechRecognizerListener {
    /**
     * Called when recognizer is ready to start listening.
     */
    fun onReady(isReady: Boolean)
    
    /**
     * Called when recognition state changes.
     */
    fun onStateChanged(state: RecognitionState)
    
    /**
     * Called when partial results are available (real-time updates).
     */
    fun onPartialResults(tokens: List<RecognizedToken>)
    
    /**
     * Called when final results are available.
     */
    fun onFinalResults(tokens: List<RecognizedToken>)
    
    /**
     * Called when an error occurs.
     */
    fun onError(error: Int, isCritical: Boolean)
    
    /**
     * Called when restart state changes.
     */
    fun onRestartStateChanged(isRestarting: Boolean)
    
    /**
     * Called when biasing is disabled due to errors.
     */
    fun onBiasingDisabled() {}
}

/**
 * Recognition states.
 */
enum class RecognitionState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR
}

