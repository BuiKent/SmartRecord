package com.yourname.smartrecorder.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yourname.smartrecorder.MainActivity
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_SERVICE
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service to keep audio playback active when app is in background.
 * Prevents Android from stopping playback when user switches apps.
 */
@AndroidEntryPoint
class PlaybackForegroundService : Service() {
    
    private val binder = LocalBinder()
    private var notificationManager: NotificationManager? = null
    private var isPlaying = false
    private var currentTitle: String = ""
    private var currentPosition: Long = 0L
    private var totalDuration: Long = 0L
    
    companion object {
        private const val CHANNEL_ID = "playback_channel"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_PAUSE = "com.yourname.smartrecorder.PAUSE_PLAYBACK"
        private const val ACTION_STOP = "com.yourname.smartrecorder.STOP_PLAYBACK"
        
        fun createIntent(context: android.content.Context): Intent {
            return Intent(context, PlaybackForegroundService::class.java)
        }
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): PlaybackForegroundService = this@PlaybackForegroundService
    }
    
    override fun onCreate() {
        super.onCreate()
        AppLogger.logService(TAG_SERVICE, "PlaybackForegroundService", "onCreate")
        createNotificationChannel()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.logService(TAG_SERVICE, "PlaybackForegroundService", "onStartCommand", 
            "action=${intent?.action}, flags=$flags, startId=$startId")
        
        when (intent?.action) {
            ACTION_PAUSE -> {
                AppLogger.logCritical(TAG_SERVICE, "Pause playback requested from notification")
                // Pause will be handled by ViewModel
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                AppLogger.logCritical(TAG_SERVICE, "Stop playback requested from notification")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Handle start playback or update notification
                val title = intent?.getStringExtra("title")
                val duration = intent?.getLongExtra("duration", 0L) ?: 0L
                val position = intent?.getLongExtra("position", 0L) ?: 0L
                val isPaused = intent?.getBooleanExtra("isPaused", false) ?: false
                
                if (title != null && duration > 0 && !isPlaying) {
                    startPlayback(title, duration)
                } else if (isPlaying) {
                    // Update notification
                    updateNotification(position, duration, isPaused)
                } else {
                    startForeground(NOTIFICATION_ID, createNotification(0, 0, false))
                    AppLogger.logService(TAG_SERVICE, "PlaybackForegroundService", "Started foreground")
                }
                return START_STICKY
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder {
        AppLogger.logService(TAG_SERVICE, "PlaybackForegroundService", "onBind")
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        AppLogger.logService(TAG_SERVICE, "PlaybackForegroundService", "onDestroy")
        if (isPlaying) {
            AppLogger.logRareCondition(TAG_SERVICE, "Service destroyed while playing", 
                "title=$currentTitle, position=$currentPosition")
        }
    }
    
    fun startPlayback(title: String, duration: Long) {
        isPlaying = true
        currentTitle = title
        totalDuration = duration
        currentPosition = 0L
        
        AppLogger.logCritical(TAG_SERVICE, "Playback started in foreground service", 
            "title=$title, duration=${duration}ms")
        
        startForeground(NOTIFICATION_ID, createNotification(0, duration, true))
    }
    
    fun stopPlayback() {
        if (!isPlaying) {
            AppLogger.logRareCondition(TAG_SERVICE, "Attempted to stop playback when not playing")
            return
        }
        
        AppLogger.logCritical(TAG_SERVICE, "Playback stopped in foreground service", 
            "title=$currentTitle, finalPosition=$currentPosition")
        
        isPlaying = false
        currentTitle = ""
        currentPosition = 0L
        totalDuration = 0L
    }
    
    fun updateNotification(position: Long, duration: Long, isPaused: Boolean = false) {
        if (isPlaying) {
            currentPosition = position
            totalDuration = duration
            val notification = createNotification(position, duration, !isPaused)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing audio playback notification"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(position: Long, duration: Long, isPlaying: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val pauseIntent = Intent(this, PlaybackForegroundService::class.java).apply {
            action = ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 0, pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = Intent(this, PlaybackForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val positionText = formatDuration(position)
        val durationText = formatDuration(duration)
        val statusText = if (isPlaying) "Playing" else "Paused"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle.ifEmpty { "Audio Playback" })
            .setContentText("$statusText - $positionText / $durationText")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                pausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }
    
    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 60000) % 60
        val hours = ms / 3600000
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}

