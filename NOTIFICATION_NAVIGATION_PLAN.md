# Kế Hoạch Xử Lý Navigation Từ Notification/Media Controls

## 📋 Tổng Quan

Khi user ẩn app và có service chạy (recording hoặc playback), notification/media controls sẽ hiển thị. Khi user tap vào notification, app cần navigate đến đúng màn hình tương ứng với context hiện tại.

---

## 🔒 Core Rules - Nguyên Tắc Cốt Lõi

**Các nguyên tắc bắt buộc phải tuân theo:**

1. **Service không tự quyết UI** - Service chỉ cung cấp:
   - `recordingId` (nếu có)
   - `route` (string): `"transcript_detail/{id}"` hoặc `"record"`

2. **Notification luôn đính route cụ thể** - Mọi notification phải có:
   - Extra `"notification_route" = route`
   - PendingIntent dùng `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`

3. **Activity/App là nơi duy nhất điều hướng** - Chỉ có:
   - `MainActivity` nhận `notification_route` trong `onCreate` + `onNewIntent`
   - Đẩy route vào `StateFlow<String?>`
   - `SmartRecorderApp` lắng nghe và `navController.navigate(route)` **1 lần duy nhất**

4. **Fallback rules:**
   - Service không có `recordingId` → route = `RECORD` + log rare condition
   - Invalid recording/file deleted → TranscriptViewModel show error + navigateBack + log rare condition

5. **LibraryScreen:**
   - Không start foreground service
   - Chỉ TranscriptScreen control playback / notification

**→ Tất cả implementation details dưới đây chỉ là cách thực hiện các rules trên.**

---

## 🎯 Các Trường Hợp Cần Xử Lý

### 1. **TranscriptScreen + Playback** ✅ (Đã xác định vấn đề)

**Hiện trạng:**
- User ở `TranscriptScreen` với `recordingId` cụ thể
- User play audio → `TranscriptViewModel` gọi `startPlaybackService(title, duration)`
- User ẩn app → `PlaybackForegroundService` tạo notification
- User tap notification → App mở về `RECORD` screen (sai)

**Logic mới:**
- `TranscriptViewModel` truyền `recordingId` khi gọi `startPlaybackService()`
- `PlaybackForegroundService` lưu `recordingId` và tạo PendingIntent với route `transcript_detail/{recordingId}`
- Tap notification → Navigate đến `TranscriptScreen` với `recordingId` đúng

**Destination:** `transcript_detail/{recordingId}`

---

### 2. **LibraryScreen (HistoryScreen) + Playback**

**Hiện trạng:**
- `LibraryViewModel` KHÔNG sử dụng `PlaybackForegroundService` khi play
- Playback chỉ local, không có notification
- Nhưng có thể có trường hợp:
  - User play từ `LibraryScreen` → không có service
  - User vào `TranscriptScreen` từ recording đó → service được start
  - User ẩn app → có notification
  - Tap notification → về đâu?

**Logic mới:**
- Nếu `LibraryViewModel` muốn có notification khi play (tùy chọn):
  - Cần gọi `startPlaybackService(recordingId, title, duration)` từ `LibraryViewModel`
  - Tạo PendingIntent với route `transcript_detail/{recordingId}` (vì user có thể muốn xem transcript)
- Nếu không muốn notification từ LibraryScreen:
  - Chỉ khi vào `TranscriptScreen` mới có notification
  - Tap notification luôn về `TranscriptScreen` (đúng vì service được start từ đó)

**Destination:** `transcript_detail/{recordingId}` (nếu có notification)

**Quyết định:** 
- **Option A (Recommended):** Không thay đổi LibraryScreen, chỉ fix TranscriptScreen. Notification chỉ xuất hiện khi play từ TranscriptScreen.
- **Option B:** Thêm foreground service cho LibraryScreen playback → notification → về TranscriptScreen.

---

### 3. **RecordScreen + Recording**

**Hiện trạng:**
- User ở `RecordScreen` đang recording
- `RecordViewModel` gọi `startRecordingService(recordingId, fileName)`
- User ẩn app → `RecordingForegroundService` tạo notification
- User tap notification → App mở về `RECORD` screen (có thể đúng, nhưng cần verify)

**Logic mới:**
- `RecordingForegroundService` đã có `recordingId` từ Intent
- Tạo PendingIntent với route `record` (vì recording chỉ xảy ra ở RecordScreen)
- Tap notification → Navigate về `RecordScreen`

**Destination:** `record`

**Note:** Recording chỉ xảy ra ở RecordScreen, nên luôn về đó là đúng.

---

## 🔧 Implementation Plan

### Phase 1: Fix PlaybackForegroundService (TranscriptScreen)

#### 1.1. Update `ForegroundServiceManager.kt`

**Thay đổi:**
```kotlin
// Thêm recordingId parameter
fun startPlaybackService(recordingId: String, title: String, duration: Long) {
    val intent = PlaybackForegroundService.createIntent(context).apply {
        putExtra("recordingId", recordingId)  // ← Thêm
        putExtra("title", title)
        putExtra("duration", duration)
    }
    ContextCompat.startForegroundService(context, intent)
}

// Thêm recordingId khi update
fun updatePlaybackNotification(recordingId: String, position: Long, duration: Long, isPaused: Boolean = false) {
    val intent = PlaybackForegroundService.createIntent(context).apply {
        putExtra("recordingId", recordingId)  // ← Thêm
        putExtra("position", position)
        putExtra("duration", duration)
        putExtra("isPaused", isPaused)
    }
    // ⚠️ CRITICAL: Chỉ dùng startForegroundService cho lần START đầu tiên
    // Update notification nên dùng startService() hoặc binding/Messenger
    // Tạm thời vẫn dùng startForegroundService nhưng cần refactor sau
    ContextCompat.startForegroundService(context, intent)
    // TODO: Refactor để dùng startService() hoặc IPC khác cho update
}
```

#### 1.2. Update `PlaybackForegroundService.kt`

**Thay đổi:**
```kotlin
@Inject
lateinit var notificationDeepLinkHandler: NotificationDeepLinkHandler

private var currentRecordingId: String? = null  // ← Thêm

// Trong onStartCommand:
val recordingId = intent?.getStringExtra("recordingId")
if (recordingId != null) {
    currentRecordingId = recordingId
}

// Trong createNotification:
private fun createNotification(position: Long, duration: Long, isPlaying: Boolean): Notification {
    // Sử dụng NotificationDeepLinkHandler
    val route = if (currentRecordingId != null) {
        AppRoutes.transcriptDetail(currentRecordingId!!)
    } else {
        AppLogger.logRareCondition(TAG_SERVICE, "Playback service started without recordingId")
        AppRoutes.RECORD  // Fallback
    }
    
    // ✅ NotificationDeepLinkHandler đã có FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE
    val pendingIntent = notificationDeepLinkHandler.createPendingIntent(route)
    
    // ... rest of notification
}
```

#### 1.3. Update `TranscriptViewModel.kt`

**Thay đổi:**
```kotlin
// Khi start playback:
foregroundServiceManager.startPlaybackService(
    recording.id,  // ← Thêm recordingId
    recording.title.ifEmpty { "Recording" },
    recording.durationMs
)

// Khi update notification:
foregroundServiceManager.updatePlaybackNotification(
    recording.id,  // ← Thêm recordingId
    _uiState.value.currentPositionMs,
    recording.durationMs,
    isPaused = true
)
```

#### 1.4. Update `MainActivity.kt` - ⚠️ CRITICAL FIX

**Vấn đề:** `LaunchedEffect(Unit)` chỉ chạy 1 lần → không handle `onNewIntent()` khi Activity đã mở sẵn.

**Giải pháp:** Dùng `StateFlow` để truyền route từ Activity → Compose.

**Thay đổi:**
```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // StateFlow để truyền notification route từ Activity → Compose
    private val notificationRouteState = MutableStateFlow<String?>(null)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Handle deep link from notification (onCreate)
        handleNotificationDeepLink(intent)
        
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Truyền StateFlow vào SmartRecorderApp
                    SmartRecorderApp(notificationRouteState = notificationRouteState)
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle deep link từ notification (onNewIntent - khi Activity đã mở)
        handleNotificationDeepLink(intent)
    }
    
    private fun handleNotificationDeepLink(intent: Intent?) {
        val route = intent?.getStringExtra("notification_route") ?: return
        // Clear extra để tránh re-process
        intent.removeExtra("notification_route")
        // Update StateFlow → Compose sẽ nhận route mới
        notificationRouteState.value = route
        AppLogger.d(TAG_LIFECYCLE, "Notification route received", "route=$route")
    }
    
    companion object {
        private const val TAG_LIFECYCLE = "MainActivity"
    }
}
```

#### 1.5. Update `SmartRecorderApp.kt` - ⚠️ CRITICAL FIX

**Thay đổi:**
```kotlin
@Composable
fun SmartRecorderApp(
    notificationRouteState: StateFlow<String?>? = null  // ← Thêm parameter
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("/") 
        ?: AppRoutes.RECORD
    
    // Lắng nghe notification route từ StateFlow (handle cả onCreate và onNewIntent)
    val notificationRoute by (notificationRouteState ?: MutableStateFlow<String?>(null)).collectAsState()
    
    LaunchedEffect(notificationRoute) {  // ← Thay đổi từ Unit → notificationRoute
        notificationRoute?.let { route ->
            AppLogger.d(TAG_NAV, "Navigating from notification", "route=$route")
            
            when {
                route.startsWith("transcript_detail/") -> {
                    val recordingId = route.substringAfter("transcript_detail/")
                    navController.navigate(AppRoutes.transcriptDetail(recordingId)) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                route == AppRoutes.RECORD -> {
                    navController.navigate(AppRoutes.RECORD) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                // ... other routes
            }
            
            // Reset route sau khi navigate (optional, để tránh re-navigate)
            // notificationRouteState?.value = null  // Nếu dùng callback
        }
    }
    
    // ... rest of SmartRecorderApp
}
```

**✅ Lợi ích:**
- Handle được cả `onCreate` (app mới mở) và `onNewIntent` (app đã mở)
- Mỗi lần có Intent mới → Compose nhận route mới và navigate an toàn
- Tránh bug "tap notification lần 2 không navigate"

---

### Phase 2: Fix RecordingForegroundService (RecordScreen)

#### 2.1. Update `RecordingForegroundService.kt`

**Thay đổi:**
```kotlin
// Sử dụng NotificationDeepLinkHandler
@Inject
lateinit var notificationDeepLinkHandler: NotificationDeepLinkHandler

// Trong createNotification:
private fun createNotification(durationMs: Long, isPausedState: Boolean): Notification {
    val pendingIntent = notificationDeepLinkHandler.createPendingIntent(AppRoutes.RECORD)
    // ... rest of notification
}
```

**Note:** Recording service đã có `recordingId`, nhưng không cần vì luôn về RecordScreen.

---

### Phase 3: Optional - LibraryScreen Playback với Notification

**Quyết định:** Không implement ngay, vì:
- LibraryScreen playback là quick preview, không cần foreground service
- Chỉ khi vào TranscriptScreen mới cần notification
- Tránh complexity không cần thiết

**Nếu muốn implement sau:**
- Thêm `startPlaybackService()` vào `LibraryViewModel.playRecording()`
- Destination vẫn là `transcript_detail/{recordingId}`

---

## ⚠️ Rare Conditions & Edge Cases

### 1. **Service chạy nhưng recordingId không tồn tại trong DB**

**Scenario:**
- User play recording → service start với `recordingId`
- User xóa recording từ LibraryScreen
- User tap notification → navigate đến TranscriptScreen
- TranscriptScreen load → recording không tồn tại

**Xử lý:**
- `TranscriptViewModel` đã có error handling khi load recording
- Nếu recording không tồn tại → show error → navigate back
- **Log:** `AppLogger.logRareCondition("Recording deleted while playback active", "recordingId=$recordingId")`

---

### 2. **App bị kill, service restart, nhưng recordingId đã bị xóa**

**Scenario:**
- Service restart với `recordingId` từ Intent
- Recording đã bị xóa
- Service vẫn chạy với notification

**Xử lý:**
- Service không cần check DB (không có access)
- Khi tap notification → TranscriptScreen sẽ handle error
- **Log:** Service log khi start với invalid recordingId (nếu có thể detect)

---

### 3. **Cả 2 service cùng chạy (Recording + Playback)**

**Scenario:**
- User đang recording ở RecordScreen
- User mở TranscriptScreen khác và play
- Cả 2 service chạy cùng lúc

**Xử lý:**
- **Không nên xảy ra** trong normal flow
- Nếu xảy ra:
  - Có 2 notifications riêng biệt
  - Tap notification recording → về RecordScreen
  - Tap notification playback → về TranscriptScreen
- **Log:** `AppLogger.logRareCondition("Both recording and playback services active")`

---

### 4. **Multiple recordings play cùng lúc**

**Scenario:**
- User play recording A → service start
- User play recording B → service update với recordingId mới
- Notification update với recordingId mới

**Xử lý:**
- Service chỉ support 1 playback tại một thời điểm
- Khi play recording mới → service update với `recordingId` mới
- Tap notification → về TranscriptScreen của recording mới nhất
- **Log:** Service log khi update với recordingId khác

---

### 5. **User xóa recording đang play**

**Scenario:**
- User play recording → service start
- User xóa recording từ LibraryScreen
- Service vẫn chạy với notification

**Xử lý:**
- Service không biết recording đã bị xóa
- Tap notification → TranscriptScreen load → error → navigate back
- **Log:** `AppLogger.logRareCondition("Recording deleted while playback active")`

---

### 6. **Notification tap khi app đã mở ở màn hình khác**

**Scenario:**
- User tap notification
- App đã mở ở LibraryScreen
- Cần navigate đến TranscriptScreen

**Xử lý:**
- `SmartRecorderApp` xử lý deep link từ Intent
- Navigate đến đúng route, clear back stack nếu cần
- **Log:** Normal navigation, không cần log đặc biệt

---

### 7. **Service start nhưng không có recordingId (legacy/error)**

**Scenario:**
- Service start từ code cũ không truyền `recordingId`
- Hoặc Intent bị corrupt

**Xử lý:**
- Service fallback về `AppRoutes.RECORD`
- **Log:** `AppLogger.logRareCondition("Playback service started without recordingId")`

---

## 📝 Testing Checklist

### Test Cases

1. ✅ **TranscriptScreen Playback**
   - Play audio → ẩn app → tap notification → về TranscriptScreen đúng recordingId

2. ✅ **RecordScreen Recording**
   - Start recording → ẩn app → tap notification → về RecordScreen

3. ✅ **Recording deleted while playback**
   - Play audio → xóa recording → tap notification → error handled

4. ✅ **App killed and restarted**
   - Play audio → kill app → tap notification → app restart → navigate đúng

5. ✅ **Multiple playbacks (không nên xảy ra)**
   - Play A → Play B → notification update với B → tap → về TranscriptScreen của B

6. ✅ **Notification tap khi app đã mở**
   - App mở ở LibraryScreen → tap notification → navigate đến TranscriptScreen

---

## 🔄 Migration Strategy

### Backward Compatibility

- Service cũ không truyền `recordingId` → fallback về `RECORD` screen
- Không break existing functionality

### Rollout Plan

1. **Phase 1:** Fix PlaybackForegroundService (TranscriptScreen) - **Priority 1**
2. **Phase 2:** Fix RecordingForegroundService (RecordScreen) - **Priority 2**
3. **Phase 3:** Optional - LibraryScreen notification (nếu cần)

---

## 📊 Summary

| Trường Hợp | Service | Destination | Priority | Status |
|------------|---------|-------------|----------|--------|
| TranscriptScreen + Playback | PlaybackForegroundService | `transcript_detail/{recordingId}` | High | ❌ Cần fix |
| LibraryScreen + Playback | None (hoặc optional) | `transcript_detail/{recordingId}` | Low | ⚠️ Optional |
| RecordScreen + Recording | RecordingForegroundService | `record` | Medium | ⚠️ Cần verify |

---

---

## 🔒 Lock Screen & Media Controls

### Hiện Trạng

**❌ Vấn đề:**
- App **KHÔNG có MediaSession** → Lock screen controls không hoạt động đúng
- Notification có `VISIBILITY_PUBLIC` nhưng không có MediaStyle → controls hạn chế
- User không thể control playback/recording từ lock screen một cách native

### Giải Pháp

#### 1. **Thêm MediaSession cho PlaybackForegroundService**

**Mục đích:**
- Enable lock screen media controls (Play/Pause/Stop)
- Enable Android Auto, Wear OS controls
- Better integration với system media controls

**Implementation:**
```kotlin
// PlaybackForegroundService.kt
import androidx.media.session.MediaSessionCompat
import androidx.media.app.NotificationCompat.MediaStyle  // ← Đúng import
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat

private var mediaSession: MediaSessionCompat? = null

override fun onCreate() {
    super.onCreate()
    // ... existing code ...
    
    // Create MediaSession for lock screen controls
    mediaSession = MediaSessionCompat(this, "PlaybackService").apply {
        isActive = true
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                // Send broadcast to ViewModel
                sendBroadcast(Intent(ACTION_RESUME))
            }
            
            override fun onPause() {
                sendBroadcast(Intent(ACTION_PAUSE))
            }
            
            override fun onStop() {
                sendBroadcast(Intent(ACTION_STOP))
            }
        })
    }
}

// ⚠️ CRITICAL: Phải set Metadata và PlaybackState để lock screen hiển thị đúng
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

// Trong createNotification:
NotificationCompat.Builder(this, CHANNEL_ID)
    .setStyle(
        MediaStyle()  // ← Dùng đúng androidx.media.app.NotificationCompat.MediaStyle
            .setShowActionsInCompactView(0, 1)  // Play/Pause và Stop
            .setMediaSession(mediaSession!!.sessionToken)
    )
    // ... rest of notification
```

**✅ Lưu ý:**
- Dùng đúng `androidx.media.app.NotificationCompat.MediaStyle` (không phải class khác)
- Phải set `MediaMetadata` (title, artist, duration) và `PlaybackState` (state, position)
- Nếu không set metadata/playbackState → lock screen UI không hiển thị progress/trạng thái chuẩn

**Navigation khi tap lock screen:**
- Lock screen controls → Tap notification → Navigate đến `transcript_detail/{recordingId}`
- Sử dụng cùng PendingIntent như notification

---

#### 2. **RecordingForegroundService - Lock Screen Controls**

**Hiện trạng:**
- Recording notification đã có `VISIBILITY_PUBLIC`
- Có action buttons (Pause/Resume/Stop)
- **Không cần MediaSession** (recording không phải media playback)

**Navigation:**
- Tap notification → Navigate đến `record` screen
- Action buttons → Broadcast → ViewModel handle

---

## 📱 Notification Panel & Quick Settings

### Hiện Trạng

**✅ Đã có:**
- Notification hiển thị trong notification panel
- Action buttons hoạt động (Pause/Resume/Stop)
- Tap notification → Mở app (nhưng navigate sai - cần fix)

**❌ Vấn đề:**
- Tap notification → Navigate sai màn hình (đã xác định ở trên)

### Giải Pháp

**Navigation từ Notification Panel:**
- **Playback notification:** Tap → Navigate đến `transcript_detail/{recordingId}`
- **Recording notification:** Tap → Navigate đến `record`
- **Action buttons:** Broadcast → ViewModel handle → Không navigate

**Quick Settings:**
- Android không có quick settings tile cho media controls
- User phải dùng notification panel hoặc lock screen

---

## 💀 Process Death & Service Restart

### Scenario: App bị kill bởi Android (RAM management)

**Vấn đề:**
1. User play audio → Service start với `recordingId`
2. Android kill app process (low memory)
3. Service restart với `START_STICKY`
4. Service có `recordingId` từ Intent, nhưng:
   - App process đã chết → ViewModel không còn
   - AudioPlayer có thể đã stop
   - State không sync

### Giải Pháp

#### 1. **Service State Persistence**

**PlaybackForegroundService:**
```kotlin
// Lưu state vào SharedPreferences khi start
private fun savePlaybackState(recordingId: String, position: Long, duration: Long) {
    val prefs = getSharedPreferences("playback_state", Context.MODE_PRIVATE)
    prefs.edit().apply {
        putString("recording_id", recordingId)
        putLong("position", position)
        putLong("duration", duration)
        putLong("last_update", System.currentTimeMillis())
        apply()
    }
}

// Restore state khi service restart
private fun restorePlaybackState(): PlaybackState? {
    val prefs = getSharedPreferences("playback_state", Context.MODE_PRIVATE)
    val recordingId = prefs.getString("recording_id", null) ?: return null
    val position = prefs.getLong("position", 0L)
    val duration = prefs.getLong("duration", 0L)
    val lastUpdate = prefs.getLong("last_update", 0L)
    
    // Check if state is stale (older than 1 hour)
    if (System.currentTimeMillis() - lastUpdate > 3600000) {
        return null  // State too old, ignore
    }
    
    return PlaybackState(recordingId, position, duration)
}
```

**RecordingForegroundService:**
- ✅ **Đã có `RecordingStateManager`** → State được persist
- Service restart → Restore state từ SharedPreferences
- **Navigation:** Khi tap notification → Navigate đến `record` (đúng)

---

#### 2. **App Restart Recovery**

**Khi app restart sau process death:**

**Scenario A: Service vẫn chạy (foreground service)**
- Service có state → Notification vẫn hiển thị
- User tap notification → App start → Navigate đúng
- **Issue:** ViewModel không có state → Cần restore từ service hoặc DB

**Scenario B: Service bị kill cùng app (rare)**
- Service restart với `START_STICKY` → Restore state từ SharedPreferences
- Notification được recreate
- User tap notification → App start → Navigate đúng

**⚠️ CRITICAL: Tránh Double Navigation**

**Vấn đề:** Nếu app được mở bằng notification → có `notification_route` → navigate. Nhưng nếu đồng thời check service state → lại navigate lần nữa → double navigation, backstack rối.

**Giải pháp:**
```kotlin
// SmartRecorderApp.kt
LaunchedEffect(notificationRoute) {
    notificationRoute?.let { route ->
        // Nếu có notification_route → chỉ navigate theo route này
        // KHÔNG check service state nữa
        navigateToRoute(route)
    }
}

// Chỉ check service state khi KHÔNG có notification_route
LaunchedEffect(Unit) {
    // Delay một chút để đảm bảo notification_route được xử lý trước
    delay(100)
    
    // Nếu không có notification_route → mới check service state
    if (notificationRoute == null) {
        val playbackState = checkPlaybackServiceState()
        if (playbackState != null) {
            // Service is running but app was killed
            // Navigate to correct screen
            navController.navigate(AppRoutes.transcriptDetail(playbackState.recordingId)) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        
        val recordingState = checkRecordingServiceState()
        if (recordingState != null) {
            // Recording service is running
            navController.navigate(AppRoutes.RECORD) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
}
```

**✅ Rule:**
- **Nếu app được mở bằng notification** → chỉ tin `notification_route`, bỏ qua auto-check service state
- Chỉ dùng `checkPlaybackServiceState()` / `checkRecordingServiceState()` cho case:
  - App mở bình thường (launcher icon)
  - Không có `notification_route`

---

#### 3. **ViewModel State Recovery**

**TranscriptViewModel:**
```kotlin
init {
    // Check if playback service is running
    viewModelScope.launch {
        val serviceState = playbackServiceManager.getCurrentState()
        if (serviceState != null && serviceState.recordingId == recordingId) {
            // Service is playing this recording
            // Restore state
            _uiState.update {
                it.copy(
                    isPlaying = true,
                    currentPositionMs = serviceState.position
                )
            }
            // Start position updates
            startPositionUpdates()
        }
    }
}
```

**RecordViewModel:**
- ✅ **Đã có recovery logic** từ `RecordingStateManager`
- App restart → Check state → Restore recording UI

---

## ⚠️ Edge Cases - Process Death

### 1. **Service restart nhưng recordingId không tồn tại**

**Scenario:**
- Service restart với `recordingId` từ Intent
- Recording đã bị xóa từ DB
- Service vẫn chạy với notification

**Xử lý:**
- Service không check DB (không có access)
- Khi tap notification → TranscriptScreen load → Error → Navigate back
- **Log:** `AppLogger.logRareCondition("Service restart with deleted recordingId", "recordingId=$recordingId")`

---

### 2. **Service restart nhưng audio file không tồn tại**

**Scenario:**
- Service restart với `recordingId`
- Audio file bị xóa hoặc move
- Service vẫn chạy

**Xử lý:**
- Service không check file (không có access)
- Khi tap notification → TranscriptScreen load → Error → Navigate back
- **Log:** `AppLogger.logRareCondition("Service restart with missing audio file", "recordingId=$recordingId")`

---

### 3. **Service restart nhưng ViewModel không restore state**

**Scenario:**
- Service restart → Notification hiển thị
- User tap notification → App start → Navigate đúng
- Nhưng ViewModel không restore playback state → UI không sync

**Xử lý:**
- ViewModel check service state khi init
- Restore state từ service hoặc DB
- **Log:** `AppLogger.logRareCondition("ViewModel state not restored after process death")`

---

### 4. **Service restart nhưng AudioPlayer đã stop**

**Scenario:**
- App bị kill → AudioPlayer stop
- Service restart → Notification vẫn hiển thị "Playing"
- User tap notification → App start → UI hiển thị "Playing" nhưng audio không chạy

**Xử lý:**
- ViewModel check AudioPlayer state khi restore
- Nếu AudioPlayer không playing → Update UI state
- **Log:** `AppLogger.logRareCondition("AudioPlayer state mismatch after process death")`

---

### 5. **Service restart với stale state (quá cũ)**

**Scenario:**
- Service restart với state từ SharedPreferences
- State quá cũ (ví dụ: 2 giờ trước)
- Service vẫn hiển thị notification

**Xử lý:**
- Check `last_update` timestamp
- Nếu quá cũ (> 1 giờ) → Clear state → Stop service
- **Log:** `AppLogger.logRareCondition("Service restart with stale state", "age=${age}ms")`

---

## 🔄 Service Lifecycle & State Management

### PlaybackForegroundService State Flow

```
1. Start Playback
   → Save state to SharedPreferences
   → Start foreground service
   → Create notification with recordingId

2. Update Position
   → Update SharedPreferences
   → Update notification

3. Process Death
   → Service restart (START_STICKY)
   → Restore state from SharedPreferences
   → Recreate notification

4. App Restart
   → Check service state
   → Navigate to correct screen
   → ViewModel restore state from service
```

### RecordingForegroundService State Flow

```
1. Start Recording
   → RecordingStateManager.save()
   → Start foreground service
   → Create notification

2. Process Death
   → Service restart (START_STICKY)
   → RecordingStateManager.restore()
   → Recreate notification

3. App Restart
   → Check RecordingStateManager
   → Navigate to RecordScreen
   → ViewModel restore state
```

---

## 📝 Testing Checklist - Process Death

### Test Cases

1. ✅ **Service restart after app kill**
   - Play audio → Kill app → Service restart → Tap notification → Navigate đúng

2. ✅ **State recovery after process death**
   - Play audio → Kill app → App restart → ViewModel restore state

3. ✅ **Stale state cleanup**
   - Service restart với state cũ (> 1 giờ) → State cleared → Service stopped

4. ✅ **Recording recovery**
   - Recording → Kill app → Service restart → App restart → Recording state restored

5. ✅ **Lock screen controls after restart**
   - Play audio → Kill app → Service restart → Lock screen controls hoạt động

---

---

## ⚠️ Critical Pitfalls & Fixes

### Pitfall 1: LaunchedEffect(Unit) không handle onNewIntent ✅ FIXED

**Vấn đề:** `LaunchedEffect(Unit)` chỉ chạy 1 lần → không handle `onNewIntent()` khi Activity đã mở sẵn.

**Fix:** Dùng `StateFlow<String?>` từ Activity → Compose, lắng nghe trong `LaunchedEffect(notificationRoute)`.

**Status:** ✅ Đã fix trong section 1.4 và 1.5

---

### Pitfall 2: PendingIntent flags ✅ ALREADY CORRECT

**Vấn đề:** Notification cũ vẫn giữ extra cũ → tap vào luôn mở recordingId cũ.

**Fix:** `NotificationDeepLinkHandler` đã có `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE` → ✅ Đúng rồi.

**Status:** ✅ Không cần fix

---

### Pitfall 3: Lạm dụng startForegroundService cho update ⚠️ NEEDS REFACTOR

**Vấn đề:** Update notification liên tục mà lần nào cũng gọi `startForegroundService` → risk ANR/crash trên Android 8+.

**Fix hiện tại:** Tạm thời vẫn dùng `startForegroundService` nhưng cần refactor sau.

**TODO:**
- Refactor để dùng `startService()` hoặc binding/Messenger cho update
- Chỉ `startForegroundService` cho lần START đầu tiên

**Status:** ⚠️ Cần refactor sau (không blocking)

---

### Pitfall 4: Double Navigation ✅ FIXED

**Vấn đề:** Vừa có `notification_route` vừa auto-check service state → navigate 2 lần.

**Fix:** Chỉ check service state khi KHÔNG có `notification_route`.

**Status:** ✅ Đã fix trong section Process Death

---

### Pitfall 5: MediaSession thiếu Metadata/PlaybackState ✅ FIXED

**Vấn đề:** Không set `MediaMetadata` và `PlaybackState` → lock screen UI không hiển thị progress/trạng thái.

**Fix:** Thêm `updateMediaSessionMetadata()` và `updateMediaSessionPlaybackState()`.

**Status:** ✅ Đã fix trong section Lock Screen

---

## 🎯 Next Steps

1. ✅ Review plan với team/user
2. ⏳ **CRITICAL:** Fix MainActivity + SmartRecorderApp (StateFlow) - **Priority 1**
3. ⏳ Implement Phase 1 (PlaybackForegroundService) - **Priority 1**
4. ⏳ Add MediaSession for lock screen controls - **Priority 2**
5. ⏳ Implement state persistence for service restart - **Priority 2**
6. ⏳ Implement ViewModel state recovery - **Priority 3**
7. ⏳ Test process death scenarios
8. ⏳ Implement Phase 2 (RecordingForegroundService) - **Priority 2**
9. ⏳ Refactor update notification (dùng startService thay vì startForegroundService) - **Priority 3**
10. ⏳ Final testing và deployment

