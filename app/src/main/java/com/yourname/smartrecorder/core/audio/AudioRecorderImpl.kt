package com.yourname.smartrecorder.core.audio

import android.media.MediaRecorder
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_AUDIO
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
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = 1 // Mono
    }
    
    override suspend fun startRecording(outputFile: File) {
        val startTime = System.currentTimeMillis()
        AppLogger.d(TAG_AUDIO, "Starting recording -> file: %s", outputFile.absolutePath)
        
        withContext(Dispatchers.IO) {
            synchronized(this@AudioRecorderImpl) {
                if (isRecording) {
                    AppLogger.w(TAG_AUDIO, "Recording already in progress, rejecting new request")
                    throw IllegalStateException("Recording already in progress")
                }
                
                try {
                    // Cleanup any existing recorder
                    mediaRecorder?.release()
                    
                    this@AudioRecorderImpl.outputFile = outputFile
                    outputFile.parentFile?.mkdirs()
                    AppLogger.d(TAG_AUDIO, "Output directory prepared: %s", outputFile.parentFile?.absolutePath)
                    
                    @Suppress("DEPRECATION")
                    mediaRecorder = MediaRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                        setOutputFile(outputFile.absolutePath)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                        setAudioSamplingRate(SAMPLE_RATE)
                        setAudioChannels(CHANNELS)
                        
                        AppLogger.d(TAG_AUDIO, "MediaRecorder configured -> format: THREE_GPP, encoder: AMR_NB, sampleRate: %d, channels: %d", 
                            SAMPLE_RATE, CHANNELS)
                        
                        prepare()
                        start()
                    }
                    
                    isRecording = true
                    val duration = System.currentTimeMillis() - startTime
                    AppLogger.i(TAG_AUDIO, "Recording started successfully -> file: %s, setupTime: %dms", 
                        outputFile.absolutePath, duration)
                } catch (e: Exception) {
                    AppLogger.e(TAG_AUDIO, "Failed to start recording -> file: %s", e, outputFile.absolutePath)
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
        val startTime = System.currentTimeMillis()
        AppLogger.d(TAG_AUDIO, "Stopping recording")
        
        synchronized(this@AudioRecorderImpl) {
            if (!isRecording) {
                AppLogger.w(TAG_AUDIO, "No recording in progress, cannot stop")
                throw IllegalStateException("No recording in progress")
            }
            
            try {
                val file = outputFile ?: throw IllegalStateException("No recording file")
                val fileSize = file.length()
                
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                outputFile = null
                isRecording = false
                
                val duration = System.currentTimeMillis() - startTime
                AppLogger.i(TAG_AUDIO, "Recording stopped successfully -> file: %s, size: %d bytes, stopTime: %dms", 
                    file.absolutePath, fileSize, duration)
                
                return@withContext file
            } catch (e: Exception) {
                AppLogger.e(TAG_AUDIO, "Failed to stop recording", e)
                mediaRecorder?.release()
                mediaRecorder = null
                outputFile = null
                isRecording = false
                throw e
            }
        }
    }
    
    override suspend fun pause() {
        AppLogger.d(TAG_AUDIO, "Pausing recording")
        withContext(Dispatchers.IO) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    mediaRecorder?.pause()
                    AppLogger.d(TAG_AUDIO, "Recording paused")
                } else {
                    AppLogger.w(TAG_AUDIO, "Pause not supported on this Android version")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_AUDIO, "Failed to pause recording", e)
            }
        }
    }
    
    override suspend fun resume() {
        AppLogger.d(TAG_AUDIO, "Resuming recording")
        withContext(Dispatchers.IO) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    mediaRecorder?.resume()
                    AppLogger.d(TAG_AUDIO, "Recording resumed")
                } else {
                    AppLogger.w(TAG_AUDIO, "Resume not supported on this Android version")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_AUDIO, "Failed to resume recording", e)
            }
        }
    }
    
    override fun getAmplitude(): Int {
        return try {
            val amplitude = mediaRecorder?.maxAmplitude ?: 0
            // Log occasionally to avoid spam
            if (amplitude > 0 && amplitude % 1000 == 0) {
                AppLogger.d(TAG_AUDIO, "Current amplitude: %d", amplitude)
            }
            amplitude
        } catch (e: Exception) {
            AppLogger.e(TAG_AUDIO, "Failed to get amplitude", e)
            0
        }
    }
}

