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
}

