# 🎯 KẾ HOẠCH THÔNG BÁO ĐƠN GIẢN - PHASE 1
## (Simplified Notification System - Phase 1)

**Mục tiêu:** Hệ thống thông báo cơ bản, sạch, production-ready cho app Thần Số Học.

**Nguyên tắc:**
- ✅ **Đơn giản**: Chỉ app content, không ads/premium
- ✅ **Ổn định**: Ít bug, dễ test
- ✅ **Production-ready**: Xử lý lỗi đầy đủ
- ✅ **Dễ maintain**: Code rõ ràng, tách lớp hợp lý

---

## 📋 TỔNG QUAN

### Scope Phase 1

✅ **Có:**
- System notifications (notification bar + lock screen)
- 1 notification/ngày - nội dung app content
- WorkManager scheduled daily
- Frequency cap (3/ngày, 4h interval)
- Deep links cơ bản (home, chat, result)
- Settings toggle đơn giản
- Permission handling an toàn

❌ **Tạm bỏ:**
- Support/Ads notifications
- Premium content notifications
- Action buttons phức tạp
- Nhiều channels
- Logic premium-only

---

## 🏗️ KIẾN TRÚC ĐƠN GIẢN

```
notification/
├── AppNotificationManager.kt      # Core manager (đổi tên từ NotificationManager)
├── NotificationChannelManager.kt  # 1 channel duy nhất
├── NotificationContent.kt         # App content messages only
├── NotificationScheduler.kt       # WorkManager scheduler
├── NotificationFrequencyCap.kt    # Tránh spam
├── worker/
│   └── NotificationWorker.kt      # Background worker
└── NotificationDeepLinkHandler.kt # Deep link (đơn giản)
```

**Tổng số files:** 7 files (thay vì 10+)

---

## 📝 YÊU CẦU PHASE 1

### 1. Nội dung thông báo

**Chỉ dùng:** App Content Notifications
- Khích lệ sử dụng app
- Giới thiệu tính năng
- Tips & mẹo
- Tone: **Giá trị, chăm sóc** - KHÔNG bán hàng

**Ví dụ:**
- "🌟 Khám phá bản thân qua Thần Số Học - Tính toán để hiểu rõ hơn về tính cách và tiềm năng!"
- "💬 Sử dụng tính năng Chat để tương tác bằng giọng nói với ứng dụng"
- "📚 Tra cứu từ điển để hiểu rõ các thuật ngữ thần số học"

### 2. Tần suất

- **1 notification/ngày** (đơn giản hóa từ "1-2/ngày")
- Worker chạy **mỗi 24 giờ**
- Frequency cap: Tối đa **3/ngày**, tối thiểu **4h** giữa các lần
- Không gửi: **22:00 - 7:00**

### 3. Channels

**Chỉ 1 channel:**
- ID: `numerology_app`
- Name: "Thông báo Thần Số Học"
- Importance: **HIGH** (hiển thị lock screen)
- Visibility: **PUBLIC** (user có thể thay đổi trong settings)

---

## 🔧 TRIỂN KHAI

### 1. AppNotificationManager (Core)

**File:** `app/src/main/java/com/app/numerology/notification/AppNotificationManager.kt`

```kotlin
package com.app.numerology.notification

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.app.numerology.core.utils.AppLogger
import com.app.numerology.data.store.SettingsStore
import kotlinx.coroutines.flow.first

@Singleton
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelManager: NotificationChannelManager,
    private val settingsStore: SettingsStore,
    private val deepLinkHandler: NotificationDeepLinkHandler
) {
    private val notificationManager = NotificationManagerCompat.from(context)
    private var notificationIdCounter = 1000
    
    companion object {
        private const val NOTIFICATION_ID_APP_CONTENT = 1001 // Fixed ID để update nếu cần
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
            AppLogger.log("Notification: System permission disabled")
            return
        }
        
        // 2. Check Android 13+ permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                AppLogger.log("Notification: POST_NOTIFICATIONS not granted")
                return
            }
        }
        
        // 3. Check user preference
        val enabled = settingsStore.notificationsEnabled.first()
        if (!enabled) {
            AppLogger.log("Notification: Disabled by user preference")
            return
        }
        
        // 4. Build PendingIntent
        val pendingIntent = deepLink?.let { link ->
            deepLinkHandler.createPendingIntent(link)
        } ?: createMainActivityIntent()
        
        // 5. Build notification
        val notification = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_APP_CONTENT)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Custom icon
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Lock screen
            .setShowWhen(true)
            .build()
        
        // 6. Show notification
        try {
            notificationManager.notify(NOTIFICATION_ID_APP_CONTENT, notification)
            AppLogger.log("Notification shown: $title")
        } catch (e: SecurityException) {
            AppLogger.logError("Notification permission denied", e)
        } catch (e: Exception) {
            AppLogger.logError("Failed to show notification", e)
            // Don't throw - graceful degradation
        }
    }
    
    private fun createMainActivityIntent(): android.app.PendingIntent {
        val intent = android.content.Intent(context, com.app.numerology.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }
}
```

### 2. NotificationChannelManager (Đơn giản)

**File:** `app/src/main/java/com/app/numerology/notification/NotificationChannelManager.kt`

```kotlin
package com.app.numerology.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class NotificationChannelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_APP_CONTENT = "numerology_app"
    }
    
    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Check if channel already exists
            if (manager.getNotificationChannel(CHANNEL_APP_CONTENT) == null) {
                val channel = NotificationChannel(
                    CHANNEL_APP_CONTENT,
                    "Thông báo Thần Số Học",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo khích lệ và mẹo sử dụng ứng dụng"
                    enableVibration(true)
                    enableLights(true)
                    lockscreenVisibility = NotificationChannel.VISIBILITY_PUBLIC
                }
                
                manager.createNotificationChannel(channel)
            }
        }
    }
}
```

### 3. NotificationContent (Chỉ App Content)

**File:** `app/src/main/java/com/app/numerology/notification/NotificationContent.kt`

```kotlin
package com.app.numerology.notification

import kotlin.random.Random

object NotificationContent {
    private val random = Random(System.currentTimeMillis())
    
    data class NotificationMessage(
        val title: String,
        val content: String,
        val deepLink: String? = null // Optional deep link route
    )
    
    private val appContentMessages = listOf(
        NotificationMessage(
            title = "🌟 Khám phá bản thân",
            content = "Tính toán thần số học để hiểu rõ hơn về tính cách, sứ mệnh và tiềm năng của bạn!",
            deepLink = "home"
        ),
        NotificationMessage(
            title = "💬 Trò chuyện với app",
            content = "Sử dụng tính năng Chat để tương tác bằng giọng nói với ứng dụng. Hỏi bất cứ điều gì về thần số học!",
            deepLink = "chat"
        ),
        NotificationMessage(
            title = "📚 Tra cứu từ điển",
            content = "Từ điển Thần Số Học giúp bạn hiểu rõ các thuật ngữ và ý nghĩa các con số. Khám phá ngay!",
            deepLink = "dict"
        ),
        NotificationMessage(
            title = "💫 Con số của bạn",
            content = "Bạn đã biết Con số Chủ đạo của mình chưa? Tính ngay để hiểu rõ hơn về bản thân!",
            deepLink = "home"
        ),
        NotificationMessage(
            title = "✨ Thần Số Học",
            content = "Thần số học là môn khoa học nghiên cứu ý nghĩa và ảnh hưởng của các con số đến cuộc đời mỗi người.",
            deepLink = "intro"
        ),
        NotificationMessage(
            title = "🎯 Sứ mệnh cuộc đời",
            content = "Con số Sứ mệnh tiết lộ mục đích sống của bạn. Khám phá ngay trong app!",
            deepLink = "home"
        ),
        NotificationMessage(
            title = "📊 Tính toán cho người thân",
            content = "Lưu thông tin của những người thân yêu để xem lại thần số học của họ bất cứ lúc nào!",
            deepLink = "saved"
        )
    )
    
    fun getRandomMessage(): NotificationMessage {
        return appContentMessages[random.nextInt(appContentMessages.size)]
    }
    
    fun getAllMessages(): List<NotificationMessage> = appContentMessages
}
```

### 4. NotificationDeepLinkHandler (Đơn giản)

**File:** `app/src/main/java/com/app/numerology/notification/NotificationDeepLinkHandler.kt`

```kotlin
package com.app.numerology.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.app.numerology.MainActivity

/**
 * Routes constants - PHẢI KHỚP với NavGraph.kt
 */
object NotificationRoutes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val DICT = "dict"
    const val INTRO = "intro"
    const val SETTINGS = "settings"
    const val SAVED = "saved"
    
    // Result route format: "result/{encodedName}/{encodedDob}"
    fun result(name: String, dob: String): String {
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        val encodedDob = java.net.URLEncoder.encode(dob, "UTF-8")
        return "result/$encodedName/$encodedDob"
    }
}

@Singleton
class NotificationDeepLinkHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Create PendingIntent for deep link navigation
     * Route format: "home", "chat", "result/{name}/{dob}", etc.
     */
    fun createPendingIntent(route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Add route data
            putExtra("notification_route", route)
        }
        
        return PendingIntent.getActivity(
            context,
            route.hashCode(), // Unique request code
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
```

### 5. NotificationFrequencyCap

**File:** `app/src/main/java/com/app/numerology/notification/NotificationFrequencyCap.kt`

```kotlin
package com.app.numerology.notification

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.app.numerology.core.utils.AppLogger
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
    
    /**
     * Check if can show notification based on frequency cap
     */
    fun canShowNotification(): Boolean {
        val now = System.currentTimeMillis()
        val today = getTodayDateString()
        
        // Check daily count
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        val todayCount = if (lastDate == today) {
            prefs.getInt(KEY_TODAY_COUNT, 0)
        } else {
            // Reset count for new day
            prefs.edit().putInt(KEY_TODAY_COUNT, 0).apply()
            0
        }
        
        if (todayCount >= MAX_PER_DAY) {
            AppLogger.log("FrequencyCap: Daily limit reached ($todayCount/$MAX_PER_DAY)")
            return false
        }
        
        // Check minimum interval
        val lastShown = prefs.getLong(KEY_LAST_SHOWN, 0)
        if (lastShown > 0) {
            val hoursSinceLastShown = (now - lastShown) / (1000 * 60 * 60)
            if (hoursSinceLastShown < MIN_INTERVAL_HOURS) {
                AppLogger.log("FrequencyCap: Minimum interval not reached (${hoursSinceLastShown}h/${MIN_INTERVAL_HOURS}h)")
                return false
            }
        }
        
        return true
    }
    
    /**
     * Record that a notification was shown
     */
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
        AppLogger.log("FrequencyCap: Recorded notification shown (today: ${prefs.getInt(KEY_TODAY_COUNT, 0)})")
    }
    
    private fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}-${calendar.get(Calendar.DAY_OF_MONTH)}"
    }
    
    /**
     * Reset frequency cap (for testing)
     */
    fun reset() {
        prefs.edit().clear().apply()
        AppLogger.log("FrequencyCap: Reset")
    }
}
```

### 6. NotificationScheduler (WorkManager)

**File:** `app/src/main/java/com/app/numerology/notification/NotificationScheduler.kt`

```kotlin
package com.app.numerology.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.app.numerology.core.utils.AppLogger
import com.app.numerology.notification.worker.NotificationWorker
import kotlinx.coroutines.flow.first
import com.app.numerology.data.store.SettingsStore
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
    
    /**
     * Schedule daily notification worker
     */
    suspend fun scheduleDailyNotifications() {
        // Cancel existing work
        workManager.cancelUniqueWork(WORK_NAME_DAILY)
        
        // Check if notifications enabled
        val enabled = settingsStore.notificationsEnabled.first()
        if (!enabled) {
            AppLogger.log("NotificationScheduler: Notifications disabled - skipping schedule")
            return
        }
        
        // Schedule periodic work (daily, 24h interval, flex 1h)
        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            24, TimeUnit.HOURS,
            1, TimeUnit.HOURS // Flex interval for battery optimization
        )
            .addTag("notification_daily")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME_DAILY,
            ExistingPeriodicWorkPolicy.UPDATE, // Update if exists
            workRequest
        )
        
        AppLogger.log("NotificationScheduler: Daily notifications scheduled")
    }
    
    /**
     * Cancel all scheduled notifications
     */
    fun cancelAllNotifications() {
        workManager.cancelUniqueWork(WORK_NAME_DAILY)
        AppLogger.log("NotificationScheduler: All notifications cancelled")
    }
}
```

### 7. NotificationWorker

**File:** `app/src/main/java/com/app/numerology/notification/worker/NotificationWorker.kt`

```kotlin
package com.app.numerology.notification.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.app.numerology.core.utils.AppLogger
import com.app.numerology.data.store.SettingsStore
import com.app.numerology.notification.AppNotificationManager
import com.app.numerology.notification.NotificationContent
import com.app.numerology.notification.NotificationFrequencyCap
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
            AppLogger.log("NotificationWorker: Starting work")
            
            // 1. Check if should show notification
            if (!shouldShowNotification()) {
                AppLogger.log("NotificationWorker: Should not show - skipping")
                return Result.success()
            }
            
            // 2. Check frequency cap
            if (!frequencyCap.canShowNotification()) {
                AppLogger.log("NotificationWorker: Frequency cap - skipping")
                return Result.success()
            }
            
            // 3. Get notification content
            val message = NotificationContent.getRandomMessage()
            
            // 4. Show notification
            notificationManager.showAppContentNotification(
                title = message.title,
                content = message.content,
                deepLink = message.deepLink
            )
            
            // 5. Record in frequency cap
            frequencyCap.recordNotificationShown()
            
            AppLogger.log("NotificationWorker: Success - shown '${message.title}'")
            Result.success()
        } catch (e: Exception) {
            AppLogger.logError("NotificationWorker: Error", e)
            // Retry with exponential backoff (WorkManager handles this)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    private suspend fun shouldShowNotification(): Boolean {
        // Check permission
        val notificationManagerCompat = androidx.core.app.NotificationManagerCompat.from(applicationContext)
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            AppLogger.log("NotificationWorker: System notifications disabled")
            return false
        }
        
        // Check user preference
        val enabled = settingsStore.notificationsEnabled.first()
        if (!enabled) {
            AppLogger.log("NotificationWorker: User disabled notifications")
            return false
        }
        
        // Check time (don't show at night: 22:00 - 7:00)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 22 || hour < 7) {
            AppLogger.log("NotificationWorker: Night time - skipping (hour: $hour)")
            return false
        }
        
        return true
    }
}
```

### 8. Worker Factory (Hilt)

**File:** `app/src/main/java/com/app/numerology/di/AppModule.kt` (thêm)

```kotlin
// Add to existing AppModule.kt

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration

@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {
    @Provides
    @Singleton
    fun provideWorkManagerConfiguration(
        workerFactory: HiltWorkerFactory
    ): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
```

**File:** `app/src/main/java/com/app/numerology/AppApplication.kt` (cập nhật)

```kotlin
// Add to AppApplication.kt

@HiltAndroidApp
class AppApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
```

---

## 🖥️ UI INTEGRATION

### 1. Settings Screen - Notification Toggle

**File:** `app/src/main/java/com/app/numerology/ui/settings/SettingScreen.kt`

Thêm vào section Settings:

```kotlin
// Trong SettingScreen.kt, thêm vào SettingSection

// Section: Thông báo
SettingSection(
    title = "Thông báo",
    icon = Icons.Default.Notifications
) {
    SwitchPreferenceItem(
        title = "Cho phép thông báo",
        description = "Nhận thông báo khích lệ và nhắc dùng app (1 lần/ngày)",
        checked = notificationsEnabled,
        onCheckedChange = { enabled ->
            // Update setting
            viewModel.setNotificationsEnabled(enabled)
            // Schedule/cancel notifications
            if (enabled) {
                notificationScheduler.scheduleDailyNotifications()
            } else {
                notificationScheduler.cancelAllNotifications()
            }
        }
    )
    
    if (!notificationsEnabled) {
        InfoItem(
            title = "Thông báo đã tắt",
            description = "Bật lại để nhận lời nhắc và mẹo sử dụng ứng dụng"
        )
    }
}
```

### 2. Handle Deep Links trong MainActivity

**File:** `app/src/main/java/com/app/numerology/MainActivity.kt`

```kotlin
// Trong MainActivity.onCreate() hoặc onNewIntent()

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ... existing code ...
    
    // Handle notification deep link
    handleNotificationDeepLink(intent)
}

override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleNotificationDeepLink(intent)
}

private fun handleNotificationDeepLink(intent: Intent?) {
    val route = intent?.getStringExtra("notification_route") ?: return
    
    // Navigate after activity created
    lifecycleScope.launch {
        delay(300) // Wait for Compose to initialize
        // Navigate via navController (get from MainScreen)
        // Implementation depends on your navigation setup
    }
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
}
```

---

## ✅ CHECKLIST TRIỂN KHAI

### Phase 1: Foundation
- [ ] Thêm dependencies (WorkManager, Hilt Work)
- [ ] Đổi tên `NotificationManager` → `AppNotificationManager`
- [ ] Tạo `NotificationChannelManager` (1 channel)
- [ ] Tạo `NotificationDeepLinkHandler` với routes constants
- [ ] Tạo `NotificationFrequencyCap`
- [ ] Cập nhật `NotificationContent` (chỉ app content)

### Phase 2: Scheduling
- [ ] Tạo `NotificationScheduler`
- [ ] Tạo `NotificationWorker`
- [ ] Cấu hình Hilt WorkManager trong `AppModule` và `AppApplication`

### Phase 3: UI Integration
- [ ] Thêm notification toggle vào `SettingScreen`
- [ ] Handle deep links trong `MainActivity`
- [ ] Test deep link navigation

### Phase 4: Testing
- [ ] Test với permission granted/denied
- [ ] Test lock screen visibility
- [ ] Test frequency cap
- [ ] Test worker schedule
- [ ] Test deep links
- [ ] Test với app killed/background

---

## 🧪 TESTING

### Debug Screen (Optional - cho testing nhanh)

**File:** `app/src/main/java/com/app/numerology/ui/settings/NotificationTestScreen.kt`

```kotlin
@Composable
fun NotificationTestScreen(
    notificationManager: AppNotificationManager = hiltViewModel(),
    frequencyCap: NotificationFrequencyCap = hiltViewModel()
) {
    Column {
        Button(onClick = {
            lifecycleScope.launch {
                val msg = NotificationContent.getRandomMessage()
                notificationManager.showAppContentNotification(
                    msg.title,
                    msg.content,
                    msg.deepLink
                )
            }
        }) {
            Text("Gửi thử Notification")
        }
        
        Button(onClick = {
            frequencyCap.reset()
        }) {
            Text("Reset Frequency Cap")
        }
    }
}
```

---

## 📝 NOTES

### Best Practices

1. **Permission**: Luôn check `areNotificationsEnabled()` và `POST_NOTIFICATIONS`
2. **Graceful degradation**: Try-catch mọi operation, không throw
3. **Logging**: Log đầy đủ để debug (AppLogger)
4. **Frequency cap**: Tránh spam, respect user
5. **Deep links**: Routes phải khớp với NavGraph

### Limitations Phase 1

- Chỉ 1 notification/ngày (không phải 1-2)
- Không có support/ads notifications
- Không có premium content
- Không có action buttons
- 1 channel duy nhất

### Future (Phase 2)

Khi app ổn định và có Premium:
- Thêm support notifications (OPT-IN riêng)
- Thêm premium content
- Multiple channels
- Action buttons

---

## 🎯 KẾT LUẬN

Phase 1 tập trung vào:
- ✅ **Đơn giản**: Dễ implement, ít bug
- ✅ **Ổn định**: Production-ready
- ✅ **User-friendly**: Chỉ nội dung giá trị, không spam
- ✅ **Dễ maintain**: Code rõ ràng, tách lớp hợp lý

**Thời gian ước tính:** 3-4 ngày (thay vì 10 ngày)

---

**Sẵn sàng để ship!** 🚀

