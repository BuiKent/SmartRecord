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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.yourname.smartrecorder.MainActivity
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_SERVICE
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_LIFECYCLE
import com.yourname.smartrecorder.core.notification.NotificationDeepLinkHandler
import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.data.repository.RecordingSessionRepository
import com.yourname.smartrecorder.ui.navigation.AppRoutes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
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
    
    @Inject
    lateinit var recordingSessionRepository: RecordingSessionRepository
    
    @Inject
    lateinit var audioRecorder: AudioRecorder
    
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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
        
        // ⚠️ CRITICAL: Update repository state if recording was active
        if (isRecording) {
            AppLogger.logRareCondition(TAG_SERVICE, "Service destroyed while recording", 
                "recordingDuration=${System.currentTimeMillis() - recordingStartTime}ms")
            // Set idle to prevent stale state
            recordingSessionRepository.setIdle()
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
        
        recordingStartTime = System.currentTimeMillis()
        lastBackgroundTime = 0L
        
        AppLogger.logCritical(TAG_SERVICE, "Recording started in foreground service", 
            "recordingId=$recordingId, fileName=$fileName")
        
        // ⚠️ CRITICAL: Update repository state FIRST
        val filePath = File(getFilesDir(), "recordings/$fileName").absolutePath
        recordingSessionRepository.setActive(
            recordingId = recordingId,
            filePath = filePath,
            startTimeMs = recordingStartTime
        )
        
        isRecording = true
        // ⚠️ CRITICAL: Khi bắt đầu recording thì isPausedState = false (đang recording, chưa paused)
        startForeground(NOTIFICATION_ID, createNotification(0, false))
        recordingStateManager.setRecordingActive(recordingId, fileName, recordingStartTime)
    }
    
    fun pauseRecording() {
        // ⚠️ CRITICAL: Check repository state thay vì local state
        val currentState = recordingSessionRepository.getCurrentState()
        if (currentState !is com.yourname.smartrecorder.domain.state.RecordingState.Active || currentState.isPaused) {
            AppLogger.logRareCondition(TAG_SERVICE, "Attempted to pause when not recording or already paused",
                "state=${currentState.javaClass.simpleName}")
            return
        }
        
        isPaused = true
        AppLogger.logCritical(TAG_SERVICE, "Recording paused in foreground service")
        
        // ⚠️ CRITICAL: Pause AudioRecorder FIRST - dùng runBlocking để đảm bảo pause hoàn thành
        try {
            runBlocking {
                audioRecorder.pause()
                AppLogger.d(TAG_SERVICE, "AudioRecorder paused successfully")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG_SERVICE, "Failed to pause AudioRecorder", e)
            // Nếu pause thất bại, revert state
            isPaused = false
            return
        }
        
        // ⚠️ CRITICAL: Update repository state (repository will set pauseStartTimeMs)
        recordingSessionRepository.pause()
        
        // Send broadcast to ViewModel
        sendBroadcast(BROADCAST_PAUSE)
        
        // ✅ Update notification với actual duration (đã trừ paused time)
        val updatedState = recordingSessionRepository.getCurrentState()
        if (updatedState is com.yourname.smartrecorder.domain.state.RecordingState.Active) {
            updateNotification(updatedState.getElapsedMs(), true)
        }
    }
    
    fun resumeRecording() {
        // ⚠️ CRITICAL: Check repository state thay vì local state
        val currentState = recordingSessionRepository.getCurrentState()
        if (currentState !is com.yourname.smartrecorder.domain.state.RecordingState.Active || !currentState.isPaused) {
            AppLogger.logRareCondition(TAG_SERVICE, "Attempted to resume when not recording or not paused",
                "state=${currentState.javaClass.simpleName}")
            return
        }
        
        isPaused = false
        AppLogger.logCritical(TAG_SERVICE, "Recording resumed in foreground service")
        
        // ⚠️ CRITICAL: Resume AudioRecorder FIRST - dùng runBlocking để đảm bảo resume hoàn thành
        try {
            runBlocking {
                audioRecorder.resume()
                AppLogger.d(TAG_SERVICE, "AudioRecorder resumed successfully")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG_SERVICE, "Failed to resume AudioRecorder", e)
            // Nếu resume thất bại, revert state
            isPaused = true
            return
        }
        
        // ⚠️ CRITICAL: Update repository state (repository will calculate totalPausedDurationMs)
        recordingSessionRepository.resume()
        
        // Send broadcast to ViewModel
        sendBroadcast(BROADCAST_RESUME)
        
        // ✅ Update notification với actual duration (đã trừ paused time)
        val updatedState = recordingSessionRepository.getCurrentState()
        if (updatedState is com.yourname.smartrecorder.domain.state.RecordingState.Active) {
            updateNotification(updatedState.getElapsedMs(), false)
        }
    }
    
    fun stopRecording() {
        if (!isRecording) {
            AppLogger.logRareCondition(TAG_SERVICE, "Attempted to stop recording when not recording")
            return
        }
        
        val duration = System.currentTimeMillis() - recordingStartTime
        AppLogger.logCritical(TAG_SERVICE, "Recording stopped in foreground service", 
            "duration=${duration}ms")
        
        // ⚠️ CRITICAL: Don't stop AudioRecorder here - let ViewModel do it
        // ViewModel will handle stopping AudioRecorder, saving file, and updating repository state
        
        isRecording = false
        isPaused = false
        recordingStartTime = 0L
        lastBackgroundTime = 0L
        recordingStateManager.clearRecordingState()
        
        // ⚠️ CRITICAL: Send broadcast FIRST, ViewModel will:
        // 1. Stop AudioRecorder
        // 2. Save file to database
        // 3. Update repository to Idle
        sendBroadcast(BROADCAST_STOP)
        
        // ⚠️ CRITICAL: Don't set repository to Idle here - let ViewModel do it after saving
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
            // ⚠️ CRITICAL: Lấy ACTUAL recording duration từ repository (đã trừ paused time)
            val currentState = recordingSessionRepository.getCurrentState()
            val actualDuration = if (currentState is com.yourname.smartrecorder.domain.state.RecordingState.Active) {
                currentState.getElapsedMs() // Đã trừ totalPausedDurationMs
            } else {
                durationMs // Fallback
            }
            
            // ⚠️ CRITICAL: Truyền isPaused trực tiếp (không đảo ngược)
            // isPaused = false → isPausedState = false → "Recording" + icon pause
            // isPaused = true → isPausedState = true → "Paused" + icon play
            val notification = createNotification(actualDuration, isPaused)
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
            val manager = getSystemService(NotificationManager::class.java)
            
            // ⚠️ CRITICAL: Luôn xóa channel cũ để đảm bảo importance được update
            try {
                val existingChannel = manager.getNotificationChannel(CHANNEL_ID)
                if (existingChannel != null) {
                    AppLogger.d(TAG_SERVICE, "Deleting existing channel -> importance: ${existingChannel.importance}")
                    manager.deleteNotificationChannel(CHANNEL_ID)
                    // Small delay to ensure deletion completes
                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                AppLogger.d(TAG_SERVICE, "Error deleting channel: ${e.message}")
            }
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_DEFAULT // DEFAULT để hiện icon trên status bar
            ).apply {
                description = "Ongoing recording notification with controls"
                setSound(null, null) // ⚠️ CRITICAL: Không có sound
                enableVibration(false) // Không rung khi recording
                enableLights(false) // Không flash LED
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // Vẫn hiển thị trên lock screen
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
            
            // Verify channel was created correctly
            val createdChannel = manager.getNotificationChannel(CHANNEL_ID)
            if (createdChannel != null) {
                AppLogger.d(TAG_SERVICE, "Channel created -> importance: ${createdChannel.importance}, canShowBadge: ${createdChannel.canShowBadge()}")
            } else {
                AppLogger.w(TAG_SERVICE, "Channel was not created!")
            }
        }
    }
    
    private fun createNotification(durationMs: Long, isPausedState: Boolean): Notification {
        val pendingIntent = notificationDeepLinkHandler.createPendingIntent(AppRoutes.RECORD)
        
        val durationText = formatDuration(durationMs)
        
        // Tạo actions với CUSTOM ICON
        val pauseResumeAction = if (isPausedState) {
            // Đang paused → hiện Play
            val resumeIntent = Intent(this, RecordingForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            val resumePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PendingIntent.getForegroundService(
                    this, 1, resumeIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getService(
                    this, 1, resumeIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            NotificationCompat.Action.Builder(
                com.yourname.smartrecorder.R.drawable.ic_notification_play, // ✅ Custom icon
                "Resume", // ✅ Có text (MediaStyle sẽ chỉ hiển thị icon trong compact view)
                resumePendingIntent
            ).build()
        } else {
            // Đang recording → hiện Pause
            val pauseIntent = Intent(this, RecordingForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PendingIntent.getForegroundService(
                    this, 2, pauseIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getService(
                    this, 2, pauseIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            NotificationCompat.Action.Builder(
                com.yourname.smartrecorder.R.drawable.ic_notification_pause, // ✅ Custom icon
                "Pause", // ✅ Có text (MediaStyle sẽ chỉ hiển thị icon trong compact view)
                pausePendingIntent
            ).build()
        }
        
        // Stop action
        val stopIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent.getForegroundService(
                this, 3, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getService(
                this, 3, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val stopAction = NotificationCompat.Action.Builder(
            com.yourname.smartrecorder.R.drawable.ic_notification_stop, // ✅ Icon Stop (vuông) thay vì X
            "Stop", // ✅ Có text (MediaStyle sẽ chỉ hiển thị icon trong compact view)
            stopPendingIntent
        ).build()
        
        // ✅ Text hiển thị - dùng text ngắn để tránh bị cắt
        val statusText = if (isPausedState) "Paused" else "Recording"
        val contentText = "$durationText · Tap to return" // Rút ngắn text để tránh bị cắt
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(statusText)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Icon mic trong status bar
            .setContentIntent(pendingIntent)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .setOngoing(!isPausedState)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false) // Không hiển thị timestamp
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setStyle(  // ✅ MediaStyle để hiển thị icon trong compact view
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1) // Hiển thị action index 0 (Play/Pause) và 1 (Stop) trong compact view
            )
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

