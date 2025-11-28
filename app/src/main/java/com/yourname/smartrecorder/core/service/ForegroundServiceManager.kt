package com.yourname.smartrecorder.core.service

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
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
        // Check notification permission before starting service
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            AppLogger.logRareCondition(TAG_SERVICE, 
                "Cannot start recording service - notifications disabled", 
                "recordingId=$recordingId")
            // Show warning toast
            Toast.makeText(
                context,
                "Notifications are disabled. Recording status won't be visible in background. Please enable notifications in Settings.",
                Toast.LENGTH_LONG
            ).show()
            // Still start service - it will work but notification will be suppressed
            // This allows recording to continue even if notifications are disabled
        }
        
        val intent = RecordingForegroundService.createIntent(context).apply {
            putExtra("recordingId", recordingId)
            putExtra("fileName", fileName)
        }
        ContextCompat.startForegroundService(context, intent)
        AppLogger.logCritical(TAG_SERVICE, "Recording foreground service started", 
            "recordingId=$recordingId, fileName=$fileName, notificationsEnabled=${NotificationManagerCompat.from(context).areNotificationsEnabled()}")
    }
    
    fun stopRecordingService() {
        val intent = RecordingForegroundService.createIntent(context)
        context.stopService(intent)
        AppLogger.logCritical(TAG_SERVICE, "Recording foreground service stopped")
    }
    
    fun updateRecordingNotification(durationMs: Long, isPaused: Boolean = false) {
        // ⚠️ CRITICAL FIX: Dùng BroadcastReceiver thay vì startService() để tránh gọi onStartCommand liên tục
        // Service đã chạy rồi (foreground), chỉ cần update notification qua broadcast
        val intent = Intent(RecordingForegroundService.BROADCAST_UPDATE_NOTIFICATION).apply {
            setPackage(context.packageName)
            putExtra("durationMs", durationMs)
            putExtra("isPaused", isPaused)
        }
        context.sendBroadcast(intent)
    }
    
    fun startPlaybackService(recordingId: String, title: String, duration: Long) {
        // Check notification permission before starting service
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            AppLogger.logRareCondition(TAG_SERVICE, 
                "Cannot start playback service - notifications disabled", 
                "recordingId=$recordingId, title=$title")
            // Show warning toast
            Toast.makeText(
                context,
                "Notifications are disabled. Playback status won't be visible in background. Please enable notifications in Settings.",
                Toast.LENGTH_LONG
            ).show()
            // Still start service - it will work but notification will be suppressed
        }
        
        val intent = PlaybackForegroundService.createIntent(context).apply {
            putExtra("recordingId", recordingId)  // ← Thêm recordingId
            putExtra("title", title)
            putExtra("duration", duration)
        }
        ContextCompat.startForegroundService(context, intent)
        AppLogger.logCritical(TAG_SERVICE, "Playback foreground service started", 
            "recordingId=$recordingId, title=$title, duration=${duration}ms, notificationsEnabled=${NotificationManagerCompat.from(context).areNotificationsEnabled()}")
    }
    
    fun stopPlaybackService() {
        val intent = PlaybackForegroundService.createIntent(context)
        context.stopService(intent)
        AppLogger.logCritical(TAG_SERVICE, "Playback foreground service stopped")
    }
    
    fun updatePlaybackNotification(recordingId: String, position: Long, duration: Long, isPaused: Boolean = false) {
        // ⚠️ CRITICAL FIX: Dùng BroadcastReceiver thay vì startService() để tránh gọi onStartCommand liên tục
        // Service đã chạy rồi (foreground), chỉ cần update notification qua broadcast
        val intent = Intent(PlaybackForegroundService.BROADCAST_UPDATE_NOTIFICATION).apply {
            setPackage(context.packageName)
            putExtra("position", position)
            putExtra("duration", duration)
            putExtra("isPaused", isPaused)
        }
        context.sendBroadcast(intent)
    }
}

