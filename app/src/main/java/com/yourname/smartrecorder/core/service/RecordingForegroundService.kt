package com.yourname.smartrecorder.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yourname.smartrecorder.MainActivity
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_SERVICE
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_LIFECYCLE
import com.yourname.smartrecorder.core.notification.NotificationDeepLinkHandler
import com.yourname.smartrecorder.ui.navigation.AppRoutes
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
    
    @Inject
    lateinit var notificationDeepLinkHandler: NotificationDeepLinkHandler
    
    private val binder = LocalBinder()
    private var notificationManager: NotificationManager? = null
    private var isRecording = false
    private var isPaused = false
    private var recordingStartTime: Long = 0L
    private var lastBackgroundTime: Long = 0L
    private val BACKGROUND_WARNING_THRESHOLD = 30 * 60 * 1000L // 30 minutes
    
    companion object {
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_PAUSE = "com.yourname.smartrecorder.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.yourname.smartrecorder.RESUME_RECORDING"
        const val ACTION_STOP = "com.yourname.smartrecorder.STOP_RECORDING"
        
        // Broadcast actions
        const val BROADCAST_PAUSE = "com.yourname.smartrecorder.BROADCAST_PAUSE"
        const val BROADCAST_RESUME = "com.yourname.smartrecorder.BROADCAST_RESUME"
        const val BROADCAST_STOP = "com.yourname.smartrecorder.BROADCAST_STOP"
        const val BROADCAST_UPDATE_NOTIFICATION = "com.yourname.smartrecorder.BROADCAST_UPDATE_NOTIFICATION"
        
        fun createIntent(context: android.content.Context): Intent {
            return Intent(context, RecordingForegroundService::class.java)
        }
    }
    
    private val notificationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BROADCAST_UPDATE_NOTIFICATION) {
                val durationMs = intent.getLongExtra("durationMs", 0L)
                val isPaused = intent.getBooleanExtra("isPaused", false)
                updateNotification(durationMs, isPaused)
            }
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
        
        // Register BroadcastReceiver for notification updates
        val filter = IntentFilter(BROADCAST_UPDATE_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(notificationUpdateReceiver, filter)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.logService(TAG_SERVICE, "RecordingForegroundService", "onStartCommand", 
            "action=${intent?.action}, flags=$flags, startId=$startId")
        
        when (intent?.action) {
            ACTION_PAUSE -> {
                AppLogger.logCritical(TAG_SERVICE, "Pause recording requested from notification")
                pauseRecording()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                AppLogger.logCritical(TAG_SERVICE, "Resume recording requested from notification")
                resumeRecording()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                AppLogger.logCritical(TAG_SERVICE, "Stop recording requested from notification")
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Handle start recording only (notification updates via BroadcastReceiver)
                val recordingId = intent?.getStringExtra("recordingId")
                val fileName = intent?.getStringExtra("fileName")
                
                if (recordingId != null && fileName != null && !isRecording) {
                    startRecording(recordingId, fileName)
                    return START_STICKY // Restart if killed
                } else {
                    // Ignore update notification calls via Intent (use BroadcastReceiver instead)
                    AppLogger.d(TAG_SERVICE, "Ignoring update notification via Intent - use BroadcastReceiver")
                    return START_NOT_STICKY
                }
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
        // Unregister BroadcastReceiver
        try {
            unregisterReceiver(notificationUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
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
    
    fun pauseRecording() {
        if (!isRecording || isPaused) {
            AppLogger.logRareCondition(TAG_SERVICE, "Attempted to pause when not recording or already paused")
            return
        }
        
        isPaused = true
        AppLogger.logCritical(TAG_SERVICE, "Recording paused in foreground service")
        
        // Send broadcast to ViewModel
        sendBroadcast(BROADCAST_PAUSE)
        updateNotification(System.currentTimeMillis() - recordingStartTime, true)
    }
    
    fun resumeRecording() {
        if (!isRecording || !isPaused) {
            AppLogger.logRareCondition(TAG_SERVICE, "Attempted to resume when not recording or not paused")
            return
        }
        
        isPaused = false
        AppLogger.logCritical(TAG_SERVICE, "Recording resumed in foreground service")
        
        // Send broadcast to ViewModel
        sendBroadcast(BROADCAST_RESUME)
        updateNotification(System.currentTimeMillis() - recordingStartTime, false)
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
        isPaused = false
        recordingStartTime = 0L
        lastBackgroundTime = 0L
        recordingStateManager.clearRecordingState()
        
        // Send broadcast to ViewModel
        sendBroadcast(BROADCAST_STOP)
    }
    
    private fun sendBroadcast(action: String) {
        val broadcastIntent = Intent(action).apply {
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)
        AppLogger.d(TAG_SERVICE, "Sent broadcast: $action")
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
                NotificationManager.IMPORTANCE_HIGH // HIGH để hiển thị lock screen
            ).apply {
                description = "Ongoing recording notification with controls"
                enableVibration(false) // Không rung khi recording
                enableLights(true)
                lockscreenVisibility = 1 // NotificationManager.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(durationMs: Long, isPausedState: Boolean): Notification {
        // Sử dụng NotificationDeepLinkHandler để tạo PendingIntent với route đúng
        // Recording luôn về RECORD screen
        val pendingIntent = notificationDeepLinkHandler.createPendingIntent(AppRoutes.RECORD)
        
        // Pause/Resume action
        val pauseResumeAction = if (isPausedState) {
            val resumeIntent = Intent(this, RecordingForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Resume",
                PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            val pauseIntent = Intent(this, RecordingForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
            )
        }
        
        // Stop action
        val stopIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPendingIntent
        )
        
        val durationText = formatDuration(durationMs)
        val statusText = if (isPausedState) "Paused" else "Recording"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$statusText - $durationText")
            .setContentText("Tap to return to app")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Mic icon
            .setContentIntent(pendingIntent)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .setOngoing(!isPausedState) // Cho phép dismiss khi paused
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Lock screen
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(true)
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

