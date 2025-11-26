package com.yourname.smartrecorder

import android.app.Application
import android.content.SharedPreferences
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import com.yourname.smartrecorder.data.stt.WhisperModelManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SmartRecorderApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    @Inject
    lateinit var modelManager: WhisperModelManager
    
    override fun onCreate() {
        super.onCreate()
        
        AppLogger.d(TAG_TRANSCRIPT, "SmartRecorderApplication onCreate")
        
        // Initialize model download in background after Hilt injection
        // Use post to ensure Hilt has initialized
        applicationScope.launch {
            // Small delay to ensure Hilt injection is complete
            kotlinx.coroutines.delay(100)
            initializeWhisperModel()
        }
    }
    
    private suspend fun initializeWhisperModel() {
        try {
            val prefs = getSharedPreferences("smart_recorder_prefs", MODE_PRIVATE)
            val modelDownloaded = prefs.getBoolean("whisper_model_downloaded", false)
            
            if (!modelDownloaded) {
                AppLogger.d(TAG_TRANSCRIPT, "First launch - downloading Whisper model to internal storage...")
                
                modelManager.downloadModel { progress ->
                    if (progress % 10 == 0 || progress == 100) {
                        AppLogger.d(TAG_TRANSCRIPT, "Model download progress: %d%%", progress)
                    }
                }
                
                // Mark as downloaded
                prefs.edit().putBoolean("whisper_model_downloaded", true).apply()
                AppLogger.d(TAG_TRANSCRIPT, "Whisper model downloaded and saved to internal storage: %s", 
                    modelManager.getModelPath())
            } else {
                // Verify model still exists
                if (modelManager.isModelDownloaded()) {
                    AppLogger.d(TAG_TRANSCRIPT, "Whisper model already exists in internal storage: %s", 
                        modelManager.getModelPath())
                } else {
                    AppLogger.w(TAG_TRANSCRIPT, "Model file missing, re-downloading...")
                    modelManager.downloadModel { progress ->
                        if (progress % 10 == 0 || progress == 100) {
                            AppLogger.d(TAG_TRANSCRIPT, "Model re-download progress: %d%%", progress)
                        }
                    }
                    prefs.edit().putBoolean("whisper_model_downloaded", true).apply()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG_TRANSCRIPT, "Failed to initialize Whisper model", e)
            // Don't crash app, model will be downloaded when needed (fallback in WhisperAudioTranscriber)
        }
    }
}

