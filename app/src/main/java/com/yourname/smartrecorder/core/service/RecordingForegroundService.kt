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
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.yourname.smartrecorder.R
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_SERVICE
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_LIFECYCLE
import com.yourname.smartrecorder.MainActivity
import com.yourname.smartrecorder.core.notification.NotificationDeepLinkHandler
import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.data.repository.RecordingSessionRepository
import com.yourname.smartrecorder.ui.navigation.AppRoutes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject

/**
 * Foreground service with custom collapsible notification
 * Uses RemoteViews for precise layout control
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
    private val BACKGROUND_WARNING_THRESHOLD = 30 * 60 * 1000L
    
    companion object {
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_PAUSE = "com.yourname.smartrecorder.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.yourname.smartrecorder.RESUME_RECORDING"
        const val ACTION_STOP = "com.yourname.smartrecorder.STOP_RECORDING"
        
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
        
        val filter = IntentFilter(BROADCAST_UPDATE_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(notificationUpdateReceiver, filter)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                pauseRecording()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                resumeRecording()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                // ✅ CRITICAL FIX: Stop button giờ dùng PendingIntent đến MainActivity trực tiếp
                // Không cần xử lý ở đây nữa vì PendingIntent đã mở app rồi
                // Chỉ cần stop service sau khi app đã mở
                AppLogger.d(TAG_SERVICE, "ACTION_STOP received", "App should already be opened by PendingIntent")
                
                // ✅ Dùng coroutine để đợi app mở, sau đó mới stop service (non-blocking)
                serviceScope.launch {
                    delay(300) // Đợi app mở và xử lý intent
                    AppLogger.d(TAG_SERVICE, "Stopping service after delay", "")
                    stopRecording()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                
                return START_NOT_STICKY
            }
            else -> {
                val recordingId = intent?.getStringExtra("recordingId")
                val fileName = intent?.getStringExtra("fileName")
                
                if (recordingId != null && fileName != null && !isRecording) {
                    startRecording(recordingId, fileName)
                    return START_STICKY
                }
                return START_NOT_STICKY
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            recordingSessionRepository.setIdle()
        }
        try {
            unregisterReceiver(notificationUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Ignore
        }
    }
    
    fun startRecording(recordingId: String, fileName: String) {
        if (isRecording) return
        
        recordingStartTime = System.currentTimeMillis()
        lastBackgroundTime = 0L
        
        // ✅ Tối ưu: Tạo notification TRƯỚC khi thao tác IO để hiển thị nhanh hơn
        isRecording = true
        isPaused = false
        startForeground(NOTIFICATION_ID, createNotification(0, false))
        
        val filePath = File(getFilesDir(), "recordings/$fileName").absolutePath
        recordingSessionRepository.setActive(
            recordingId = recordingId,
            filePath = filePath,
            startTimeMs = recordingStartTime
        )
        recordingStateManager.setRecordingActive(recordingId, fileName, recordingStartTime)
    }
    
    fun pauseRecording() {
        val currentState = recordingSessionRepository.getCurrentState()
        if (currentState !is com.yourname.smartrecorder.domain.state.RecordingState.Active || currentState.isPaused) {
            return
        }
        
        isPaused = true
        try {
            runBlocking { audioRecorder.pause() }
        } catch (e: Exception) {
            isPaused = false
            return
        }
        
        recordingSessionRepository.pause()
        sendBroadcast(BROADCAST_PAUSE)
        
        val updatedState = recordingSessionRepository.getCurrentState()
        if (updatedState is com.yourname.smartrecorder.domain.state.RecordingState.Active) {
            updateNotification(updatedState.getElapsedMs(), true)
        }
    }
    
    fun resumeRecording() {
        val currentState = recordingSessionRepository.getCurrentState()
        if (currentState !is com.yourname.smartrecorder.domain.state.RecordingState.Active || !currentState.isPaused) {
            return
        }
        
        isPaused = false
        try {
            runBlocking { audioRecorder.resume() }
        } catch (e: Exception) {
            isPaused = true
            return
        }
        
        recordingSessionRepository.resume()
        sendBroadcast(BROADCAST_RESUME)
        
        val updatedState = recordingSessionRepository.getCurrentState()
        if (updatedState is com.yourname.smartrecorder.domain.state.RecordingState.Active) {
            updateNotification(updatedState.getElapsedMs(), false)
        }
    }
    
    fun stopRecording() {
        if (!isRecording) return
        
        isRecording = false
        isPaused = false
        recordingStartTime = 0L
        lastBackgroundTime = 0L
        recordingStateManager.clearRecordingState()
        
        sendBroadcast(BROADCAST_STOP)
    }
    
    private fun sendBroadcast(action: String) {
        sendBroadcast(Intent(action).apply { setPackage(packageName) })
    }
    
    fun updateNotification(durationMs: Long, isPaused: Boolean = false) {
        if (isRecording) {
            val currentState = recordingSessionRepository.getCurrentState()
            val actualDuration = if (currentState is com.yourname.smartrecorder.domain.state.RecordingState.Active) {
                currentState.getElapsedMs()
            } else {
                durationMs
            }
            
            notificationManager?.notify(NOTIFICATION_ID, createNotification(actualDuration, isPaused))
        }
    }
    
    fun onAppBackgrounded() {
        if (isRecording) {
            lastBackgroundTime = System.currentTimeMillis()
        }
    }
    
    fun onAppForegrounded() {
        if (isRecording && lastBackgroundTime > 0) {
            lastBackgroundTime = 0L
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // ✅ Chỉ tạo channel nếu chưa có, không xóa/recreate để giữ tùy chỉnh của user
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Recording",
                    NotificationManager.IMPORTANCE_HIGH // HIGH to always stay on top
                ).apply {
                    description = "Ongoing recording notification"
                    setSound(null, null) // Silent notification (không cần setSilent(true))
                    enableVibration(false)
                    enableLights(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
                AppLogger.d(TAG_SERVICE, "Recording channel created -> importance: HIGH")
            } else {
                AppLogger.d(TAG_SERVICE, "Recording channel already exists, skipping creation")
            }
        }
    }
    
    /**
     * Creates custom notification layout using RemoteViews
     * Collapsed: Time + Status + 3 icons in a row
     * Expanded: App name + Time + Status + 3 icons in a row
     */
    private fun createNotification(durationMs: Long, isPausedState: Boolean): Notification {
        val pendingIntent = notificationDeepLinkHandler.createPendingIntent(AppRoutes.RECORD)
        
        val durationText = formatDuration(durationMs)
        val statusText = if (isPausedState) "Paused" else "Recording"
        
        // Create PendingIntents for actions
        // ✅ CRITICAL FIX: Stop button dùng PendingIntent đến MainActivity (deep link) thay vì Service
        // Để mở app trực tiếp, không bị Android 12+ chặn background Activity start
        val currentState = recordingSessionRepository.getCurrentState()
        val recordingId = if (currentState is com.yourname.smartrecorder.domain.state.RecordingState.Active) {
            currentState.recordingId
        } else {
            null
        }
        val stopRoute = if (recordingId != null) {
            com.yourname.smartrecorder.ui.navigation.AppRoutes.transcriptDetail(recordingId)
        } else {
            AppRoutes.RECORD
        }
        val stopPendingIntent = notificationDeepLinkHandler.createPendingIntent(stopRoute)
        
        val pauseResumePendingIntent = if (isPausedState) {
            createActionPendingIntent(ACTION_RESUME, 2)
        } else {
            createActionPendingIntent(ACTION_PAUSE, 2)
        }
        
        // ✅ Set icon cho tất cả buttons
        val pauseResumeIcon = if (isPausedState) {
            R.drawable.ic_notification_play
        } else {
            R.drawable.ic_notification_pause
        }
        val stopIcon = R.drawable.ic_notification_stop
        
        // Create collapsed view (compact layout)
        val collapsedView = RemoteViews(packageName, R.layout.notification_recording_collapsed).apply {
            setTextViewText(R.id.notification_time, durationText)
            setTextViewText(R.id.notification_status, statusText)
            
            // Set icons for buttons
            setImageViewResource(R.id.btn_pause_resume, pauseResumeIcon)
            setImageViewResource(R.id.btn_stop, stopIcon)
            
            // Set click listeners
            setOnClickPendingIntent(R.id.btn_pause_resume, pauseResumePendingIntent)
            setOnClickPendingIntent(R.id.btn_stop, stopPendingIntent)
        }
        
        // Create expanded view
        val expandedView = RemoteViews(packageName, R.layout.notification_recording_expanded).apply {
            setTextViewText(R.id.notification_time, durationText)
            setTextViewText(R.id.notification_status, statusText)
            
            // Set icons for buttons
            setImageViewResource(R.id.btn_pause_resume, pauseResumeIcon)
            setImageViewResource(R.id.btn_stop, stopIcon)
            
            setOnClickPendingIntent(R.id.btn_pause_resume, pauseResumePendingIntent)
            setOnClickPendingIntent(R.id.btn_stop, stopPendingIntent)
        }
        
        // Create heads-up view (same as collapsed for consistency)
        val headsUpView = RemoteViews(packageName, R.layout.notification_recording_collapsed).apply {
            setTextViewText(R.id.notification_time, durationText)
            setTextViewText(R.id.notification_status, statusText)
            setImageViewResource(R.id.btn_pause_resume, pauseResumeIcon)
            setImageViewResource(R.id.btn_stop, stopIcon)
            setOnClickPendingIntent(R.id.btn_pause_resume, pauseResumePendingIntent)
            setOnClickPendingIntent(R.id.btn_stop, stopPendingIntent)
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setCustomHeadsUpContentView(headsUpView) // ✅ Heads-up view với controls
            .setOngoing(true) // ✅ Luôn ongoing để không bị dismiss (kể cả khi paused)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // HIGH to stay on top
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            // ✅ BỎ setSilent(true) - channel đã có setSound(null, null) nên vẫn im lặng
            // Không bị đẩy vào nhóm "silent" và hiển thị nhanh hơn
            .setStyle(NotificationCompat.DecoratedCustomViewStyle()) // ✅ DecoratedCustomViewStyle để hệ thống bọc nền mặc định
            .build()
    }
    
    private fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, RecordingForegroundService::class.java).apply {
            this.action = action
        }
        // ✅ Dùng getForegroundService() cho API 26+ để tránh bị throttle trên Android 12+
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            // API < 26: dùng getService()
            @Suppress("DEPRECATION")
            PendingIntent.getService(
                this, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
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