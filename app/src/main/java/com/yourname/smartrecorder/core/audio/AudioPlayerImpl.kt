package com.yourname.smartrecorder.core.audio

import android.media.MediaPlayer
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayerImpl @Inject constructor() : AudioPlayer {
    
    private var mediaPlayer: MediaPlayer? = null
    
    companion object {
        private const val TAG = "AudioPlayer"
    }
    
    override fun play(file: File, onCompletion: () -> Unit) {
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
        }
    }
    
    override fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause audio", e)
        }
    }
    
    override fun resume() {
        try {
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume audio", e)
        }
    }
    
    override fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop audio", e)
        }
    }
    
    override fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek audio", e)
        }
    }
    
    override fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    override fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    override fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    override fun release() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release audio player", e)
        }
    }
}

