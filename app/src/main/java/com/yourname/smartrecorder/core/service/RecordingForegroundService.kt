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
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_LIFECYCLE
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service to keep recording active when app is in background.
 * Prevents Android from killing the recording process.
 */
@AndroidEntryPoint
class RecordingForegroundService : Service() {
    
    @Inject
    lateinit var recordingStateManager: RecordingStateManager
    
    private val binder = LocalBinder()
    private var notificationManager: NotificationManager? = null
    private var isRecording = false
    private var recordingStartTime: Long = 0L
    private var lastBackgroundTime: Long = 0L
    private val BACKGROUND_WARNING_THRESHOLD = 30 * 60 * 1000L // 30 minutes
    
    companion object {
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.yourname.smartrecorder.STOP_RECORDING"
        
        fun createIntent(context: android.content.Context): Intent {
            return Intent(context, RecordingForegroundService::class.java)
        }
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): RecordingForegroundService = this@RecordingForegroundService
    }
    
    override fun onCreate() {
        super.onCreate()
        AppLogger.logService(TAG_SERVICE, "RecordingForegroundService", "onCreate")
        createNotificationChannel()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.logService(TAG_SERVICE, "RecordingForegroundService", "onStartCommand", 
            "action=${intent?.action}, flags=$flags, startId=$startId")
        
        when (intent?.action) {
            ACTION_STOP -> {
                AppLogger.logCritical(TAG_SERVICE, "Stop recording requested from notification")
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Handle start recording or update notification
                val recordingId = intent?.getStringExtra("recordingId")
                val fileName = intent?.getStringExtra("fileName")
                val durationMs = intent?.getLongExtra("durationMs", 0L) ?: 0L
                val isPaused = intent?.getBooleanExtra("isPaused", false) ?: false
                
                if (recordingId != null && fileName != null && !isRecording) {
                    startRecording(recordingId, fileName)
                } else if (isRecording) {
                    // Update notification
                    updateNotification(durationMs, isPaused)
                } else {
                    startForeground(NOTIFICATION_ID, createNotification(0, false))
                    AppLogger.logService(TAG_SERVICE, "RecordingForegroundService", "Started foreground")
                }
                return START_STICKY // Restart if killed
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder {
        AppLogger.logService(TAG_SERVICE, "RecordingForegroundService", "onBind")
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        AppLogger.logService(TAG_SERVICE, "RecordingForegroundService", "onDestroy")
        if (isRecording) {
            AppLogger.logRareCondition(TAG_SERVICE, "Service destroyed while recording", 
                "recordingDuration=${System.currentTimeMillis() - recordingStartTime}ms")
        }
    }
    
    fun startRecording(recordingId: String, fileName: String) {
        if (isRecording) {
            AppLogger.logRareCondition(TAG_SERVICE, "Attempted to start recording while already recording", 
                "recordingId=$recordingId")
            return
        }
        
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        lastBackgroundTime = 0L
        
        AppLogger.logCritical(TAG_SERVICE, "Recording started in foreground service", 
            "recordingId=$recordingId, fileName=$fileName")
        
        startForeground(NOTIFICATION_ID, createNotification(0, true))
        recordingStateManager.setRecordingActive(recordingId, fileName, recordingStartTime)
    }
    
    fun stopRecording() {
        if (!isRecording) {
            AppLogger.logRareCondition(TAG_SERVICE, "Attempted to stop recording when not recording")
            return
        }
        
        val duration = System.currentTimeMillis() - recordingStartTime
        AppLogger.logCritical(TAG_SERVICE, "Recording stopped in foreground service", 
            "duration=${duration}ms")
        
        isRecording = false
        recordingStartTime = 0L
        lastBackgroundTime = 0L
        recordingStateManager.clearRecordingState()
    }
    
    fun updateNotification(durationMs: Long, isPaused: Boolean = false) {
        if (isRecording) {
            val notification = createNotification(durationMs, !isPaused)
            notificationManager?.notify(NOTIFICATION_ID, notification)
            
            // Check if app has been in background too long
            if (lastBackgroundTime > 0) {
                val backgroundDuration = System.currentTimeMillis() - lastBackgroundTime
                if (backgroundDuration > BACKGROUND_WARNING_THRESHOLD) {
                    AppLogger.logRareCondition(TAG_SERVICE, 
                        "Recording in background for extended period", 
                        "duration=${backgroundDuration}ms, threshold=${BACKGROUND_WARNING_THRESHOLD}ms")
                    // Could show a warning notification here
                }
            }
        }
    }
    
    fun onAppBackgrounded() {
        if (isRecording) {
            lastBackgroundTime = System.currentTimeMillis()
            AppLogger.logLifecycle(TAG_LIFECYCLE, "RecordingForegroundService", 
                "App backgrounded while recording", 
                "recordingDuration=${System.currentTimeMillis() - recordingStartTime}ms")
        }
    }
    
    fun onAppForegrounded() {
        if (isRecording && lastBackgroundTime > 0) {
            val backgroundDuration = System.currentTimeMillis() - lastBackgroundTime
            AppLogger.logLifecycle(TAG_LIFECYCLE, "RecordingForegroundService", 
                "App foregrounded while recording", 
                "backgroundDuration=${backgroundDuration}ms")
            lastBackgroundTime = 0L
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_LOW // Low priority to avoid interruption
            ).apply {
                description = "Ongoing recording notification"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(durationMs: Long, isRecording: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val durationText = formatDuration(durationMs)
        val statusText = if (isRecording) "Recording" else "Paused"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$statusText - $durationText")
            .setContentText("Tap to return to app")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(isRecording)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
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

