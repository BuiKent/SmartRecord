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
                    
                    // Using setOutputFile(File) for API 26+, setOutputFile(String) for API 24-25
                    // Note: MediaRecorder() constructor is deprecated in API 34+ but still the standard way
                    @Suppress("DEPRECATION")
                    mediaRecorder = MediaRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                        
                        // setOutputFile(File) requires API 26+, use String path for API 24-25
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            setOutputFile(outputFile)
                        } else {
                            @Suppress("DEPRECATION")
                            setOutputFile(outputFile.absolutePath)
                        }
                        
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
        
        try {
            val finalFile: File
            val fileSizeBefore: Long
            
            synchronized(this@AudioRecorderImpl) {
                if (!isRecording) {
                    AppLogger.w(TAG_AUDIO, "No recording in progress, cannot stop")
                    throw IllegalStateException("No recording in progress")
                }
                
                val file = outputFile ?: throw IllegalStateException("No recording file")
                finalFile = file
                fileSizeBefore = file.length()
                AppLogger.d(TAG_AUDIO, "File size before stop: %d bytes", fileSizeBefore)
                
                mediaRecorder?.apply {
                    try {
                        stop()
                        AppLogger.d(TAG_AUDIO, "MediaRecorder.stop() called successfully")
                    } catch (e: Exception) {
                        AppLogger.e(TAG_AUDIO, "Error calling stop() on MediaRecorder", e)
                        // Continue with release even if stop() fails
                    }
                    
                    try {
                        reset() // Reset before release to ensure proper cleanup
                        AppLogger.d(TAG_AUDIO, "MediaRecorder.reset() called successfully")
                    } catch (e: Exception) {
                        AppLogger.w(TAG_AUDIO, "Error calling reset() on MediaRecorder (may not be supported)", e.message)
                        // reset() might not be available on all API levels
                    }
                    
                    release()
                    AppLogger.d(TAG_AUDIO, "MediaRecorder released")
                }
                mediaRecorder = null
                outputFile = null
                isRecording = false
            }
            
            // Small delay to ensure file is flushed to disk (outside synchronized block)
            kotlinx.coroutines.delay(100)
            
            // Validate file after stop (outside synchronized block)
            val fileSizeAfter = finalFile.length()
            AppLogger.d(TAG_AUDIO, "File size after stop: %d bytes (before: %d)", fileSizeAfter, fileSizeBefore)
            
            if (!finalFile.exists()) {
                throw IllegalStateException("Recording file does not exist after stop: ${finalFile.absolutePath}")
            }
            
            if (fileSizeAfter == 0L) {
                AppLogger.w(TAG_AUDIO, "Warning: Recording file is empty (0 bytes)")
            }
            
            if (fileSizeAfter < fileSizeBefore) {
                AppLogger.w(TAG_AUDIO, "Warning: File size decreased after stop (before: %d, after: %d)", 
                    fileSizeBefore, fileSizeAfter)
            }
            
            val duration = System.currentTimeMillis() - startTime
            AppLogger.i(TAG_AUDIO, "Recording stopped successfully -> file: %s, size: %d bytes, stopTime: %dms", 
                finalFile.absolutePath, fileSizeAfter, duration)
            
            return@withContext finalFile
        } catch (e: Exception) {
            AppLogger.e(TAG_AUDIO, "Failed to stop recording", e)
            synchronized(this@AudioRecorderImpl) {
                mediaRecorder?.release()
                mediaRecorder = null
                outputFile = null
                isRecording = false
            }
            throw e
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
        // Thread-safe access to mediaRecorder
        synchronized(this@AudioRecorderImpl) {
            if (!isRecording || mediaRecorder == null) {
                return 0
            }
            
            return try {
                // maxAmplitude returns the maximum amplitude since last call, then resets
                // This is fine for waveform visualization as we call it frequently
                val amplitude = mediaRecorder!!.maxAmplitude
                
                // Log occasionally to avoid spam (only log non-zero values)
                if (amplitude > 0 && amplitude % 5000 == 0) {
                    AppLogger.d(TAG_AUDIO, "Current amplitude: %d", amplitude)
                }
                amplitude
            } catch (e: IllegalStateException) {
                // MediaRecorder might not be in recording state
                AppLogger.w(TAG_AUDIO, "Cannot get amplitude - MediaRecorder not recording: %s", e.message)
                0
            } catch (e: Exception) {
                AppLogger.e(TAG_AUDIO, "Failed to get amplitude", e)
                0
            }
        }
    }
    
    override suspend fun forceReset() = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        AppLogger.logRareCondition(TAG_AUDIO, "Force resetting AudioRecorder", 
            "isRecording=$isRecording, hasMediaRecorder=${mediaRecorder != null}, hasOutputFile=${outputFile != null}")
        
        synchronized(this@AudioRecorderImpl) {
            try {
                if (mediaRecorder != null) {
                    try {
                        // Try to stop if recording
                        if (isRecording) {
                            try {
                                mediaRecorder?.stop()
                                AppLogger.d(TAG_AUDIO, "MediaRecorder.stop() called during force reset")
                            } catch (e: Exception) {
                                AppLogger.w(TAG_AUDIO, "Error calling stop() during force reset (expected if not recording): %s", e.message)
                                // Continue - MediaRecorder might already be stopped or in invalid state
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG_AUDIO, "Error during force reset stop attempt: %s", e.message)
                    }
                    
                    try {
                        // Reset before release
                        mediaRecorder?.reset()
                        AppLogger.d(TAG_AUDIO, "MediaRecorder.reset() called during force reset")
                    } catch (e: Exception) {
                        AppLogger.w(TAG_AUDIO, "Error calling reset() during force reset: %s", e.message)
                        // Continue - reset() might not be available on all API levels
                    }
                    
                    try {
                        // Release MediaRecorder
                        mediaRecorder?.release()
                        AppLogger.d(TAG_AUDIO, "MediaRecorder released during force reset")
                    } catch (e: Exception) {
                        AppLogger.e(TAG_AUDIO, "Error releasing MediaRecorder during force reset", e)
                        // Continue - try to reset state anyway
                    }
                }
                
                // Reset all state
                mediaRecorder = null
                outputFile = null
                isRecording = false
                
                val duration = System.currentTimeMillis() - startTime
                AppLogger.i(TAG_AUDIO, "AudioRecorder force reset completed -> duration: %dms", duration)
            } catch (e: Exception) {
                AppLogger.e(TAG_AUDIO, "Failed to force reset AudioRecorder", e)
                // Force reset state even if cleanup fails
                mediaRecorder = null
                outputFile = null
                isRecording = false
                throw e
            }
        }
    }
}

