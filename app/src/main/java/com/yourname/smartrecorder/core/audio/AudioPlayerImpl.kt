package com.yourname.smartrecorder.core.audio

import android.media.MediaPlayer
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_AUDIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.Volatile

@Singleton
class AudioPlayerImpl @Inject constructor() : AudioPlayer {
    
    @Volatile
    private var mediaPlayer: MediaPlayer? = null
    
    @Volatile
    private var currentFile: File? = null
    
    @Volatile
    private var isLooping: Boolean = false
    
    override fun play(file: File, onCompletion: () -> Unit) {
        val startTime = System.currentTimeMillis()
        AppLogger.logMain(TAG_AUDIO, "Playing audio -> file: %s, size: %d bytes, exists: %b", 
            file.absolutePath, file.length(), file.exists())
        
        synchronized(this) {
            try {
                // Check for concurrent playback
                if (mediaPlayer != null && mediaPlayer?.isPlaying == true) {
                    AppLogger.logRareCondition(TAG_AUDIO, 
                        "Stopping existing playback before starting new one", 
                        "currentFile=${currentFile?.absolutePath}")
                    release()
                }
                
                currentFile = file
                
                AppLogger.logBackground(TAG_AUDIO, "Creating MediaPlayer instance")
                mediaPlayer = MediaPlayer().apply {
                    AppLogger.logBackground(TAG_AUDIO, "Setting data source -> path: %s", file.absolutePath)
                    setDataSource(file.absolutePath)
                    
                    AppLogger.logBackground(TAG_AUDIO, "Preparing MediaPlayer")
                    prepare()
                    
                    val duration = duration
                    AppLogger.logMain(TAG_AUDIO, "MediaPlayer prepared -> duration: %d ms (%.2f minutes)", 
                        duration, duration / 60000.0)
                    
                    isLooping = this@AudioPlayerImpl.isLooping
                    setLooping(isLooping)
                    AppLogger.logBackground(TAG_AUDIO, "Looping set to: %b", isLooping)
                    
                    setOnCompletionListener {
                        AppLogger.logMain(TAG_AUDIO, "Playback completed -> file: %s, looping: %b", 
                            file.absolutePath, isLooping)
                        if (!isLooping) {
                            onCompletion()
                        }
                    }
                    
                    AppLogger.logBackground(TAG_AUDIO, "Starting MediaPlayer")
                    start()
                }
                
                val setupTime = System.currentTimeMillis() - startTime
                AppLogger.logMain(TAG_AUDIO, "Audio playback started successfully -> file: %s, setupTime: %dms", 
                    file.absolutePath, setupTime)
            } catch (e: Exception) {
                AppLogger.e(TAG_AUDIO, "Failed to play audio -> file: %s", e, file.absolutePath)
                release()
                throw e
            }
        }
    }
    
    override fun pause() {
        AppLogger.logMain(TAG_AUDIO, "Pausing audio playback")
        synchronized(this) {
            try {
                if (mediaPlayer == null) {
                    AppLogger.logRareCondition(TAG_AUDIO, "Pause called but MediaPlayer is null")
                    return
                }
                
                val position = mediaPlayer?.currentPosition ?: 0
                val isCurrentlyPlaying = mediaPlayer?.isPlaying ?: false
                
                if (!isCurrentlyPlaying) {
                    AppLogger.logRareCondition(TAG_AUDIO, "Pause called but MediaPlayer is not playing", 
                        "position=$position")
                    return
                }
                
                mediaPlayer?.pause()
                AppLogger.logMain(TAG_AUDIO, "Audio paused successfully -> position: %d ms (%.2f%%)", 
                    position, if (mediaPlayer?.duration ?: 0 > 0) {
                        (position.toFloat() / mediaPlayer!!.duration) * 100
                    } else 0f)
            } catch (e: Exception) {
                AppLogger.e(TAG_AUDIO, "Failed to pause audio", e)
            }
        }
    }
    
    override fun resume() {
        AppLogger.logMain(TAG_AUDIO, "Resuming audio playback")
        synchronized(this) {
            try {
                if (mediaPlayer == null) {
                    AppLogger.logRareCondition(TAG_AUDIO, "Resume called but MediaPlayer is null")
                    return
                }
                
                val position = mediaPlayer?.currentPosition ?: 0
                val isCurrentlyPlaying = mediaPlayer?.isPlaying ?: false
                
                if (isCurrentlyPlaying) {
                    AppLogger.logRareCondition(TAG_AUDIO, "Resume called but MediaPlayer is already playing", 
                        "position=$position")
                    return
                }
                
                mediaPlayer?.start()
                AppLogger.logMain(TAG_AUDIO, "Audio resumed successfully -> position: %d ms", position)
            } catch (e: Exception) {
                AppLogger.e(TAG_AUDIO, "Failed to resume audio", e)
            }
        }
    }
    
    override fun stop() {
        AppLogger.d(TAG_AUDIO, "Stopping audio playback")
        synchronized(this) {
            try {
                val file = currentFile?.absolutePath
                mediaPlayer?.stop()
                // Release after stop to free resources
                mediaPlayer?.release()
                mediaPlayer = null
                currentFile = null
                AppLogger.d(TAG_AUDIO, "Audio stopped and released -> file: %s", file)
            } catch (e: Exception) {
                AppLogger.e(TAG_AUDIO, "Failed to stop audio", e)
                // Ensure cleanup even on error
                try {
                    mediaPlayer?.release()
                } catch (e2: Exception) {
                    // Ignore release errors
                }
                mediaPlayer = null
                currentFile = null
            }
        }
    }
    
    override fun seekTo(positionMs: Int) {
        AppLogger.d(TAG_AUDIO, "Seeking to position: %d ms", positionMs)
        synchronized(this) {
            try {
                mediaPlayer?.seekTo(positionMs)
                AppLogger.d(TAG_AUDIO, "Seek completed -> position: %d ms", positionMs)
            } catch (e: Exception) {
                AppLogger.e(TAG_AUDIO, "Failed to seek audio -> position: %d ms", e, positionMs)
            }
        }
    }
    
    override fun getCurrentPosition(): Int {
        return try {
            synchronized(this) {
                mediaPlayer?.currentPosition ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }
    
    override fun getDuration(): Int {
        return try {
            synchronized(this) {
                mediaPlayer?.duration ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }
    
    override fun isPlaying(): Boolean {
        return try {
            synchronized(this) {
                mediaPlayer?.isPlaying ?: false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    override fun setLooping(looping: Boolean) {
        AppLogger.d(TAG_AUDIO, "Setting looping: %b", looping)
        synchronized(this) {
            isLooping = looping
            mediaPlayer?.isLooping = looping
        }
    }
    
    override fun isLooping(): Boolean {
        return synchronized(this) {
            isLooping
        }
    }
    
    override fun release() {
        AppLogger.d(TAG_AUDIO, "Releasing audio player")
        synchronized(this) {
            try {
                val file = currentFile?.absolutePath
                mediaPlayer?.release()
                mediaPlayer = null
                currentFile = null
                isLooping = false
                AppLogger.d(TAG_AUDIO, "Audio player released -> file: %s", file)
            } catch (e: Exception) {
                AppLogger.e(TAG_AUDIO, "Failed to release audio player", e)
                mediaPlayer = null
                currentFile = null
                isLooping = false
            }
        }
    }
    
    override suspend fun forceReset() = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        AppLogger.logRareCondition(TAG_AUDIO, "Force resetting AudioPlayer", 
            "isPlaying=${isPlaying()}, hasMediaPlayer=${mediaPlayer != null}, hasCurrentFile=${currentFile != null}")
        
        synchronized(this@AudioPlayerImpl) {
            try {
                if (mediaPlayer != null) {
                    try {
                        // Try to stop if playing
                        if (isPlaying()) {
                            try {
                                mediaPlayer?.stop()
                                AppLogger.d(TAG_AUDIO, "MediaPlayer.stop() called during force reset")
                            } catch (e: Exception) {
                                AppLogger.w(TAG_AUDIO, "Error calling stop() during force reset (expected if not playing): %s", e.message)
                                // Continue - MediaPlayer might already be stopped or in invalid state
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG_AUDIO, "Error during force reset stop attempt: %s", e.message)
                    }
                    
                    try {
                        // Release MediaPlayer
                        mediaPlayer?.release()
                        AppLogger.d(TAG_AUDIO, "MediaPlayer released during force reset")
                    } catch (e: Exception) {
                        AppLogger.e(TAG_AUDIO, "Error releasing MediaPlayer during force reset", e)
                        // Continue - try to reset state anyway
                    }
                }
                
                // Reset all state
                mediaPlayer = null
                currentFile = null
                isLooping = false
                
                val duration = System.currentTimeMillis() - startTime
                AppLogger.i(TAG_AUDIO, "AudioPlayer force reset completed -> duration: %dms", duration)
            } catch (e: Exception) {
                AppLogger.e(TAG_AUDIO, "Failed to force reset AudioPlayer", e)
                // Force reset state even if cleanup fails
                mediaPlayer = null
                currentFile = null
                isLooping = false
                throw e
            }
        }
    }
}

