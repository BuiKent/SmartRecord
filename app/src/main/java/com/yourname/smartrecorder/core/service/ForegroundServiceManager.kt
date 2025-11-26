package com.yourname.smartrecorder.core.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_SERVICE
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages foreground services for recording and playback.
 * Provides easy access to start/stop services from ViewModels.
 */
@Singleton
class ForegroundServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    fun startRecordingService(recordingId: String, fileName: String) {
        val intent = RecordingForegroundService.createIntent(context).apply {
            putExtra("recordingId", recordingId)
            putExtra("fileName", fileName)
        }
        ContextCompat.startForegroundService(context, intent)
        AppLogger.logCritical(TAG_SERVICE, "Recording foreground service started", 
            "recordingId=$recordingId, fileName=$fileName")
    }
    
    fun stopRecordingService() {
        val intent = RecordingForegroundService.createIntent(context)
        context.stopService(intent)
        AppLogger.logCritical(TAG_SERVICE, "Recording foreground service stopped")
    }
    
    fun updateRecordingNotification(durationMs: Long, isPaused: Boolean = false) {
        val intent = RecordingForegroundService.createIntent(context).apply {
            putExtra("durationMs", durationMs)
            putExtra("isPaused", isPaused)
        }
        ContextCompat.startForegroundService(context, intent)
    }
    
    fun startPlaybackService(title: String, duration: Long) {
        val intent = PlaybackForegroundService.createIntent(context).apply {
            putExtra("title", title)
            putExtra("duration", duration)
        }
        ContextCompat.startForegroundService(context, intent)
        AppLogger.logCritical(TAG_SERVICE, "Playback foreground service started", 
            "title=$title, duration=${duration}ms")
    }
    
    fun stopPlaybackService() {
        val intent = PlaybackForegroundService.createIntent(context)
        context.stopService(intent)
        AppLogger.logCritical(TAG_SERVICE, "Playback foreground service stopped")
    }
    
    fun updatePlaybackNotification(position: Long, duration: Long, isPaused: Boolean = false) {
        val intent = PlaybackForegroundService.createIntent(context).apply {
            putExtra("position", position)
            putExtra("duration", duration)
            putExtra("isPaused", isPaused)
        }
        ContextCompat.startForegroundService(context, intent)
    }
}

