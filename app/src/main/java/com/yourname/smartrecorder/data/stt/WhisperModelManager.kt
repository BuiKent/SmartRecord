package com.yourname.smartrecorder.data.stt

import android.content.Context
import android.content.SharedPreferences
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperModelManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .callTimeout(600, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private val modelDir = File(context.filesDir, "whisper-models")
    private val modelName = "ggml-tiny.en.bin"
    private val modelFile = File(modelDir, modelName)
    private val prefs: SharedPreferences = context.getSharedPreferences("smart_recorder_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "WhisperModelManager"
        private const val MODEL_SIZE = 75L * 1024 * 1024 // 75MB
        private const val MODEL_SIZE_TOLERANCE = 0.05 // 5%
        
        // SharedPreferences keys
        private const val PREF_MODEL_DOWNLOADED = "whisper_model_downloaded"
        private const val PREF_MODEL_DOWNLOADING = "whisper_model_downloading"
        private const val PREF_MODEL_DOWNLOAD_START_TIME = "whisper_model_download_start_time"
        
        // Maximum download time (30 minutes) - if exceeded, consider download stuck and allow retry
        private const val MAX_DOWNLOAD_TIME_MS = 30 * 60 * 1000L
        
        private val MODEL_URLS = listOf(
            // Primary URL
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
            // Fallback URLs
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin?download=true",
            "https://github.com/ggerganov/whisper.cpp/releases/download/v1.5.4/ggml-tiny.en.bin"
        )
    }
    
    suspend fun downloadModel(
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        
        // Check if already downloaded and verified
        if (modelFile.exists() && isModelValid(modelFile)) {
            AppLogger.d(TAG_TRANSCRIPT, "Model already exists -> path: ${modelFile.absolutePath}, size: ${modelFile.length() / (1024 * 1024)}MB")
            // Update flag to mark as downloaded
            prefs.edit().putBoolean(PREF_MODEL_DOWNLOADED, true).apply()
            onProgress(100)
            return@withContext
        }
        
        // Check if another process is downloading
        val isDownloading = prefs.getBoolean(PREF_MODEL_DOWNLOADING, false)
        val downloadStartTime = prefs.getLong(PREF_MODEL_DOWNLOAD_START_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        
        if (isDownloading && (currentTime - downloadStartTime) < MAX_DOWNLOAD_TIME_MS) {
            // Another process is downloading, wait and check periodically
            AppLogger.d(TAG_TRANSCRIPT, "Another process is downloading model, waiting...")
            var waitCount = 0
            val maxWait = 60 // Wait up to 60 seconds (60 * 1s)
            
            while (waitCount < maxWait) {
                kotlinx.coroutines.delay(1000) // Wait 1 second
                waitCount++
                
                // Re-check if model is now available
                if (modelFile.exists() && isModelValid(modelFile)) {
                    AppLogger.d(TAG_TRANSCRIPT, "Model was downloaded by another process, skipping download -> path: ${modelFile.absolutePath}")
                    prefs.edit().putBoolean(PREF_MODEL_DOWNLOADED, true).apply()
                    onProgress(100)
                    return@withContext
                }
                
                // Check if download flag is still set
                val stillDownloading = prefs.getBoolean(PREF_MODEL_DOWNLOADING, false)
                if (!stillDownloading) {
                    // Download flag was cleared, but model not ready - might have failed
                    AppLogger.d(TAG_TRANSCRIPT, "Download flag cleared but model not ready, will try downloading")
                    break
                }
            }
            
            // If we've waited too long, proceed with download (previous might be stuck)
            if (waitCount >= maxWait) {
                AppLogger.w(TAG_TRANSCRIPT, "Waited too long for other download, proceeding anyway")
            }
        }
        
        // Try to acquire download lock
        val lockAcquired = try {
            val wasDownloading = prefs.getBoolean(PREF_MODEL_DOWNLOADING, false)
            if (!wasDownloading || (currentTime - downloadStartTime) >= MAX_DOWNLOAD_TIME_MS) {
                // Set download flag
                prefs.edit()
                    .putBoolean(PREF_MODEL_DOWNLOADING, true)
                    .putLong(PREF_MODEL_DOWNLOAD_START_TIME, currentTime)
                    .apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            AppLogger.w(TAG_TRANSCRIPT, "Failed to acquire download lock, proceeding anyway", e)
            true // Proceed anyway to avoid blocking
        }
        
        if (!lockAcquired) {
            // Another process got the lock first, wait and check again
            kotlinx.coroutines.delay(2000)
            if (modelFile.exists() && isModelValid(modelFile)) {
                AppLogger.d(TAG_TRANSCRIPT, "Model is now available after waiting for lock")
                prefs.edit().putBoolean(PREF_MODEL_DOWNLOADED, true).apply()
                onProgress(100)
                return@withContext
            }
            throw IllegalStateException("Cannot acquire download lock, another download is in progress")
        }
        
        try {
            // We have the lock, proceed with download
            AppLogger.d(TAG_TRANSCRIPT, "Acquired download lock, starting download...")
            
            // Clean up any existing invalid model file (only if invalid, not if valid)
            if (modelFile.exists() && !isModelValid(modelFile)) {
                AppLogger.w(TAG_TRANSCRIPT, "Existing model file is invalid, deleting: ${modelFile.absolutePath}")
                modelFile.delete()
            }
            
            modelDir.mkdirs()
            if (!modelDir.exists() || !modelDir.isDirectory) {
                throw IllegalStateException("Cannot create model directory: ${modelDir.absolutePath}")
            }
            
            val tempFile = File(context.cacheDir, "$modelName.tmp")
            
            // Try each URL
            for ((urlIndex, url) in MODEL_URLS.withIndex()) {
                try {
                // Re-check before each URL attempt (another process might have finished)
                if (urlIndex > 0 && modelFile.exists() && isModelValid(modelFile)) {
                    AppLogger.d(TAG_TRANSCRIPT, "Model was downloaded before retry, skipping remaining URLs -> path: ${modelFile.absolutePath}")
                    onProgress(100)
                    return@withContext
                }
                
                AppLogger.d(TAG_TRANSCRIPT, "Downloading from URL ${urlIndex + 1}/${MODEL_URLS.size}")
                
                val downloadedFile = downloadFile(url, tempFile) { progress ->
                    onProgress(progress)
                }
                
                // Verify model before moving
                if (!isModelValid(downloadedFile)) {
                    val actualSize = downloadedFile.length()
                    val expectedMin = (MODEL_SIZE * (1 - MODEL_SIZE_TOLERANCE)).toLong()
                    val expectedMax = (MODEL_SIZE * (1 + MODEL_SIZE_TOLERANCE)).toLong()
                    AppLogger.e(TAG_TRANSCRIPT, "Model verification failed -> actualSize: ${actualSize} bytes, expected: ${expectedMin}-${expectedMax} bytes (${MODEL_SIZE / (1024 * 1024)}MB ± ${MODEL_SIZE_TOLERANCE * 100}%)")
                    throw IllegalStateException("Model verification failed: file size ${actualSize / (1024 * 1024)}MB does not match expected ${MODEL_SIZE / (1024 * 1024)}MB ± ${MODEL_SIZE_TOLERANCE * 100}%")
                }
                
                AppLogger.d(TAG_TRANSCRIPT, "Model verified successfully -> size: ${downloadedFile.length() / (1024 * 1024)}MB, path: ${downloadedFile.absolutePath}")
                
                // Ensure model directory exists
                modelDir.mkdirs()
                if (!modelDir.exists() || !modelDir.isDirectory) {
                    throw IllegalStateException("Cannot create model directory: ${modelDir.absolutePath}")
                }
                
                // Re-check if model file is now valid (another process might have finished)
                if (modelFile.exists() && isModelValid(modelFile)) {
                    AppLogger.d(TAG_TRANSCRIPT, "Model file is now valid, skipping download -> path: ${modelFile.absolutePath}")
                    prefs.edit()
                        .putBoolean(PREF_MODEL_DOWNLOADED, true)
                        .putBoolean(PREF_MODEL_DOWNLOADING, false)
                        .remove(PREF_MODEL_DOWNLOAD_START_TIME)
                        .apply()
                    onProgress(100)
                    return@withContext
                }
                
                // Only delete old model file if it exists and is invalid (protect user data)
                if (modelFile.exists() && !isModelValid(modelFile)) {
                    AppLogger.w(TAG_TRANSCRIPT, "Existing model file is invalid, deleting before overwrite: ${modelFile.absolutePath}")
                    val deleted = modelFile.delete()
                    if (!deleted) {
                        AppLogger.w(TAG_TRANSCRIPT, "Failed to delete invalid model file, will try to overwrite")
                    }
                } else if (modelFile.exists()) {
                    // File exists but we already checked it's invalid earlier - this shouldn't happen
                    AppLogger.w(TAG_TRANSCRIPT, "Model file exists but validation unclear, skipping delete to protect user data")
                }
                
                // Copy to final location (use copyTo for cross-filesystem safety)
                AppLogger.d(TAG_TRANSCRIPT, "Copying model to final location: ${modelFile.absolutePath}")
                try {
                    downloadedFile.copyTo(modelFile, overwrite = true)
                    
                    // Verify file was copied successfully
                    if (!modelFile.exists()) {
                        throw IllegalStateException("Model file does not exist after copy: ${modelFile.absolutePath}")
                    }
                    
                    if (!isModelValid(modelFile)) {
                        modelFile.delete()
                        throw IllegalStateException("Model verification failed after copy to final location")
                    }
                    
                    AppLogger.d(TAG_TRANSCRIPT, "Model copied and verified successfully -> final path: ${modelFile.absolutePath}, size: ${modelFile.length() / (1024 * 1024)}MB")
                    
                } catch (e: Exception) {
                    // Clean up on failure
                    if (modelFile.exists()) {
                        modelFile.delete()
                    }
                    AppLogger.e(TAG_TRANSCRIPT, "Failed to copy model to final location", e)
                    throw e
                } finally {
                    // Clean up temp file after successful copy
                    if (downloadedFile.exists()) {
                        val deleted = downloadedFile.delete()
                        if (!deleted) {
                            AppLogger.w(TAG_TRANSCRIPT, "Failed to delete temp file: ${downloadedFile.absolutePath}")
                        }
                    }
                }
                
                AppLogger.d(TAG_TRANSCRIPT, "Model downloaded and saved successfully")
                
                // Mark as downloaded and clear downloading flag
                prefs.edit()
                    .putBoolean(PREF_MODEL_DOWNLOADED, true)
                    .putBoolean(PREF_MODEL_DOWNLOADING, false)
                    .remove(PREF_MODEL_DOWNLOAD_START_TIME)
                    .apply()
                
                onProgress(100)
                return@withContext
                
            } catch (e: Exception) {
                AppLogger.e(TAG_TRANSCRIPT, "Download failed from URL ${urlIndex + 1}/${MODEL_URLS.size}: $url", e)
                
                // Clean up temp file only if it exists
                if (tempFile.exists()) {
                    val deleted = tempFile.delete()
                    if (!deleted) {
                        AppLogger.w(TAG_TRANSCRIPT, "Failed to delete temp file after error: ${tempFile.absolutePath}")
                    }
                }
                
                // Re-check if model was downloaded by another process before retrying
                if (modelFile.exists() && isModelValid(modelFile)) {
                    AppLogger.d(TAG_TRANSCRIPT, "Model was downloaded by another process during error recovery, skipping remaining URLs")
                    onProgress(100)
                    return@withContext
                }
                
                if (urlIndex == MODEL_URLS.size - 1) {
                    // All URLs failed - clear download flag
                    prefs.edit()
                        .putBoolean(PREF_MODEL_DOWNLOADING, false)
                        .remove(PREF_MODEL_DOWNLOAD_START_TIME)
                        .apply()
                    
                    AppLogger.e(TAG_TRANSCRIPT, "All ${MODEL_URLS.size} download URLs failed. Cannot download Whisper model.")
                    throw IllegalStateException("All download URLs failed", e)
                } else {
                    AppLogger.d(TAG_TRANSCRIPT, "Trying next URL...")
                }
                }
            }
        } finally {
            // Clear download flag on exit (in case of unexpected errors)
            try {
                val stillDownloading = prefs.getBoolean(PREF_MODEL_DOWNLOADING, false)
                if (stillDownloading) {
                    // Only clear if we still have the lock (model not downloaded successfully)
                    val modelExists = modelFile.exists()
                    val modelValid = if (modelExists) isModelValid(modelFile) else false
                    if (!modelExists || !modelValid) {
                        prefs.edit()
                            .putBoolean(PREF_MODEL_DOWNLOADING, false)
                            .remove(PREF_MODEL_DOWNLOAD_START_TIME)
                            .apply()
                        AppLogger.d(TAG_TRANSCRIPT, "Cleared download flag after failure")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_TRANSCRIPT, "Error in finally block while clearing download flag", e)
            }
        }
    }
    
    private suspend fun downloadFile(
        url: String,
        outputFile: File,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}")
        }
        
        val totalBytes = response.body?.contentLength() ?: 0L
        if (totalBytes <= 0) {
            throw IllegalStateException("Invalid content length")
        }
        
        outputFile.parentFile?.mkdirs()
        var downloadedBytes = 0L
        
        onProgress(0)
        
        response.body?.byteStream()?.use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    if (totalBytes > 0) {
                        val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                        onProgress(progress)
                    }
                }
            }
        }
        
        onProgress(100)
        outputFile
    }
    
    private fun isModelValid(modelFile: File): Boolean {
        if (!modelFile.exists() || !modelFile.isFile) {
            AppLogger.w(TAG_TRANSCRIPT, "Model file does not exist or is not a file: ${modelFile.absolutePath}")
            return false
        }
        
        val fileSize = modelFile.length()
        val minSize = (MODEL_SIZE * (1 - MODEL_SIZE_TOLERANCE)).toLong()
        val maxSize = (MODEL_SIZE * (1 + MODEL_SIZE_TOLERANCE)).toLong()
        
        val isValid = fileSize in minSize..maxSize
        if (!isValid) {
            AppLogger.w(TAG_TRANSCRIPT, "Model file size invalid -> size: ${fileSize} bytes (${fileSize / (1024 * 1024)}MB), expected: ${minSize}-${maxSize} bytes (${minSize / (1024 * 1024)}-${maxSize / (1024 * 1024)}MB)")
        }
        
        return isValid
    }
    
    fun isModelDownloaded(): Boolean {
        return modelFile.exists() && isModelValid(modelFile)
    }
    
    fun getModelPath(): String = modelFile.absolutePath
    
    suspend fun deleteModel() = withContext(Dispatchers.IO) {
        if (modelFile.exists()) {
            modelFile.delete()
        }
    }
}

