package com.yourname.smartrecorder.data.stt

import android.content.Context
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
    
    companion object {
        private const val TAG = "WhisperModelManager"
        private const val MODEL_SIZE = 75L * 1024 * 1024 // 75MB
        private const val MODEL_SIZE_TOLERANCE = 0.05 // 5%
        
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
        
        // Check if already downloaded
        if (modelFile.exists() && isModelValid(modelFile)) {
            AppLogger.d(TAG_TRANSCRIPT, "Model already exists")
            onProgress(100)
            return@withContext
        }
        
        modelDir.mkdirs()
        val tempFile = File(context.cacheDir, "$modelName.tmp")
        
        // Try each URL
        for ((urlIndex, url) in MODEL_URLS.withIndex()) {
            try {
                AppLogger.d(TAG_TRANSCRIPT, "Downloading from URL ${urlIndex + 1}/${MODEL_URLS.size}")
                
                val downloadedFile = downloadFile(url, tempFile) { progress ->
                    onProgress(progress)
                }
                
                // Verify model
                if (!isModelValid(downloadedFile)) {
                    throw IllegalStateException("Model verification failed")
                }
                
                // Move to final location
                if (modelFile.exists()) {
                    modelFile.delete()
                }
                downloadedFile.renameTo(modelFile)
                
                if (!modelFile.exists()) {
                    downloadedFile.copyTo(modelFile, overwrite = true)
                    downloadedFile.delete()
                }
                
                AppLogger.d(TAG_TRANSCRIPT, "Model downloaded successfully")
                onProgress(100)
                return@withContext
                
            } catch (e: Exception) {
                AppLogger.e(TAG_TRANSCRIPT, "Download failed from URL $urlIndex", e)
                tempFile.delete()
                
                if (urlIndex == MODEL_URLS.size - 1) {
                    throw IllegalStateException("All download URLs failed", e)
                }
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
            return false
        }
        
        val fileSize = modelFile.length()
        val minSize = (MODEL_SIZE * (1 - MODEL_SIZE_TOLERANCE)).toLong()
        val maxSize = (MODEL_SIZE * (1 + MODEL_SIZE_TOLERANCE)).toLong()
        
        return fileSize in minSize..maxSize
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

