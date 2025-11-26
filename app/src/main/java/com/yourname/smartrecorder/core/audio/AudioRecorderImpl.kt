package com.yourname.smartrecorder.core.audio

import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.Volatile

@Singleton
class AudioRecorderImpl @Inject constructor() : AudioRecorder {
    
    @Volatile
    private var mediaRecorder: MediaRecorder? = null
    
    @Volatile
    private var outputFile: File? = null
    
    @Volatile
    private var isRecording: Boolean = false
    
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = 1 // Mono
    }
    
    override suspend fun startRecording(outputFile: File) {
        withContext(Dispatchers.IO) {
            synchronized(this@AudioRecorderImpl) {
                if (isRecording) {
                    throw IllegalStateException("Recording already in progress")
                }
                
                try {
                    // Cleanup any existing recorder
                    mediaRecorder?.release()
                    
                    this@AudioRecorderImpl.outputFile = outputFile
                    outputFile.parentFile?.mkdirs()
                    
                    @Suppress("DEPRECATION")
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
                    
                    isRecording = true
                    Log.d(TAG, "Recording started: ${outputFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start recording", e)
                    mediaRecorder?.release()
                    mediaRecorder = null
                    this@AudioRecorderImpl.outputFile = null
                    isRecording = false
                    throw e
                }
            }
        }
    }
    
    override suspend fun stopRecording(): File = withContext(Dispatchers.IO) {
        synchronized(this@AudioRecorderImpl) {
            if (!isRecording) {
                throw IllegalStateException("No recording in progress")
            }
            
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                
                val file = outputFile ?: throw IllegalStateException("No recording file")
                outputFile = null
                isRecording = false
                
                Log.d(TAG, "Recording stopped: ${file.absolutePath}")
                return@withContext file
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording", e)
                mediaRecorder?.release()
                mediaRecorder = null
                outputFile = null
                isRecording = false
                throw e
            }
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

