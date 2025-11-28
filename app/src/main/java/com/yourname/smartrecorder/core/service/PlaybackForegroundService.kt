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
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.yourname.smartrecorder.MainActivity
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_SERVICE
import com.yourname.smartrecorder.core.notification.NotificationDeepLinkHandler
import com.yourname.smartrecorder.core.audio.AudioPlayer
import com.yourname.smartrecorder.data.repository.PlaybackSessionRepository
import com.yourname.smartrecorder.ui.navigation.AppRoutes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service to keep audio playback active when app is in background.
 * Prevents Android from stopping playback when user switches apps.
 */
@AndroidEntryPoint
class PlaybackForegroundService : Service() {
    
    @Inject
    lateinit var notificationDeepLinkHandler: NotificationDeepLinkHandler
    
    @Inject
    lateinit var playbackSessionRepository: PlaybackSessionRepository
    
    @Inject
    lateinit var audioPlayer: AudioPlayer
    
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var notificationManager: NotificationManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var isPlaying = false
    private var currentTitle: String = ""
    private var currentRecordingId: String? = null  // ← Thêm để lưu recordingId
    private var currentPosition: Long = 0L
    private var totalDuration: Long = 0L
    
    companion object {
        private const val CHANNEL_ID = "playback_channel"
        private const val NOTIFICATION_ID = 2
        const val ACTION_PAUSE = "com.yourname.smartrecorder.PAUSE_PLAYBACK"
        const val ACTION_RESUME = "com.yourname.smartrecorder.RESUME_PLAYBACK"
        private const val ACTION_STOP = "com.yourname.smartrecorder.STOP_PLAYBACK"
        const val BROADCAST_UPDATE_NOTIFICATION = "com.yourname.smartrecorder.BROADCAST_UPDATE_PLAYBACK_NOTIFICATION"
        
        fun createIntent(context: android.content.Context): Intent {
            return Intent(context, PlaybackForegroundService::class.java)
        }
    }
    
    private val notificationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BROADCAST_UPDATE_NOTIFICATION) {
                val position = intent.getLongExtra("position", 0L)
                val duration = intent.getLongExtra("duration", 0L)
                val isPaused = intent.getBooleanExtra("isPaused", false)
                
                // ⚠️ CRITICAL: Update repository state
                if (currentRecordingId != null) {
                    playbackSessionRepository.updatePosition(position)
                    if (isPaused && isPlaying) {
                        playbackSessionRepository.pause()
                    } else if (!isPaused && !isPlaying) {
                        playbackSessionRepository.resume()
                    }
                }
                
                updateNotification(position, duration, isPaused)
            }
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
        
        // Register BroadcastReceiver for notification updates
        val filter = IntentFilter(BROADCAST_UPDATE_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(notificationUpdateReceiver, filter)
        }
        
        // Create MediaSession for lock screen controls
        mediaSession = MediaSessionCompat(this, "PlaybackService").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    AppLogger.d(TAG_SERVICE, "MediaSession onPlay called")
                    // ⚠️ CRITICAL: Gọi service method trực tiếp thay vì broadcast
                    resumePlayback()
                }
                override fun onPause() {
                    AppLogger.d(TAG_SERVICE, "MediaSession onPause called")
                    // ⚠️ CRITICAL: Gọi service method trực tiếp thay vì broadcast
                    pausePlayback()
                }
                override fun onStop() {
                    AppLogger.d(TAG_SERVICE, "MediaSession onStop called")
                    // ⚠️ CRITICAL: Gọi service method trực tiếp thay vì broadcast
                    stopPlayback()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.logService(TAG_SERVICE, "PlaybackForegroundService", "onStartCommand", 
            "action=${intent?.action}, flags=$flags, startId=$startId")
        
        when (intent?.action) {
            ACTION_PAUSE -> {
                AppLogger.logCritical(TAG_SERVICE, "Pause playback requested from notification")
                pausePlayback()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                AppLogger.logCritical(TAG_SERVICE, "Resume playback requested from notification")
                resumePlayback()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                AppLogger.logCritical(TAG_SERVICE, "Stop playback requested from notification")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Handle start playback only (notification updates via BroadcastReceiver)
                val recordingId = intent?.getStringExtra("recordingId")
                val title = intent?.getStringExtra("title")
                val duration = intent?.getLongExtra("duration", 0L) ?: 0L
                
                // Lưu recordingId nếu có
                if (recordingId != null) {
                    currentRecordingId = recordingId
                }
                
                if (title != null && duration > 0 && !isPlaying) {
                    startPlayback(title, duration)
                    return START_STICKY
                } else {
                    // Ignore update notification calls via Intent (use BroadcastReceiver instead)
                    AppLogger.d(TAG_SERVICE, "Ignoring update notification via Intent - use BroadcastReceiver")
                    return START_NOT_STICKY
                }
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
        
        // ⚠️ CRITICAL: Update repository state if playing
        if (isPlaying) {
            AppLogger.logRareCondition(TAG_SERVICE, "Service destroyed while playing", 
                "title=$currentTitle, position=$currentPosition")
            playbackSessionRepository.setIdle()
        }
        
        // Unregister BroadcastReceiver
        try {
            unregisterReceiver(notificationUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
        // Release MediaSession
        mediaSession?.release()
        mediaSession = null
    }
    
    fun startPlayback(title: String, duration: Long) {
        isPlaying = true
        currentTitle = title
        totalDuration = duration
        currentPosition = 0L
        
        AppLogger.logCritical(TAG_SERVICE, "Playback started in foreground service", 
            "recordingId=$currentRecordingId, title=$title, duration=${duration}ms")
        
        // ⚠️ CRITICAL: Update repository state
        if (currentRecordingId != null) {
            playbackSessionRepository.setPlaying(
                recordingId = currentRecordingId!!,
                positionMs = 0L,
                durationMs = duration,
                isLooping = false  // Will be updated if looping is enabled
            )
        }
        
        // Update MediaSession metadata
        updateMediaSessionMetadata(title, duration)
        updateMediaSessionPlaybackState(true, 0L)
        
        startForeground(NOTIFICATION_ID, createNotification(0, duration, true))
    }
    
    fun stopPlayback() {
        if (!isPlaying) {
            AppLogger.logRareCondition(TAG_SERVICE, "Attempted to stop playback when not playing")
            return
        }
        
        AppLogger.logCritical(TAG_SERVICE, "Playback stopped in foreground service", 
            "recordingId=$currentRecordingId, title=$currentTitle, finalPosition=$currentPosition")
        
        // ⚠️ CRITICAL: Update repository state FIRST
        playbackSessionRepository.setIdle()
        
        isPlaying = false
        currentTitle = ""
        currentRecordingId = null
        currentPosition = 0L
        totalDuration = 0L
        
        // Clear MediaSession
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 1.0f)
                .build()
        )
    }
    
    fun pausePlayback() {
        val currentState = playbackSessionRepository.state.value
        if (currentState !is com.yourname.smartrecorder.domain.state.PlaybackState.Playing) {
            AppLogger.logRareCondition(TAG_SERVICE, "Attempted to pause when not playing")
            return
        }
        
        isPlaying = false
        AppLogger.logCritical(TAG_SERVICE, "Playback paused in foreground service", 
            "recordingId=${currentState.recordingId}, position=${currentState.positionMs}")
        
        // ⚠️ CRITICAL: Pause AudioPlayer FIRST
        serviceScope.launch {
            try {
                audioPlayer.pause()
                AppLogger.d(TAG_SERVICE, "AudioPlayer paused successfully")
            } catch (e: Exception) {
                AppLogger.e(TAG_SERVICE, "Failed to pause AudioPlayer", e)
            }
        }
        
        // ⚠️ CRITICAL: Update repository state
        playbackSessionRepository.pause()
        
        // Update MediaSession
        updateMediaSessionPlaybackState(false, currentState.positionMs)
        updateNotification(currentState.positionMs, currentState.durationMs, true)
    }
    
    fun resumePlayback() {
        val currentState = playbackSessionRepository.state.value
        if (currentState !is com.yourname.smartrecorder.domain.state.PlaybackState.Paused) {
            AppLogger.logRareCondition(TAG_SERVICE, "Attempted to resume when not paused")
            return
        }
        
        isPlaying = true
        AppLogger.logCritical(TAG_SERVICE, "Playback resumed in foreground service", 
            "recordingId=${currentState.recordingId}, position=${currentState.positionMs}")
        
        // ⚠️ CRITICAL: Resume AudioPlayer FIRST
        serviceScope.launch {
            try {
                audioPlayer.resume()
                AppLogger.d(TAG_SERVICE, "AudioPlayer resumed successfully")
            } catch (e: Exception) {
                AppLogger.e(TAG_SERVICE, "Failed to resume AudioPlayer", e)
            }
        }
        
        // ⚠️ CRITICAL: Update repository state
        playbackSessionRepository.resume()
        
        // Update MediaSession
        updateMediaSessionPlaybackState(true, currentState.positionMs)
        updateNotification(currentState.positionMs, currentState.durationMs, false)
    }
    
    fun updateNotification(position: Long, duration: Long, isPaused: Boolean = false) {
        if (isPlaying) {
            currentPosition = position
            totalDuration = duration
            
            // Update MediaSession playback state
            updateMediaSessionPlaybackState(!isPaused, position)
            
            val notification = createNotification(position, duration, !isPaused)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }
    
    private fun updateMediaSessionMetadata(title: String, duration: Long) {
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Smart Recorder")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .build()
        )
    }
    
    private fun updateMediaSessionPlaybackState(isPlaying: Boolean, position: Long) {
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, position, 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP
                )
                .build()
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Playback",
                NotificationManager.IMPORTANCE_DEFAULT // DEFAULT để hiện icon trên status bar
            ).apply {
                description = "Ongoing audio playback notification"
                setSound(null, null) // ⚠️ CRITICAL: Không có sound
                enableVibration(false) // Không rung
                enableLights(false) // Không flash LED
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // Vẫn hiển thị trên lock screen
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(position: Long, duration: Long, isPlaying: Boolean): Notification {
        // Sử dụng NotificationDeepLinkHandler để tạo PendingIntent với route đúng
        val route = if (currentRecordingId != null) {
            AppRoutes.transcriptDetail(currentRecordingId!!)
        } else {
            AppLogger.logRareCondition(TAG_SERVICE, "Playback service started without recordingId")
            AppRoutes.RECORD  // Fallback
        }
        
        val pendingIntent = notificationDeepLinkHandler.createPendingIntent(route)
        
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
        
        // Play/Pause action
        val playPauseAction = NotificationCompat.Action(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            pausePendingIntent
        )
        
        // Stop action
        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPendingIntent
        )
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle.ifEmpty { "Audio Playback" })
            .setContentText("$positionText / $durationText") // Time labels trong expanded view
            .setSmallIcon(if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause) // Icon khác nhau cho play/pause
            .setContentIntent(pendingIntent)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // DEFAULT để hiện icon trên status bar
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Lock screen visibility
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true) // ⚠️ CRITICAL: Chỉ alert lần đầu, update im lặng
            .setSilent(true) // ⚠️ CRITICAL: Im lặng hoàn toàn
        
        // Add MediaStyle for lock screen controls
        mediaSession?.let { session ->
            builder.setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(0, 1)  // Compact view: Play/Pause (0) và Stop (1)
                    .setMediaSession(session.sessionToken)
            )
        }
        
        return builder.build()
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

