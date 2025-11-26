package com.yourname.smartrecorder.data.stt

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperEngine @Inject constructor() {

    companion object {
        private const val TAG = "WhisperEngine"
        private const val WAV_HEADER_SIZE = 44
        private const val SAMPLE_RATE = 16000
        private const val INVALID_MODEL_PTR = 0L
        
        init {
            try {
                System.loadLibrary("whisper")
                AppLogger.d(TAG, "Native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                AppLogger.e(TAG, "Failed to load native library", e)
            }
        }
    }
    
    // JNI functions
    external fun initModel(modelPath: String): Long
    external fun transcribeAudio(modelPtr: Long, audioData: ShortArray, sampleRate: Int): String
    external fun freeModel(modelPtr: Long)
    
    data class WhisperSegment(
        val text: String,
        val start: Double,
        val end: Double
    )
    
    suspend fun transcribe(
        modelPtr: Long,
        wavFile: File,
        onProgress: (Int) -> Unit = {}
    ): List<WhisperSegment> = withContext(Dispatchers.IO) {
        
        if (modelPtr == INVALID_MODEL_PTR) {
            throw IllegalStateException("Invalid model pointer")
        }
        
        val pcmDataSize = maxOf(0, wavFile.length() - WAV_HEADER_SIZE)
        if (pcmDataSize <= 0) {
            throw IllegalStateException("Invalid WAV file")
        }
        
        val pcmData = mutableListOf<Short>()
        
        FileInputStream(wavFile).use { stream ->
            stream.skip(WAV_HEADER_SIZE.toLong())
            
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                
                val byteBuffer = ByteBuffer.wrap(buffer, 0, read)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                
                while (byteBuffer.remaining() >= 2) {
                    pcmData.add(byteBuffer.short)
                }
            }
        }
        
        val audioSamples = pcmData.toShortArray()
        onProgress(50)
        
        val jsonResult = transcribeAudio(modelPtr, audioSamples, SAMPLE_RATE)
        onProgress(100)
        
        parseWhisperJson(jsonResult)
    }
    
    suspend fun initModelFromPath(modelPath: String): Long = withContext(Dispatchers.IO) {
        val modelPtr = initModel(modelPath)
        if (modelPtr == INVALID_MODEL_PTR) {
            throw IllegalStateException("Failed to initialize model")
        }
        modelPtr
    }
    
    private fun parseWhisperJson(jsonResult: String): List<WhisperSegment> {
        try {
            val json = JSONObject(jsonResult)
            val segmentsArray = json.getJSONArray("segments")
            val segments = mutableListOf<WhisperSegment>()
            
            for (i in 0 until segmentsArray.length()) {
                val segmentObj = segmentsArray.getJSONObject(i)
                val text = segmentObj.getString("text").trim()
                val start = segmentObj.getDouble("start")
                val end = segmentObj.getDouble("end")
                
                if (text.isNotBlank()) {
                    segments.add(WhisperSegment(text, start, end))
                }
            }
            
            return segments
        } catch (e: Exception) {
            AppLogger.e(TAG_TRANSCRIPT, "Failed to parse JSON", e)
            return listOf(WhisperSegment(jsonResult.trim(), 0.0, 0.0))
        }
    }
}

