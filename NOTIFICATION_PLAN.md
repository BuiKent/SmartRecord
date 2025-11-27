# 🎯 KẾ HOẠCH HỆ THỐNG THÔNG BÁO - SMART RECORDER NOTES
## (Notification System Plan - Smart Recorder Notes)

**📚 Tài liệu liên quan:**
- `todolist.md` - Checklist tổng thể (section: Notification System)
- `FOREGROUND_SERVICE_STATUS.md` - Trạng thái hiện tại và checklist chi tiết

**Mục tiêu:** Hệ thống thông báo toàn diện, production-ready cho app Smart Recorder Notes, tập trung vào điều khiển ghi âm qua màn hình khóa và thanh công cụ.

**Nguyên tắc:**
- ✅ **Điều khiển từ xa**: Pause/Resume/Stop recording từ lock screen và notification bar
- ✅ **Media Controls**: Playback notification với MediaStyle chuẩn Android
- ✅ **Ổn định**: Xử lý lỗi đầy đủ, graceful degradation
- ✅ **User-friendly**: Không spam, tôn trọng user preference
- ✅ **Production-ready**: Test đầy đủ, logging chi tiết

---

## 📋 TỔNG QUAN

### Scope Phase 1

✅ **Có:**
- **Foreground Service Notifications** (cải thiện):
  - Recording notification với action buttons (Pause/Resume/Stop)
  - Lock screen controls cho recording
  - MediaStyle notification cho playback
  - Media controls (Play/Pause/Stop) trong notification và lock screen
- **Daily Promotional Notifications**:
  - 1 notification/ngày - nội dung app content
  - WorkManager scheduled daily
  - Frequency cap (3/ngày, 4h interval)
- **Deep links** cơ bản (record, library, transcript, settings)
- **Settings toggle** đơn giản
- **Permission handling** an toàn

❌ **Tạm bỏ:**
- Support/Ads notifications
- Premium content notifications
- Action buttons phức tạp (chỉ giữ Pause/Resume/Stop)
- Nhiều channels (chỉ 3: recording, playback, app_content)

---

## 🏗️ KIẾN TRÚC

```
core/notification/
├── AppNotificationManager.kt          # Core manager cho app content notifications
├── NotificationChannelManager.kt      # Quản lý channels (recording, playback, app_content)
├── NotificationContent.kt            # App content messages
├── NotificationScheduler.kt          # WorkManager scheduler
├── NotificationFrequencyCap.kt       # Tránh spam
├── NotificationDeepLinkHandler.kt    # Deep link handler
└── worker/
    └── NotificationWorker.kt         # Background worker

core/service/
├── RecordingForegroundService.kt     # (CẢI THIỆN) Thêm pause/resume actions
└── PlaybackForegroundService.kt      # (CẢI THIỆN) MediaStyle notification
```

**Tổng số files:** 8 files (7 mới + 2 cải thiện)

---

## 📝 YÊU CẦU CHI TIẾT

### 1. Recording Notification (CẢI THIỆN)

**Mục tiêu:** Điều khiển recording từ lock screen và notification bar

**Tính năng:**
- ✅ Hiển thị duration, status (Recording/Paused)
- ✅ Action buttons: **Pause/Resume**, **Stop**
- ✅ Lock screen visibility: **PUBLIC**
- ✅ Priority: **HIGH** (hiển thị lock screen)
- ✅ Expandable notification với waveform preview (optional)
- ✅ Tap notification → mở app đến RecordScreen

**Channels:**
- ID: `recording_channel`
- Name: "Recording"
- Importance: **HIGH**
- Visibility: **PUBLIC**

**Actions:**
```kotlin
// Pause/Resume action
ACTION_PAUSE = "com.yourname.smartrecorder.PAUSE_RECORDING"
ACTION_RESUME = "com.yourname.smartrecorder.RESUME_RECORDING"
ACTION_STOP = "com.yourname.smartrecorder.STOP_RECORDING"
```

**Notification Style:**
- Small icon: Mic icon (custom)
- Content: "Recording - 00:05:23" hoặc "Paused - 00:05:23"
- Actions: 
  - Pause (khi đang recording) / Resume (khi paused)
  - Stop
- Ongoing: true (khi recording), false (khi paused)

---

### 2. Playback Notification (CẢI THIỆN)

**Mục tiêu:** Media controls chuẩn Android với MediaStyle

**Tính năng:**
- ✅ MediaStyle notification (Android 5.0+)
- ✅ Media controls: **Play/Pause**, **Stop**
- ✅ Lock screen controls
- ✅ Hiển thị title, position, duration
- ✅ Progress bar (optional, Android 10+)
- ✅ Tap notification → mở app đến TranscriptScreen

**Channels:**
- ID: `playback_channel`
- Name: "Audio Playback"
- Importance: **LOW** (không làm phiền)
- Visibility: **PUBLIC**

**Actions:**
```kotlin
ACTION_PLAY = "com.yourname.smartrecorder.PLAY_PLAYBACK"
ACTION_PAUSE = "com.yourname.smartrecorder.PAUSE_PLAYBACK"
ACTION_STOP = "com.yourname.smartrecorder.STOP_PLAYBACK"
```

**Notification Style:**
- MediaStyle với large icon (album art placeholder)
- Content: Recording title
- Subtext: "00:05:23 / 00:15:45"
- Actions: Play/Pause, Stop
- Ongoing: false (cho phép dismiss)

---

### 3. App Content Notifications (MỚI)

**Mục tiêu:** Khích lệ sử dụng app, giới thiệu tính năng

**Nội dung:**
- Khích lệ sử dụng app
- Giới thiệu tính năng (Live Transcribe, Export, Flashcards)
- Tips & mẹo
- Tone: **Giá trị, chăm sóc** - KHÔNG bán hàng

**Ví dụ:**
- "🎙️ Bạn có cuộc họp quan trọng? Ghi âm và chuyển đổi thành transcript tự động!"
- "📝 Sử dụng Live Transcribe để xem transcript real-time khi đang ghi âm"
- "📚 Tạo flashcards từ câu hỏi trong transcript để ôn tập hiệu quả"
- "💾 Export transcript sang nhiều định dạng: TXT, Markdown, SRT"
- "🔍 Tìm kiếm trong lịch sử ghi âm bằng từ khóa hoặc nội dung"

**Tần suất:**
- **1 notification/ngày** (WorkManager chạy mỗi 24 giờ)
- Frequency cap: Tối đa **3/ngày**, tối thiểu **4h** giữa các lần
- Không gửi: **22:00 - 7:00** (tránh làm phiền ban đêm)

**Channels:**
- ID: `app_content_channel`
- Name: "Thông báo Smart Recorder"
- Importance: **DEFAULT** (user có thể điều chỉnh)
- Visibility: **PUBLIC**

---

## 🔧 TRIỂN KHAI CHI TIẾT

### Phase 1: Cải thiện Foreground Service Notifications ✅ PARTIALLY COMPLETED

**📖 Xem trạng thái hiện tại:** `FOREGROUND_SERVICE_STATUS.md`

**Status:**
- ✅ RecordingForegroundService: Pause/Resume actions, improved notification, lock screen visibility
- ⏳ PlaybackForegroundService: MediaStyle notification (pending)

#### 1.1. RecordingForegroundService.kt (CẢI THIỆN) ✅ COMPLETED

**File:** `app/src/main/java/com/yourname/smartrecorder/core/service/RecordingForegroundService.kt`

**📋 Checklist:** Xem `FOREGROUND_SERVICE_STATUS.md` section "RecordingForegroundService.kt"

**Thay đổi:**
1. Thêm action constants cho Pause/Resume:
```kotlin
companion object {
    private const val CHANNEL_ID = "recording_channel"
    private const val NOTIFICATION_ID = 1
    private const val ACTION_PAUSE = "com.yourname.smartrecorder.PAUSE_RECORDING"
    private const val ACTION_RESUME = "com.yourname.smartrecorder.RESUME_RECORDING"
    private const val ACTION_STOP = "com.yourname.smartrecorder.STOP_RECORDING"
}
```

2. Xử lý actions trong `onStartCommand`:
```kotlin
when (intent?.action) {
    ACTION_PAUSE -> {
        // Gửi broadcast hoặc callback để ViewModel pause
        pauseRecording()
        return START_NOT_STICKY
    }
    ACTION_RESUME -> {
        resumeRecording()
        return START_NOT_STICKY
    }
    ACTION_STOP -> {
        stopRecording()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }
}
```

3. Cải thiện `createNotification`:
```kotlin
private fun createNotification(durationMs: Long, isPaused: Boolean): Notification {
    val intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("route", AppRoutes.RECORD)
    }
    val pendingIntent = PendingIntent.getActivity(
        this, 0, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    
    // Pause/Resume action
    val pauseResumeAction = if (isPaused) {
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
    val stopAction = NotificationCompat.Action(
        android.R.drawable.ic_menu_close_clear_cancel,
        "Stop",
        PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE)
    )
    
    val durationText = formatDuration(durationMs)
    val statusText = if (isPaused) "Paused" else "Recording"
    
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("$statusText - $durationText")
        .setContentText("Tap to return to app")
        .setSmallIcon(R.drawable.ic_mic) // Custom icon
        .setContentIntent(pendingIntent)
        .addAction(pauseResumeAction)
        .addAction(stopAction)
        .setOngoing(!isPaused) // Cho phép dismiss khi paused
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Lock screen
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setShowWhen(true)
        .build()
}
```

4. Cải thiện channel:
```kotlin
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
            lockscreenVisibility = NotificationChannel.VISIBILITY_PUBLIC
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
```

5. Thêm methods để giao tiếp với ViewModel:
```kotlin
interface RecordingServiceListener {
    fun onPauseRequested()
    fun onResumeRequested()
    fun onStopRequested()
}

private var listener: RecordingServiceListener? = null

fun setListener(listener: RecordingServiceListener?) {
    this.listener = listener
}

private fun pauseRecording() {
    listener?.onPauseRequested()
    // Hoặc dùng BroadcastReceiver
}

private fun resumeRecording() {
    listener?.onResumeRequested()
}

private fun stopRecording() {
    listener?.onStopRequested()
    isRecording = false
    recordingStateManager.clearRecordingState()
}
```

**Hoặc dùng BroadcastReceiver (recommended):**
```kotlin
// Trong RecordingForegroundService
private val broadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
    }
}

override fun onCreate() {
    super.onCreate()
    val filter = IntentFilter().apply {
        addAction(ACTION_PAUSE)
        addAction(ACTION_RESUME)
        addAction(ACTION_STOP)
    }
    registerReceiver(broadcastReceiver, filter)
}

// Trong RecordViewModel
fun handleServiceAction(action: String) {
    when (action) {
        RecordingForegroundService.ACTION_PAUSE -> onPauseClick()
        RecordingForegroundService.ACTION_RESUME -> onResumeClick()
        RecordingForegroundService.ACTION_STOP -> onStopClick()
    }
}
```

---

#### 1.2. PlaybackForegroundService.kt (CẢI THIỆN)

**File:** `app/src/main/java/com/yourname/smartrecorder/core/service/PlaybackForegroundService.kt`

**📋 Checklist:** Xem `FOREGROUND_SERVICE_STATUS.md` section "PlaybackForegroundService.kt"

**Thay đổi:**
1. Sử dụng MediaStyle notification:
```kotlin
import androidx.media.app.NotificationCompat.MediaStyle

private fun createNotification(position: Long, duration: Long, isPlaying: Boolean): Notification {
    // ... existing code ...
    
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(currentTitle.ifEmpty { "Audio Playback" })
        .setContentText("$positionText / $durationText")
        .setSmallIcon(R.drawable.ic_play) // Custom icon
        .setLargeIcon(getLargeIcon()) // Album art placeholder
        .setContentIntent(pendingIntent)
        .addAction(playPauseAction)
        .addAction(stopAction)
        .setStyle(
            MediaStyle()
                .setShowActionsInCompactView(0, 1) // Show Play/Pause and Stop in compact view
                .setMediaSession(mediaSession.sessionToken) // Cần MediaSession
        )
        .setOngoing(false) // Cho phép dismiss
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .build()
}
```

2. Thêm MediaSession:
```kotlin
import android.support.v4.media.session.MediaSessionCompat

private var mediaSession: MediaSessionCompat? = null

override fun onCreate() {
    super.onCreate()
    // ... existing code ...
    mediaSession = MediaSessionCompat(this, "PlaybackService").apply {
        isActive = true
    }
}

override fun onDestroy() {
    super.onDestroy()
    mediaSession?.release()
    mediaSession = null
}
```

3. Cải thiện channel:
```kotlin
private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Audio playback notification with media controls"
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = NotificationChannel.VISIBILITY_PUBLIC
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
```

---

### Phase 2: App Content Notifications (MỚI)

#### 2.1. NotificationChannelManager.kt

**File:** `app/src/main/java/com/yourname/smartrecorder/core/notification/NotificationChannelManager.kt`

```kotlin
package com.yourname.smartrecorder.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationChannelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_RECORDING = "recording_channel"
        const val CHANNEL_PLAYBACK = "playback_channel"
        const val CHANNEL_APP_CONTENT = "app_content_channel"
    }
    
    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Recording channel
            if (manager.getNotificationChannel(CHANNEL_RECORDING) == null) {
                val recordingChannel = NotificationChannel(
                    CHANNEL_RECORDING,
                    "Recording",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Ongoing recording notification with controls"
                    enableVibration(false)
                    enableLights(true)
                    lockscreenVisibility = NotificationChannel.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(recordingChannel)
            }
            
            // Playback channel
            if (manager.getNotificationChannel(CHANNEL_PLAYBACK) == null) {
                val playbackChannel = NotificationChannel(
                    CHANNEL_PLAYBACK,
                    "Audio Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Audio playback notification with media controls"
                    enableVibration(false)
                    enableLights(false)
                    lockscreenVisibility = NotificationChannel.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(playbackChannel)
            }
            
            // App content channel
            if (manager.getNotificationChannel(CHANNEL_APP_CONTENT) == null) {
                val appContentChannel = NotificationChannel(
                    CHANNEL_APP_CONTENT,
                    "Thông báo Smart Recorder",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Thông báo khích lệ và mẹo sử dụng ứng dụng"
                    enableVibration(true)
                    enableLights(true)
                    lockscreenVisibility = NotificationChannel.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(appContentChannel)
            }
        }
    }
}
```

---

#### 2.2. NotificationContent.kt

**File:** `app/src/main/java/com/yourname/smartrecorder/core/notification/NotificationContent.kt`

```kotlin
package com.yourname.smartrecorder.core.notification

import kotlin.random.Random

object NotificationContent {
    private val random = Random(System.currentTimeMillis())
    
    data class NotificationMessage(
        val title: String,
        val content: String,
        val deepLink: String? = null
    )
    
    private val appContentMessages = listOf(
        NotificationMessage(
            title = "🎙️ Ghi âm thông minh",
            content = "Bạn có cuộc họp quan trọng? Ghi âm và chuyển đổi thành transcript tự động với AI!",
            deepLink = AppRoutes.RECORD
        ),
        NotificationMessage(
            title = "📝 Live Transcribe",
            content = "Sử dụng Live Transcribe để xem transcript real-time khi đang ghi âm. Khám phá ngay!",
            deepLink = AppRoutes.REALTIME_TRANSCRIPT
        ),
        NotificationMessage(
            title = "📚 Tạo Flashcards",
            content = "Tạo flashcards từ câu hỏi trong transcript để ôn tập hiệu quả hơn!",
            deepLink = AppRoutes.STUDY
        ),
        NotificationMessage(
            title = "💾 Export linh hoạt",
            content = "Export transcript sang nhiều định dạng: TXT, Markdown, SRT. Phù hợp với mọi nhu cầu!",
            deepLink = AppRoutes.LIBRARY
        ),
        NotificationMessage(
            title = "🔍 Tìm kiếm thông minh",
            content = "Tìm kiếm trong lịch sử ghi âm bằng từ khóa hoặc nội dung. Nhanh chóng và chính xác!",
            deepLink = AppRoutes.LIBRARY
        ),
        NotificationMessage(
            title = "📖 Xem lại transcript",
            content = "Bạn có ${getUnreadCount()} bản ghi âm chưa xem. Xem lại ngay để không bỏ lỡ thông tin quan trọng!",
            deepLink = AppRoutes.LIBRARY
        ),
        NotificationMessage(
            title = "✨ Tính năng mới",
            content = "Khám phá các tính năng mới trong Smart Recorder: Live Transcribe, Flashcards, Export templates!",
            deepLink = AppRoutes.RECORD
        )
    )
    
    fun getRandomMessage(): NotificationMessage {
        return appContentMessages[random.nextInt(appContentMessages.size)]
    }
    
    fun getAllMessages(): List<NotificationMessage> = appContentMessages
    
    // Helper để lấy số lượng recording chưa xem (optional)
    private fun getUnreadCount(): Int {
        // TODO: Query database để lấy số lượng recording chưa xem
        return 0
    }
}
```

---

#### 2.3. NotificationDeepLinkHandler.kt

**File:** `app/src/main/java/com/yourname/smartrecorder/core/notification/NotificationDeepLinkHandler.kt`

```kotlin
package com.yourname.smartrecorder.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.yourname.smartrecorder.MainActivity
import com.yourname.smartrecorder.ui.navigation.AppRoutes

@Singleton
class NotificationDeepLinkHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Create PendingIntent for deep link navigation
     * Route format: "record", "library", "transcript_detail/{recordingId}", etc.
     */
    fun createPendingIntent(route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_route", route)
        }
        
        return PendingIntent.getActivity(
            context,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
```

---

#### 2.4. AppNotificationManager.kt

**File:** `app/src/main/java/com/yourname/smartrecorder/core/notification/AppNotificationManager.kt`

```kotlin
package com.yourname.smartrecorder.core.notification

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.data.local.SettingsStore
import kotlinx.coroutines.flow.first

@Singleton
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelManager: NotificationChannelManager,
    private val settingsStore: SettingsStore,
    private val deepLinkHandler: NotificationDeepLinkHandler
) {
    private val notificationManager = NotificationManagerCompat.from(context)
    
    companion object {
        private const val NOTIFICATION_ID_APP_CONTENT = 1001
    }
    
    init {
        channelManager.createChannels()
    }
    
    /**
     * Show app content notification
     */
    suspend fun showAppContentNotification(title: String, content: String, deepLink: String? = null) {
        // 1. Check permission
        if (!notificationManager.areNotificationsEnabled()) {
            AppLogger.d("Notification", "System permission disabled")
            return
        }
        
        // 2. Check Android 13+ permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                AppLogger.d("Notification", "POST_NOTIFICATIONS not granted")
                return
            }
        }
        
        // 3. Check user preference
        val enabled = settingsStore.notificationsEnabled.first()
        if (!enabled) {
            AppLogger.d("Notification", "Disabled by user preference")
            return
        }
        
        // 4. Build PendingIntent
        val pendingIntent = deepLink?.let { link ->
            deepLinkHandler.createPendingIntent(link)
        } ?: deepLinkHandler.createPendingIntent(AppRoutes.RECORD)
        
        // 5. Build notification
        val notification = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_APP_CONTENT)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Custom icon
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .build()
        
        // 6. Show notification
        try {
            notificationManager.notify(NOTIFICATION_ID_APP_CONTENT, notification)
            AppLogger.d("Notification", "Notification shown: $title")
        } catch (e: SecurityException) {
            AppLogger.e("Notification", "Permission denied", e)
        } catch (e: Exception) {
            AppLogger.e("Notification", "Failed to show notification", e)
        }
    }
}
```

---

#### 2.5. NotificationFrequencyCap.kt

**File:** `app/src/main/java/com/yourname/smartrecorder/core/notification/NotificationFrequencyCap.kt`

```kotlin
package com.yourname.smartrecorder.core.notification

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.yourname.smartrecorder.core.logging.AppLogger
import java.util.Calendar

@Singleton
class NotificationFrequencyCap @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "notification_frequency"
        private const val KEY_LAST_SHOWN = "last_shown_timestamp"
        private const val KEY_TODAY_COUNT = "today_count"
        private const val KEY_LAST_DATE = "last_date"
        
        private const val MAX_PER_DAY = 3
        private const val MIN_INTERVAL_HOURS = 4L
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun canShowNotification(): Boolean {
        val now = System.currentTimeMillis()
        val today = getTodayDateString()
        
        // Check daily count
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        val todayCount = if (lastDate == today) {
            prefs.getInt(KEY_TODAY_COUNT, 0)
        } else {
            prefs.edit().putInt(KEY_TODAY_COUNT, 0).apply()
            0
        }
        
        if (todayCount >= MAX_PER_DAY) {
            AppLogger.d("FrequencyCap", "Daily limit reached ($todayCount/$MAX_PER_DAY)")
            return false
        }
        
        // Check minimum interval
        val lastShown = prefs.getLong(KEY_LAST_SHOWN, 0)
        if (lastShown > 0) {
            val hoursSinceLastShown = (now - lastShown) / (1000 * 60 * 60)
            if (hoursSinceLastShown < MIN_INTERVAL_HOURS) {
                AppLogger.d("FrequencyCap", "Minimum interval not reached (${hoursSinceLastShown}h/${MIN_INTERVAL_HOURS}h)")
                return false
            }
        }
        
        return true
    }
    
    fun recordNotificationShown() {
        val now = System.currentTimeMillis()
        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        
        val editor = prefs.edit()
        editor.putLong(KEY_LAST_SHOWN, now)
        
        if (lastDate == today) {
            val count = prefs.getInt(KEY_TODAY_COUNT, 0) + 1
            editor.putInt(KEY_TODAY_COUNT, count)
        } else {
            editor.putInt(KEY_TODAY_COUNT, 1)
            editor.putString(KEY_LAST_DATE, today)
        }
        
        editor.apply()
        AppLogger.d("FrequencyCap", "Recorded notification shown (today: ${prefs.getInt(KEY_TODAY_COUNT, 0)})")
    }
    
    private fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}-${calendar.get(Calendar.DAY_OF_MONTH)}"
    }
    
    fun reset() {
        prefs.edit().clear().apply()
        AppLogger.d("FrequencyCap", "Reset")
    }
}
```

---

#### 2.6. NotificationScheduler.kt

**File:** `app/src/main/java/com/yourname/smartrecorder/core/notification/NotificationScheduler.kt`

```kotlin
package com.yourname.smartrecorder.core.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.notification.worker.NotificationWorker
import com.yourname.smartrecorder.data.local.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val settingsStore: SettingsStore
) {
    companion object {
        private const val WORK_NAME_DAILY = "notification_daily_app"
    }
    
    suspend fun scheduleDailyNotifications() {
        workManager.cancelUniqueWork(WORK_NAME_DAILY)
        
        val enabled = settingsStore.notificationsEnabled.first()
        if (!enabled) {
            AppLogger.d("NotificationScheduler", "Notifications disabled - skipping schedule")
            return
        }
        
        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            24, TimeUnit.HOURS,
            1, TimeUnit.HOURS
        )
            .addTag("notification_daily")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME_DAILY,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        
        AppLogger.d("NotificationScheduler", "Daily notifications scheduled")
    }
    
    fun cancelAllNotifications() {
        workManager.cancelUniqueWork(WORK_NAME_DAILY)
        AppLogger.d("NotificationScheduler", "All notifications cancelled")
    }
}
```

---

#### 2.7. NotificationWorker.kt

**File:** `app/src/main/java/com/yourname/smartrecorder/core/notification/worker/NotificationWorker.kt`

```kotlin
package com.yourname.smartrecorder.core.notification.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.notification.AppNotificationManager
import com.yourname.smartrecorder.core.notification.NotificationContent
import com.yourname.smartrecorder.core.notification.NotificationFrequencyCap
import com.yourname.smartrecorder.data.local.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.Calendar

@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationManager: AppNotificationManager,
    private val settingsStore: SettingsStore,
    private val frequencyCap: NotificationFrequencyCap
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            AppLogger.d("NotificationWorker", "Starting work")
            
            if (!shouldShowNotification()) {
                AppLogger.d("NotificationWorker", "Should not show - skipping")
                return Result.success()
            }
            
            if (!frequencyCap.canShowNotification()) {
                AppLogger.d("NotificationWorker", "Frequency cap - skipping")
                return Result.success()
            }
            
            val message = NotificationContent.getRandomMessage()
            
            notificationManager.showAppContentNotification(
                title = message.title,
                content = message.content,
                deepLink = message.deepLink
            )
            
            frequencyCap.recordNotificationShown()
            
            AppLogger.d("NotificationWorker", "Success - shown '${message.title}'")
            Result.success()
        } catch (e: Exception) {
            AppLogger.e("NotificationWorker", "Error", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    private suspend fun shouldShowNotification(): Boolean {
        val notificationManagerCompat = androidx.core.app.NotificationManagerCompat.from(applicationContext)
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            AppLogger.d("NotificationWorker", "System notifications disabled")
            return false
        }
        
        val enabled = settingsStore.notificationsEnabled.first()
        if (!enabled) {
            AppLogger.d("NotificationWorker", "User disabled notifications")
            return false
        }
        
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 22 || hour < 7) {
            AppLogger.d("NotificationWorker", "Night time - skipping (hour: $hour)")
            return false
        }
        
        return true
    }
}
```

---

### Phase 3: UI Integration

#### 3.1. Settings Screen - Notification Toggle ✅ COMPLETED

**File:** `app/src/main/java/com/yourname/smartrecorder/ui/screens/SettingsScreen.kt`

**Status:** ✅ COMPLETED

**Đã triển khai:**
1. ✅ Notification toggle với system state sync
2. ✅ Warning card khi notifications disabled
3. ✅ Permission request dialog khi toggle ON từ disabled
4. ✅ Open system settings khi toggle OFF
5. ✅ Refresh state khi user quay lại từ system settings

**Implementation:**
```kotlin
// SettingsViewModel.kt
fun onNotificationToggleChanged(wantsToEnable: Boolean, context: Context) {
    viewModelScope.launch {
        val currentSystemValue = notificationPermissionManager.areNotificationsEnabled(context)
        
        if (wantsToEnable) {
            if (!currentSystemValue) {
                // Request permission dialog (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    _eventFlow.emit(SettingsEvent.RequestNotificationPermission)
                } else {
                    // Android < 13: Open system settings
                    _eventFlow.emit(SettingsEvent.OpenSystemSettings)
                }
            } else {
                // Already enabled, just update UI
                _systemNotificationAllowed.value = true
            }
        } else {
            // Toggle OFF → Open system settings
            _eventFlow.emit(SettingsEvent.OpenSystemSettings)
        }
    }
}

// SettingsScreen.kt
Switch(
    checked = uiState.notificationsEnabled,
    onCheckedChange = { viewModel.onNotificationToggleChanged(it, context) }
)

// Warning card when disabled
if (!uiState.notificationsEnabled) {
    Card(/* warning card with open settings button */)
}
```

**Key Features:**
- ✅ System state as single source of truth
- ✅ Permission request dialog khi toggle ON từ disabled
- ✅ Warning card hướng dẫn user enable notifications
- ✅ Lifecycle-aware refresh (repeatOnLifecycle)
- ✅ Retry logic cho Samsung/Xiaomi delay

---

#### 3.2. Handle Deep Links trong MainActivity

**File:** `app/src/main/java/com/yourname/smartrecorder/MainActivity.kt`

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ... existing code ...
    handleNotificationDeepLink(intent)
}

override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleNotificationDeepLink(intent)
}

private fun handleNotificationDeepLink(intent: Intent?) {
    val route = intent?.getStringExtra("notification_route") ?: return
    
    lifecycleScope.launch {
        delay(300) // Wait for Compose to initialize
        // Navigate via navController
        // Implementation depends on navigation setup
    }
}
```

---

#### 3.3. Handle Service Actions trong RecordViewModel

**File:** `app/src/main/java/com/yourname/smartrecorder/ui/record/RecordViewModel.kt`

Thêm BroadcastReceiver để handle actions từ notification:
```kotlin
private val serviceActionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            RecordingForegroundService.ACTION_PAUSE -> onPauseClick()
            RecordingForegroundService.ACTION_RESUME -> onResumeClick()
            RecordingForegroundService.ACTION_STOP -> onStopClick()
        }
    }
}

init {
    // Register receiver
    val filter = IntentFilter().apply {
        addAction(RecordingForegroundService.ACTION_PAUSE)
        addAction(RecordingForegroundService.ACTION_RESUME)
        addAction(RecordingForegroundService.ACTION_STOP)
    }
    context.registerReceiver(serviceActionReceiver, filter)
}

override fun onCleared() {
    super.onCleared()
    context.unregisterReceiver(serviceActionReceiver)
}
```

---

## 📦 DEPENDENCIES

**File:** `app/build.gradle.kts`

```kotlin
dependencies {
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Hilt for WorkManager
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")
    
    // Media3 for MediaSession (optional, for better media controls)
    implementation("androidx.media3:media3-session:1.2.0")
}
```

---

## ✅ CHECKLIST TRIỂN KHAI

### Phase 1: Cải thiện Foreground Service Notifications
- [ ] Cải thiện `RecordingForegroundService`:
  - [ ] Thêm ACTION_PAUSE, ACTION_RESUME
  - [ ] Cải thiện notification với action buttons
  - [ ] Set visibility PUBLIC cho lock screen
  - [ ] Set priority HIGH
  - [ ] Thêm BroadcastReceiver hoặc callback để giao tiếp với ViewModel
- [ ] Cải thiện `PlaybackForegroundService`:
  - [ ] Sử dụng MediaStyle notification
  - [ ] Thêm MediaSession
  - [ ] Cải thiện media controls
- [ ] Test pause/resume/stop từ notification và lock screen

### Phase 2: App Content Notifications
- [ ] Tạo `NotificationChannelManager` (3 channels)
- [ ] Tạo `NotificationContent` với messages phù hợp
- [ ] Tạo `NotificationDeepLinkHandler`
- [ ] Tạo `AppNotificationManager`
- [ ] Tạo `NotificationFrequencyCap`
- [ ] Tạo `NotificationScheduler`
- [ ] Tạo `NotificationWorker`
- [ ] Cấu hình Hilt WorkManager trong `AppModule` và `AppApplication`

### Phase 3: UI Integration
- [ ] Thêm notification toggle vào `SettingsScreen`
- [ ] Handle deep links trong `MainActivity`
- [ ] Handle service actions trong `RecordViewModel`
- [ ] Test deep link navigation

#### 3.2. Onboarding Screen - Notification Permission ✅ COMPLETED

**File:** `app/src/main/java/com/yourname/smartrecorder/ui/onboarding/OnboardingScreen.kt`

**Status:** ✅ COMPLETED

**Đã triển khai:**
1. ✅ Check permission state từ system trước khi request
2. ✅ Request permission ở page 2 (Notifications)
3. ✅ Auto-navigate sau khi permission granted/denied
4. ✅ Sync với NotificationPermissionManager
5. ✅ Handle Android < 13 (notifications enabled by default)

**Implementation:**
```kotlin
// Check system permission state
val notificationPermissionManager = NotificationPermissionManager()

LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasNotificationPermission = notificationPermissionManager.areNotificationsEnabled(context)
    } else {
        hasNotificationPermission = true // Android < 13
    }
}

// Permission launcher
val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { isGranted ->
    hasNotificationPermission = isGranted
    if (isGranted) {
        viewModel.enableNotifications()
    }
    // Refresh system state
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        delay(150)
        val actualState = notificationPermissionManager.areNotificationsEnabled(context)
        hasNotificationPermission = actualState
    }
    // Auto-navigate to next page
    coroutineScope.launch {
        pagerState.animateScrollToPage(3)
    }
}

// Page 2 Next button
if (currentPage == 2) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val currentSystemState = notificationPermissionManager.areNotificationsEnabled(context)
        if (!currentSystemState && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Already granted, navigate
            coroutineScope.launch {
                pagerState.animateScrollToPage(3)
            }
        }
    }
}
```

**Key Features:**
- ✅ Check system state trước khi request (tránh request nhiều lần)
- ✅ Auto-navigate sau khi xử lý permission
- ✅ Sync với system state sau khi permission granted
- ✅ Handle Android version differences

### Phase 4: Testing
- [ ] Test recording notification với pause/resume/stop
- [ ] Test playback notification với media controls
- [ ] Test lock screen controls
- [x] Test với permission granted/denied ✅ (Settings & Onboarding)
- [ ] Test frequency cap
- [ ] Test worker schedule
- [ ] Test deep links
- [ ] Test với app killed/background

---

## 🧪 TESTING

### Test Cases

1. **Recording Notification:**
   - Start recording → notification xuất hiện với "Recording - 00:00:00"
   - Tap Pause → recording pause, notification update thành "Paused"
   - Tap Resume → recording resume, notification update thành "Recording"
   - Tap Stop → recording stop, notification dismiss
   - Lock screen → controls hiển thị đúng

2. **Playback Notification:**
   - Start playback → MediaStyle notification xuất hiện
   - Tap Play/Pause → playback toggle
   - Tap Stop → playback stop
   - Lock screen → media controls hiển thị

3. **App Content Notification:**
   - Daily notification xuất hiện đúng giờ (7:00 - 22:00)
   - Frequency cap hoạt động (max 3/ngày, min 4h interval)
   - Tap notification → navigate đúng route
   - Toggle off → notifications dừng

---

## 📝 NOTES

### Best Practices

1. **Permission**: Luôn check `areNotificationsEnabled()` và `POST_NOTIFICATIONS`
2. **Graceful degradation**: Try-catch mọi operation, không throw
3. **Logging**: Log đầy đủ để debug (AppLogger)
4. **Frequency cap**: Tránh spam, respect user
5. **Deep links**: Routes phải khớp với NavGraph
6. **Lock screen**: Set visibility PUBLIC và priority HIGH cho recording
7. **Media controls**: Sử dụng MediaStyle và MediaSession cho playback

### Limitations Phase 1

- Chỉ 1 notification/ngày cho app content
- Không có support/ads notifications
- Không có premium content
- Không có action buttons phức tạp (chỉ Pause/Resume/Stop)
- 3 channels duy nhất

### Future (Phase 2)

- Thêm waveform preview trong expandable notification
- Thêm progress bar cho playback (Android 10+)
- Thêm bookmark action trong recording notification
- Multiple notification channels cho các loại content khác nhau
- Rich notifications với images

---

## 🎯 KẾT LUẬN

Phase 1 tập trung vào:
- ✅ **Điều khiển từ xa**: Pause/Resume/Stop recording từ lock screen
- ✅ **Media controls**: Playback notification chuẩn Android
- ✅ **Promotional**: Daily notifications để khích lệ sử dụng app
- ✅ **Production-ready**: Xử lý lỗi đầy đủ, logging chi tiết
- ✅ **User-friendly**: Không spam, tôn trọng user preference

**Thời gian ước tính:** 4-5 ngày

---

**Sẵn sàng để ship!** 🚀

