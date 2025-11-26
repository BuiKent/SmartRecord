package com.yourname.smartrecorder.core.audio

import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorderImpl @Inject constructor() : AudioRecorder {
    
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = 1 // Mono
    }
    
    override suspend fun startRecording(outputFile: File) {
        withContext(Dispatchers.IO) {
        try {
            this@AudioRecorderImpl.outputFile = outputFile
            outputFile.parentFile?.mkdirs()
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(outputFile.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioChannels(CHANNELS)
                
                prepare()
                start()
            }
            
            Log.d(TAG, "Recording started: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            throw e
        }
        }
    }
    
    override suspend fun stopRecording(): File = withContext(Dispatchers.IO) {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            val file = outputFile ?: throw IllegalStateException("No recording file")
            outputFile = null
            
            Log.d(TAG, "Recording stopped: ${file.absolutePath}")
            return@withContext file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            throw e
        }
    }
    
    override suspend fun pause() {
        withContext(Dispatchers.IO) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    mediaRecorder?.pause()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause recording", e)
            }
        }
    }
    
    override suspend fun resume() {
        withContext(Dispatchers.IO) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    mediaRecorder?.resume()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume recording", e)
            }
        }
    }
    
    override fun getAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }
}

