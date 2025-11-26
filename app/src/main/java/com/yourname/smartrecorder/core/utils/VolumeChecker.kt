package com.yourname.smartrecorder.core.utils

import android.content.Context
import android.media.AudioManager

object VolumeChecker {
    /**
     * Check if the device volume is below 15%
     * @return true if volume is below 15%, false otherwise
     */
    fun isVolumeLow(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        
        if (maxVolume == 0) return false // Avoid division by zero
        
        val volumePercent = (currentVolume.toFloat() / maxVolume.toFloat()) * 100f
        return volumePercent < 15f
    }
    
    /**
     * Get current volume percentage
     */
    fun getVolumePercent(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        
        if (maxVolume == 0) return 0
        
        return ((currentVolume.toFloat() / maxVolume.toFloat()) * 100f).toInt()
    }
}

