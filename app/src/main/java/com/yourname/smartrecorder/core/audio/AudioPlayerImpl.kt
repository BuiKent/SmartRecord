package com.yourname.smartrecorder.core.audio

import android.media.MediaPlayer
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.Volatile

@Singleton
class AudioPlayerImpl @Inject constructor() : AudioPlayer {
    
    @Volatile
    private var mediaPlayer: MediaPlayer? = null
    
    companion object {
        private const val TAG = "AudioPlayer"
    }
    
    override fun play(file: File, onCompletion: () -> Unit) {
        synchronized(this) {
            try {
                release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        onCompletion()
                    }
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play audio", e)
                release()
            }
        }
    }
    
    override fun pause() {
        synchronized(this) {
            try {
                mediaPlayer?.pause()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause audio", e)
            }
        }
    }
    
    override fun resume() {
        synchronized(this) {
            try {
                mediaPlayer?.start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume audio", e)
            }
        }
    }
    
    override fun stop() {
        synchronized(this) {
            try {
                mediaPlayer?.stop()
                // Release after stop to free resources
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop audio", e)
                // Ensure cleanup even on error
                try {
                    mediaPlayer?.release()
                } catch (e2: Exception) {
                    // Ignore release errors
                }
                mediaPlayer = null
            }
        }
    }
    
    override fun seekTo(positionMs: Int) {
        synchronized(this) {
            try {
                mediaPlayer?.seekTo(positionMs)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to seek audio", e)
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
    
    override fun release() {
        synchronized(this) {
            try {
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release audio player", e)
                mediaPlayer = null
            }
        }
    }
}

