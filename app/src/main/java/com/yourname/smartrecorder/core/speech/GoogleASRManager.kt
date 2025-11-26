package com.yourname.smartrecorder.core.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_REALTIME
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.Volatile

/**
 * Manages Google Speech Recognition API for real-time transcription.
 * 
 * Features:
 * - Continuous listening with auto-restart
 * - Beep sound suppression
 * - Partial and final results handling
 * - Error recovery
 * - Warmup for reduced latency
 * - Offline mode support
 */
@Singleton
class GoogleASRManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = TAG_REALTIME
        private const val PREF_NAME = "google_asr_prefs"
        private const val PREF_KEY_BIASING_SUPPORT = "biasing_supported"
        private const val PREF_KEY_BEEP_STRATEGY = "beep_strategy"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val connectivityManager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: SpeechRecognizerListener? = null
    
    @Volatile
    private var isListeningActive = false
    
    @Volatile
    private var isMuted = false
    
    @Volatile
    private var isRestarting = false
    
    @Volatile
    private var isPerformingGracefulRestart = false
    
    @Volatile
    private var needsRecreation = false
    
    @Volatile
    private var isWarmedUp = false
    
    private var languageCode: String = "en-US"
    private var biasingStrings: List<String> = emptyList()
    private var isBiasingSupported: Boolean = true
    
    private var beepStrategy: BeepSuppressionStrategy = BeepSuppressionStrategy.DEFAULT
    private var quickFailureCount = 0
    
    // Partial results debouncing
    private val partialDebouncer = Handler(Looper.getMainLooper())
    private var pendingPartialBundle: Bundle? = null
    private var lastPartialTime = 0L
    private var partialBurstCount = 0
    
    // Duplicate detection
    private var lastSentPartial: String = ""
    private var duplicateSkipCount = 0
    
    // Partial results cache for merging
    private var partialResultsCache: List<RecognizedToken> = emptyList()
    
    // Session tracking
    private var sessionId = 0
    private var sessionStartTime = 0L
    private var lastStartTime = 0L
    
    // Error counters
    private var busyErrorCounter = 0
    private var consecutiveAudioErrorCount = 0
    
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            AppLogger.d(TAG, "onReadyForSpeech")
            if (isPerformingGracefulRestart) {
                isPerformingGracefulRestart = false
                isRestarting = false
                listener?.onRestartStateChanged(false)
            }
            listener?.onStateChanged(RecognitionState.LISTENING)
        }
        
        override fun onBeginningOfSpeech() {
            AppLogger.d(TAG, "onBeginningOfSpeech")
        }
        
        override fun onRmsChanged(rmsdB: Float) {
            // Audio level changes - can be used for visualization
        }
        
        override fun onBufferReceived(buffer: ByteArray?) {
            // Not used
        }
        
        override fun onEndOfSpeech() {
            AppLogger.d(TAG, "onEndOfSpeech")
        }
        
        override fun onError(error: Int) {
            handleError(error)
        }
        
        override fun onResults(results: Bundle?) {
            if (!isListeningActive) return
            resetAllCounters()
            
            if (results == null) {
                restartListeningLoop()
                return
            }
            
            // Extract tokens with confidence
            val finalTokens = extractTokensWithConfidence(results)
            
            // Merge with partial results cache
            val mergedTokens = if (partialResultsCache.isNotEmpty()) {
                AppLogger.d(TAG, "Merging partialCache=${partialResultsCache.size} tokens, finalTokens=${finalTokens.size} tokens")
                mergeResults(partialResultsCache, finalTokens)
            } else {
                finalTokens
            }
            
            // Apply noise filtering
            val cleanedTokens = NoiseFilter.clean(mergedTokens)
            
            if (cleanedTokens.isNotEmpty()) {
                listener?.onFinalResults(cleanedTokens)
            }
            
            // Clear cache and restart
            partialResultsCache = emptyList()
            restartListeningLoop()
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            if (!isListeningActive) return
            resetAllCounters()
            
            if (partialResults == null) return
            
            // Adaptive Smart Debounce with Burst Detection
            val now = System.currentTimeMillis()
            val timeSinceLastPartial = now - lastPartialTime
            
            // Detect burst (multiple partials in quick succession)
            if (timeSinceLastPartial < 200L) {
                partialBurstCount++
            } else {
                partialBurstCount = 0
            }
            lastPartialTime = now
            
            // Calculate adaptive debounce delay
            val debounceDelay = when {
                partialBurstCount >= 5 -> 100L  // Heavy burst → wait longer
                partialBurstCount >= 3 -> 50L   // Moderate burst → short wait
                else -> 0L                      // Normal → no delay (realtime)
            }
            
            if (debounceDelay > 0L) {
                // Debounce: Schedule delayed processing
                pendingPartialBundle = partialResults
                partialDebouncer.postDelayed({
                    if (isListeningActive && pendingPartialBundle != null) {
                        processPartialResultsInternal(pendingPartialBundle!!)
                        pendingPartialBundle = null
                    }
                }, debounceDelay)
            } else {
                // Process immediately (no burst detected)
                processPartialResultsInternal(partialResults)
            }
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {
            // Not used
        }
    }
    
    /**
     * Initialize the recognizer.
     */
    fun initialize(listener: SpeechRecognizerListener) {
        this.listener = listener
        loadStrategy()
        createRecognizer()
    }
    
    /**
     * Start listening for speech.
     */
    fun startListening() {
        if (isListeningActive) return
        
        // Ensure recognizer exists
        ensureRecognizer()
        
        // Check permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            AppLogger.e(TAG, "RECORD_AUDIO permission not granted!")
            listener?.onError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, isCritical = true)
            return
        }
        
        AppLogger.d(TAG, "startListening: User initiated start.")
        isListeningActive = true
        resetAllCounters()
        partialResultsCache = emptyList()
        resetDebounceState()
        
        internalStart()
    }
    
    /**
     * Stop listening.
     */
    fun stopListening() {
        AppLogger.d(TAG, "stopListening called")
        isListeningActive = false
        isPerformingGracefulRestart = false
        isRestarting = false
        listener?.onRestartStateChanged(false)
        
        mainHandler.post {
            speechRecognizer?.cancel()
        }
        
        unmuteAllBeepStreams()
        resetAllCounters()
    }
    
    /**
     * Update biasing strings for better accuracy.
     */
    fun updateBiasingStrings(newStrings: List<String>) {
        val distinctNewStrings = newStrings.distinct()
        if (biasingStrings == distinctNewStrings) return
        
        biasingStrings = distinctNewStrings
        AppLogger.d(TAG, "Updating ASR bias list with ${biasingStrings.size} words.")
        
        if (isListeningActive && isBiasingSupported) {
            restartListeningGracefully()
        }
    }
    
    /**
     * Set language code (e.g., "en-US", "vi-VN").
     */
    fun setLanguage(languageCode: String) {
        this.languageCode = languageCode
    }
    
    /**
     * Check if device has internet connectivity.
     * Returns true if connected to internet, false otherwise.
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error checking network availability", e)
            false
        }
    }
    
    /**
     * Determine if should prefer offline mode.
     * Returns true only if no internet is available (fallback to offline).
     * Returns false if internet is available (prefer online).
     */
    private fun shouldPreferOffline(): Boolean {
        val hasInternet = isNetworkAvailable()
        val preferOffline = !hasInternet
        AppLogger.d(TAG, "Network available: $hasInternet, Prefer offline: $preferOffline")
        return preferOffline
    }
    
    /**
     * Destroy the recognizer.
     */
    fun destroy() {
        AppLogger.d(TAG, "destroy() called — cleaning up")
        isListeningActive = false
        isPerformingGracefulRestart = false
        isWarmedUp = false
        needsRecreation = true
        mainHandler.removeCallbacksAndMessages(null)
        partialDebouncer.removeCallbacksAndMessages(null)
        
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        unmuteAllBeepStreams()
    }
    
    // Private methods
    
    private fun createRecognizer() {
        try {
            // Reuse existing recognizer if available and valid
            if (speechRecognizer != null && !needsRecreation) {
                AppLogger.d(TAG, "Reusing existing recognizer instance")
                listener?.onReady(true)
                return
            }
            
            // Only destroy and recreate if needed
            if (needsRecreation) {
                speechRecognizer?.destroy()
                speechRecognizer = null
                needsRecreation = false
            }
            
            val isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
            if (!isAvailable) {
                AppLogger.e(TAG, "Speech recognition not available on this device")
                listener?.onReady(false)
                return
            }
            
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }
            AppLogger.d(TAG, "SpeechRecognizer created successfully")
            listener?.onReady(true)
            
            // Warmup after a short delay
            mainHandler.postDelayed({ warmupRecognizer() }, 200L)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create SpeechRecognizer", e)
            listener?.onReady(false)
        }
    }
    
    private fun ensureRecognizer() {
        if (speechRecognizer == null || needsRecreation) {
            createRecognizer()
        }
    }
    
    private fun warmupRecognizer() {
        if (isWarmedUp || speechRecognizer == null || isListeningActive) {
            return
        }
        
        mainHandler.post {
            if (isListeningActive || speechRecognizer == null) {
                return@post
            }
            
            try {
                AppLogger.d(TAG, "Warming up recognizer...")
                val intent = getOptimizedRecognizerIntent()
                speechRecognizer?.startListening(intent)
                
                // Cancel immediately after short delay to warm up the engine
                mainHandler.postDelayed({
                    if (!isListeningActive && speechRecognizer != null) {
                        speechRecognizer?.cancel()
                        isWarmedUp = true
                        AppLogger.d(TAG, "Recognizer warmup complete")
                    }
                }, 100)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Warmup failed (non-critical)", e)
                isWarmedUp = true
            }
        }
    }
    
    private fun internalStart() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context) || !isListeningActive) return@post
            lastStartTime = System.currentTimeMillis()
            
            // Increment sessionId and record session start time
            sessionId++
            sessionStartTime = System.currentTimeMillis()
            
            listener?.onStateChanged(RecognitionState.LISTENING)
            muteBeep()  // Mute beep before starting
            
            try {
                speechRecognizer?.startListening(getOptimizedRecognizerIntent())
            } catch (e: Exception) {
                AppLogger.e(TAG, "startListening failed", e)
                unmuteAllBeepStreams()
            }
        }
    }
    
    private fun getOptimizedRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Free-form model for natural sentences
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            
            // Language configuration
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            
            // Partial results for low latency
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            
            // Multiple alternatives for confidence extraction
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            
            // Offline preference: Only prefer offline if no internet is available
            // Otherwise, prefer online (better accuracy, real-time updates)
            val preferOffline = shouldPreferOffline()
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            AppLogger.d(TAG, "Intent configured with preferOffline=$preferOffline (network available: ${isNetworkAvailable()})")
            
            // Continuous listening configuration
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L)
            
            // Biasing strings (contextual hints)
            if (isBiasingSupported && biasingStrings.isNotEmpty()) {
                AppLogger.d(TAG, "Applying ${biasingStrings.size} biasing strings to intent.")
                putStringArrayListExtra("android.speech.extra.BIASING_STRINGS", ArrayList(biasingStrings))
            }
        }
    }
    
    private fun restartListeningLoop() {
        if (isListeningActive) {
            internalStart()
        }
    }
    
    private fun restartListeningGracefully() {
        if (!isListeningActive || !isBiasingSupported) return
        
        mainHandler.post {
            AppLogger.d(TAG, "Initiating graceful restart for biasing update...")
            isPerformingGracefulRestart = true
            isRestarting = true
            listener?.onRestartStateChanged(true)
            speechRecognizer?.cancel()
        }
    }
    
    private fun processPartialResultsInternal(partialResults: Bundle) {
        // Extract tokens with confidence
        val partialTokens = extractTokensWithConfidence(partialResults)
        
        // Skip empty results early
        if (partialTokens.isEmpty()) {
            return
        }
        
        // Cache for merging with final results
        partialResultsCache = partialTokens
        
        // Apply noise filtering
        val cleanedTokens = NoiseFilter.clean(partialTokens)
        
        if (cleanedTokens.isEmpty()) {
            return
        }
        
        // Skip duplicate partial results
        val partialText = cleanedTokens.joinToString(" ") { it.text }
        if (partialText == lastSentPartial) {
            duplicateSkipCount++
            if (duplicateSkipCount % 10 == 0) {
                AppLogger.d(TAG, "Skipped $duplicateSkipCount duplicate partial results")
            }
            return
        }
        
        lastSentPartial = partialText
        duplicateSkipCount = 0
        
        // Send to listener
        listener?.onPartialResults(cleanedTokens)
    }
    
    private fun extractTokensWithConfidence(results: Bundle): List<RecognizedToken> {
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?: return emptyList()
        
        // Extract top-3 alternatives
        val top3Alternatives = matches.take(3)
        val confidenceScores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        
        // Get top result and confidence
        val topResult = top3Alternatives.firstOrNull() ?: return emptyList()
        val topConfidence = confidenceScores?.firstOrNull() ?: estimateConfidenceFromRank(0)
        
        // Split top result into tokens
        val topTokens = topResult.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        
        // Extract alternatives from top-2 and top-3
        return topTokens.mapIndexed { tokenIndex, tokenText ->
            val wordAlternatives = mutableListOf<String>()
            
            // Extract from top-2 (index 1) if exists
            if (top3Alternatives.size > 1) {
                val alt2Tokens = top3Alternatives[1].split(Regex("\\s+")).filter { it.isNotBlank() }
                if (tokenIndex < alt2Tokens.size) {
                    val alt2Word = alt2Tokens[tokenIndex]
                    if (alt2Word.lowercase() != tokenText.lowercase()) {
                        wordAlternatives.add(alt2Word)
                    }
                }
            }
            
            // Extract from top-3 (index 2) if exists
            if (top3Alternatives.size > 2) {
                val alt3Tokens = top3Alternatives[2].split(Regex("\\s+")).filter { it.isNotBlank() }
                if (tokenIndex < alt3Tokens.size) {
                    val alt3Word = alt3Tokens[tokenIndex]
                    if (alt3Word.lowercase() != tokenText.lowercase() && 
                        (wordAlternatives.isEmpty() || alt3Word.lowercase() != wordAlternatives[0].lowercase())) {
                        wordAlternatives.add(alt3Word)
                    }
                }
            }
            
            RecognizedToken(
                text = tokenText,
                confidence = topConfidence,
                alternatives = wordAlternatives
            )
        }
    }
    
    private fun estimateConfidenceFromRank(rank: Int): Float {
        // Estimate confidence based on rank (0 = top result, 1 = second, etc.)
        return when (rank) {
            0 -> 0.85f
            1 -> 0.70f
            2 -> 0.55f
            else -> 0.40f
        }
    }
    
    private fun mergeResults(partial: List<RecognizedToken>, final: List<RecognizedToken>): List<RecognizedToken> {
        // Simple merge: prefer final results, but boost confidence if token appears in both
        val finalMap = final.associateBy { it.text.lowercase() }
        
        return final.map { finalToken ->
            val partialToken = partial.find { it.text.lowercase() == finalToken.text.lowercase() }
            if (partialToken != null) {
                // Boost confidence if appears in both
                finalToken.copy(confidence = minOf(1.0f, finalToken.confidence * 1.1f))
            } else {
                finalToken
            }
        }
    }
    
    private fun handleError(error: Int) {
        if (!isListeningActive) {
            unmuteAllBeepStreams()
            return
        }
        
        // Handle graceful restart
        if (isPerformingGracefulRestart && error == SpeechRecognizer.ERROR_CLIENT) {
            AppLogger.d(TAG, "Graceful restart: Ignored expected ERROR_CLIENT.")
            isPerformingGracefulRestart = false
            mainHandler.postDelayed({ restartListeningLoop() }, 250L)
            return
        }
        
        // Handle recognizer busy
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            busyErrorCounter++
            
            if (isBiasingSupported && busyErrorCounter >= 1) {
                // Disable biasing permanently if too many busy errors
                AppLogger.e(TAG, "Too many busy errors. Disabling biasing feature permanently.")
                isBiasingSupported = false
                prefs.edit().putBoolean(PREF_KEY_BIASING_SUPPORT, false).apply()
                listener?.onBiasingDisabled()
                isRestarting = true
                listener?.onRestartStateChanged(true)
                restartListeningLoop()
                return
            }
            
            // Retry after 300ms
            isRestarting = true
            listener?.onRestartStateChanged(true)
            mainHandler.postDelayed({ restartListeningLoop() }, 300L)
            return
        }
        
        // Handle audio errors
        if (error == SpeechRecognizer.ERROR_AUDIO) {
            consecutiveAudioErrorCount++
            AppLogger.w(TAG, "Audio error (count: $consecutiveAudioErrorCount)")
        }
        
        // Handle recoverable errors
        val isRecoverable = error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_AUDIO
        
        if (isRecoverable) {
            if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                listener?.onError(error, isCritical = false)
            }
            handleRecoverableError()
            isRestarting = true
            listener?.onRestartStateChanged(true)
            restartListeningLoop()
            return
        }
        
        // Critical error
        AppLogger.e(TAG, "Critical speech error: $error. Shutting down.")
        unmuteAllBeepStreams()
        isListeningActive = false
        needsRecreation = true
        listener?.onError(error, isCritical = true)
        listener?.onStateChanged(RecognitionState.ERROR)
    }
    
    private fun handleRecoverableError() {
        val timeSinceStart = System.currentTimeMillis() - lastStartTime
        if (beepStrategy != BeepSuppressionStrategy.HEAVY_DUTY && timeSinceStart < 1500) {
            quickFailureCount++
            if (quickFailureCount >= 2) {
                AppLogger.w(TAG, "Consecutive quick failures. Escalating to HEAVY_DUTY strategy.")
                beepStrategy = BeepSuppressionStrategy.HEAVY_DUTY
                saveStrategy(beepStrategy)
                quickFailureCount = 0
            }
        } else {
            quickFailureCount = 0
        }
    }
    
    private fun muteBeep() {
        if (isMuted) return
        AppLogger.d(TAG, "Muting streams with strategy: $beepStrategy")
        try {
            // Mute notification stream (beep sound)
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_NOTIFICATION, 
                AudioManager.ADJUST_MUTE, 
                0
            )
            
            // Mute system stream if needed
            if (beepStrategy == BeepSuppressionStrategy.HEAVY_DUTY) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_SYSTEM, 
                    AudioManager.ADJUST_MUTE, 
                    0
                )
            }
            isMuted = true
        } catch (se: SecurityException) {
            AppLogger.w(TAG, "Mute not allowed by system policy.", se)
        }
    }
    
    private fun unmuteAllBeepStreams() {
        if (!isMuted) return
        AppLogger.d(TAG, "Unmuting all streams.")
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            isMuted = false
        } catch (se: SecurityException) {
            AppLogger.w(TAG, "Unmute not allowed by system policy.", se)
        }
    }
    
    private fun resetAllCounters() {
        quickFailureCount = 0
        busyErrorCounter = 0
        consecutiveAudioErrorCount = 0
    }
    
    private fun resetDebounceState() {
        lastPartialTime = 0L
        partialBurstCount = 0
        pendingPartialBundle = null
        partialDebouncer.removeCallbacksAndMessages(null)
    }
    
    private fun loadStrategy() {
        val strategyName = prefs.getString(PREF_KEY_BEEP_STRATEGY, BeepSuppressionStrategy.DEFAULT.name)
        beepStrategy = try {
            BeepSuppressionStrategy.valueOf(strategyName ?: BeepSuppressionStrategy.DEFAULT.name)
        } catch (e: Exception) {
            BeepSuppressionStrategy.DEFAULT
        }
        
        isBiasingSupported = prefs.getBoolean(PREF_KEY_BIASING_SUPPORT, true)
    }
    
    private fun saveStrategy(strategy: BeepSuppressionStrategy) {
        prefs.edit().putString(PREF_KEY_BEEP_STRATEGY, strategy.name).apply()
    }
    
    private enum class BeepSuppressionStrategy {
        DEFAULT,      // Only mute notification stream
        HEAVY_DUTY    // Mute both system and notification streams
    }
}

