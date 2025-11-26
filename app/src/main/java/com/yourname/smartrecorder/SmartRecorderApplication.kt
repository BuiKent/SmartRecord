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
            // PRIORITY 1: Check if model file exists and is valid (protect user data)
            // This prevents deleting model that user might have manually placed
            if (modelManager.isModelDownloaded()) {
                AppLogger.d(TAG_TRANSCRIPT, "Whisper model already exists in internal storage: %s", 
                    modelManager.getModelPath())
                // Ensure flag is set to protect data on next startup
                val prefs = getSharedPreferences("smart_recorder_prefs", MODE_PRIVATE)
                prefs.edit().putBoolean("whisper_model_downloaded", true).apply()
                return
            }
            
            // PRIORITY 2: Check download flag (backup check)
            val prefs = getSharedPreferences("smart_recorder_prefs", MODE_PRIVATE)
            val modelDownloaded = prefs.getBoolean("whisper_model_downloaded", false)
            
            if (modelDownloaded) {
                // Flag says downloaded but file missing - might have been deleted by user/system
                AppLogger.w(TAG_TRANSCRIPT, "Model file missing but flag says downloaded (possibly deleted by user/system), re-downloading...")
            } else {
                AppLogger.d(TAG_TRANSCRIPT, "First launch - downloading Whisper model to internal storage...")
            }
            
            // Download model (WhisperModelManager will handle locking and prevent duplicates)
            // The flag will be set by WhisperModelManager after successful download
            val progressLogger = AppLogger.ProgressLogger(TAG_TRANSCRIPT, "[SmartRecorderApplication] Model download")
            modelManager.downloadModel { progress ->
                progressLogger.logProgress(progress)
            }
            
            // Verify download succeeded (WhisperModelManager already sets the flag)
            if (modelManager.isModelDownloaded()) {
                AppLogger.d(TAG_TRANSCRIPT, "Whisper model downloaded and saved to internal storage: %s", 
                    modelManager.getModelPath())
            } else {
                AppLogger.e(TAG_TRANSCRIPT, "Model download completed but file verification failed")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG_TRANSCRIPT, "Failed to initialize Whisper model", e)
            // Don't crash app, model will be downloaded when needed (fallback in WhisperAudioTranscriber)
        }
    }
}

