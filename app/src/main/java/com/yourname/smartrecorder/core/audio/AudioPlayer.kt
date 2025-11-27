package com.yourname.smartrecorder.core.audio

import android.media.MediaPlayer
import java.io.File

interface AudioPlayer {
    fun play(file: File, onCompletion: () -> Unit = {})
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Int)
    fun getCurrentPosition(): Int
    fun getDuration(): Int
    fun isPlaying(): Boolean
    fun release()
    fun setLooping(looping: Boolean)
    fun isLooping(): Boolean
    /**
     * Force reset playback state without stopping gracefully.
     * Used when ViewModel is cleared or playback needs to be aborted.
     * This will release MediaPlayer and reset all state.
     */
    suspend fun forceReset()
}

