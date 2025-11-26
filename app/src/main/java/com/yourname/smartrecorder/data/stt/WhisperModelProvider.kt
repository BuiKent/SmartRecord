package com.yourname.smartrecorder.data.stt

import android.content.Context
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.Volatile

@Singleton
class WhisperModelProvider @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val modelManager: WhisperModelManager,
    private val engine: WhisperEngine
) {
    @Volatile
    private var cachedModelPtr: Long? = null
    
    companion object {
        private const val TAG = "WhisperModelProvider"
        private const val INVALID_MODEL_PTR = 0L
    }
    
    suspend fun getModel(): Long = withContext(Dispatchers.IO) {
        // Check cached model
        cachedModelPtr?.let { modelPtr ->
            if (modelPtr != INVALID_MODEL_PTR) {
                AppLogger.d(TAG_TRANSCRIPT, "Using cached model")
                return@withContext modelPtr
            }
        }
        
        // Check if model exists
        if (!modelManager.isModelDownloaded()) {
            throw IllegalStateException(
                "Model not found. Please download first using WhisperModelManager.downloadModel()"
            )
        }
        
        // Load model
        val modelPath = modelManager.getModelPath()
        val modelPtr = engine.initModelFromPath(modelPath)
        
        if (modelPtr == INVALID_MODEL_PTR) {
            throw IllegalStateException("Failed to load model")
        }
        
        cachedModelPtr = modelPtr
        AppLogger.d(TAG_TRANSCRIPT, "Model loaded successfully")
        modelPtr
    }
    
    suspend fun freeModel() = withContext(Dispatchers.IO) {
        cachedModelPtr?.let { modelPtr ->
            if (modelPtr != INVALID_MODEL_PTR) {
                engine.freeModel(modelPtr)
            }
            cachedModelPtr = null
        }
    }
    
    fun isModelReady(): Boolean {
        return modelManager.isModelDownloaded()
    }
    
    fun clearCache() {
        cachedModelPtr = null
    }
}

