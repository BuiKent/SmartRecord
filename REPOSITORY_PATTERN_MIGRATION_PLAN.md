# Kế Hoạch Migration Sang Repository Pattern (Production-Ready)

## 📋 Mục Tiêu

**Chuyển đổi từ ViewModel-centric sang Service-centric architecture:**
- Service là **source of truth** cho recording/playback state
- Repository lưu trữ và expose state qua StateFlow
- ViewModel chỉ observe và render UI
- **Robust**: Kể cả khi process bị kill, state vẫn sync được

---

## 🔍 Phân Tích Hiện Trạng

### 1.1. Recording State - Hiện Tại

**RecordViewModel (Source of Truth hiện tại):**
```kotlin
// State variables trong ViewModel
private var currentRecording: Recording? = null
private var startTimeMs: Long = 0L
private var totalPausedDurationMs: Long = 0L
private var pauseStartTimeMs: Long = 0L
@Volatile private var isStarting: Boolean = false
@Volatile private var isPaused: Boolean = false

// UI State
data class RecordUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val durationMs: Long = 0L,
    // ...
)
```

**RecordingForegroundService:**
```kotlin
// State variables trong Service
private var isRecording = false
private var isPaused = false
private var recordingStartTime: Long = 0L
private var lastBackgroundTime: Long = 0L

// Có RecordingStateManager nhưng chỉ dùng cho auto-save
recordingStateManager.setRecordingActive(recordingId, fileName, recordingStartTime)
```

**Vấn đề:**
- ❌ ViewModel và Service **có 2 bộ state riêng biệt**
- ❌ ViewModel recreate → state mất → không sync với service
- ❌ Service không phải source of truth
- ❌ Không có cách nào để ViewModel mới biết service đang record

---

### 1.2. Playback State - Hiện Tại

**TranscriptViewModel (Source of Truth hiện tại):**
```kotlin
// State trong ViewModel
data class TranscriptUiState(
    val isPlaying: Boolean = false,
    val isLooping: Boolean = false,
    val currentPositionMs: Long = 0L,
    val currentSegmentId: Long? = null,
    // ...
)

// Có recovery logic nhưng không đầy đủ
if (audioPlayer.isPlaying() && !_uiState.value.isPlaying) {
    // Recovery...
}
```

**PlaybackForegroundService:**
```kotlin
// State trong Service
private var isPlaying = false
private var currentTitle: String = ""
private var currentRecordingId: String? = null
private var currentPosition: Long = 0L
private var totalDuration: Long = 0L
```

**Vấn đề:**
- ❌ ViewModel và Service **có 2 bộ state riêng biệt**
- ❌ ViewModel recreate → state mất → không sync với service
- ❌ Recovery logic chỉ check AudioPlayer, không check service state

---

### 1.3. Navigation - Hiện Tại

**NotificationDeepLinkHandler:**
```kotlin
val intent = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    putExtra("notification_route", route)
}
```

**MainActivity:**
```xml
<!-- AndroidManifest.xml -->
<activity android:name=".MainActivity" ... />
<!-- Không có launchMode -->
```

**Vấn đề:**
- ❌ `FLAG_ACTIVITY_CLEAR_TOP` có thể recreate Activity
- ❌ Activity recreate → ViewModel recreate → state mất
- ❌ Không có `launchMode="singleTop"` trong manifest

---

## 🎯 Kiến Trúc Mới (Repository Pattern)

### 2.1. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Source of Truth                      │
│  ┌──────────────────┐      ┌──────────────────┐        │
│  │ RecordingService │      │ PlaybackService  │        │
│  │  (Engine)       │      │  (Engine)        │        │
│  └────────┬─────────┘      └────────┬─────────┘        │
│           │                         │                   │
│           │ Update State            │ Update State      │
│           ▼                         ▼                   │
│  ┌──────────────────┐      ┌──────────────────┐        │
│  │RecordingSession  │      │PlaybackSession   │        │
│  │   Repository     │      │   Repository     │        │
│  │  (StateFlow)     │      │  (StateFlow)     │        │
│  └────────┬─────────┘      └────────┬─────────┘        │
│           │                         │                   │
│           │ Observe                 │ Observe           │
│           ▼                         ▼                   │
│  ┌──────────────────┐      ┌──────────────────┐        │
│  │ RecordViewModel  │      │TranscriptViewModel│       │
│  │   (UI Adapter)   │      │   (UI Adapter)    │       │
│  └────────┬─────────┘      └────────┬─────────┘        │
│           │                         │                   │
│           │ Render                  │ Render            │
│           ▼                         ▼                   │
│  ┌──────────────────┐      ┌──────────────────┐        │
│  │   RecordScreen   │      │ TranscriptScreen │        │
│  └──────────────────┘      └──────────────────┘        │
└─────────────────────────────────────────────────────────┘
```

**Nguyên tắc:**
1. **Service** = Engine thực sự (MediaRecorder, AudioPlayer)
2. **Repository** = State storage (StateFlow)
3. **ViewModel** = UI adapter (observe repository, render UI)
4. **Screen** = UI (observe ViewModel)

---

### 2.2. State Models

#### RecordingState
```kotlin
sealed interface RecordingState {
    object Idle : RecordingState
    
    data class Active(
        val recordingId: String,
        val filePath: String,
        val startTimeMs: Long,
        val isPaused: Boolean = false,
        val pauseStartTimeMs: Long? = null,  // null nếu không paused
        val totalPausedDurationMs: Long = 0L  // Tổng thời gian đã pause
    ) : RecordingState {
        /**
         * Tính elapsed time (không tính pause time)
         */
        fun getElapsedMs(): Long {
            val now = System.currentTimeMillis()
            val baseElapsed = now - startTimeMs - totalPausedDurationMs
            return if (isPaused && pauseStartTimeMs != null) {
                // Đang paused: trừ thêm thời gian pause hiện tại
                baseElapsed - (now - pauseStartTimeMs)
            } else {
                baseElapsed
            }
        }
    }
}
```

#### PlaybackState
```kotlin
sealed interface PlaybackState {
    object Idle : PlaybackState
    
    data class Playing(
        val recordingId: String,
        val positionMs: Long,
        val durationMs: Long,
        val isLooping: Boolean = false
    ) : PlaybackState
    
    data class Paused(
        val recordingId: String,
        val positionMs: Long,
        val durationMs: Long,
        val isLooping: Boolean = false
    ) : PlaybackState
}
```

---

## 📝 Step-by-Step Implementation Plan

### Phase 1: Tạo State Models & Repositories

#### Step 1.1: Tạo RecordingState.kt
**File:** `app/src/main/java/com/yourname/smartrecorder/domain/state/RecordingState.kt`

**Nội dung:**
```kotlin
package com.yourname.smartrecorder.domain.state

sealed interface RecordingState {
    object Idle : RecordingState
    
    data class Active(
        val recordingId: String,
        val filePath: String,
        val startTimeMs: Long,
        val isPaused: Boolean = false,
        val pauseStartTimeMs: Long? = null,
        val totalPausedDurationMs: Long = 0L
    ) : RecordingState {
        fun getElapsedMs(): Long {
            val now = System.currentTimeMillis()
            val baseElapsed = now - startTimeMs - totalPausedDurationMs
            return if (isPaused && pauseStartTimeMs != null) {
                baseElapsed - (now - pauseStartTimeMs)
            } else {
                baseElapsed
            }
        }
    }
}
```

**Checklist:**
- [ ] Tạo file `RecordingState.kt`
- [ ] Định nghĩa `Idle` và `Active` states
- [ ] Thêm `getElapsedMs()` helper method
- [ ] Test `getElapsedMs()` với các trường hợp: recording, paused, resumed

**⚠️ Pitfalls:**
- ❌ **Sai:** Tính elapsed không trừ pause time → duration sai
- ✅ **Đúng:** Luôn trừ `totalPausedDurationMs` và pause time hiện tại nếu đang paused

---

#### Step 1.2: Tạo PlaybackState.kt
**File:** `app/src/main/java/com/yourname/smartrecorder/domain/state/PlaybackState.kt`

**Nội dung:**
```kotlin
package com.yourname.smartrecorder.domain.state

sealed interface PlaybackState {
    object Idle : PlaybackState
    
    data class Playing(
        val recordingId: String,
        val positionMs: Long,
        val durationMs: Long,
        val isLooping: Boolean = false
    ) : PlaybackState
    
    data class Paused(
        val recordingId: String,
        val positionMs: Long,
        val durationMs: Long,
        val isLooping: Boolean = false
    ) : PlaybackState
}
```

**Checklist:**
- [ ] Tạo file `PlaybackState.kt`
- [ ] Định nghĩa `Idle`, `Playing`, `Paused` states
- [ ] Thêm `isLooping` flag cho cả Playing và Paused

**⚠️ Pitfalls:**
- ❌ **Sai:** Chỉ có `Playing` state, không có `Paused` → không phân biệt được
- ✅ **Đúng:** Tách riêng `Playing` và `Paused` để UI render đúng

---

#### Step 1.3: Tạo RecordingSessionRepository.kt
**File:** `app/src/main/java/com/yourname/smartrecorder/data/repository/RecordingSessionRepository.kt`

**Nội dung:**
```kotlin
package com.yourname.smartrecorder.data.repository

import com.yourname.smartrecorder.domain.state.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingSessionRepository @Inject constructor() {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()
    
    /**
     * Set recording to active state
     * Called by RecordingForegroundService when recording starts
     */
    fun setActive(
        recordingId: String,
        filePath: String,
        startTimeMs: Long = System.currentTimeMillis()
    ) {
        _state.value = RecordingState.Active(
            recordingId = recordingId,
            filePath = filePath,
            startTimeMs = startTimeMs,
            isPaused = false,
            pauseStartTimeMs = null,
            totalPausedDurationMs = 0L
        )
    }
    
    /**
     * Pause recording
     * Called by RecordingForegroundService when pause is requested
     */
    fun pause() {
        val current = _state.value
        if (current is RecordingState.Active && !current.isPaused) {
            _state.value = current.copy(
                isPaused = true,
                pauseStartTimeMs = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Resume recording
     * Called by RecordingForegroundService when resume is requested
     */
    fun resume() {
        val current = _state.value
        if (current is RecordingState.Active && current.isPaused) {
            val pauseDuration = current.pauseStartTimeMs?.let {
                System.currentTimeMillis() - it
            } ?: 0L
            
            _state.value = current.copy(
                isPaused = false,
                pauseStartTimeMs = null,
                totalPausedDurationMs = current.totalPausedDurationMs + pauseDuration
            )
        }
    }
    
    /**
     * Set recording to idle state
     * Called by RecordingForegroundService when recording stops
     */
    fun setIdle() {
        _state.value = RecordingState.Idle
    }
    
    /**
     * Get current state (for testing/debugging)
     */
    fun getCurrentState(): RecordingState = _state.value
}
```

**Checklist:**
- [ ] Tạo file `RecordingSessionRepository.kt`
- [ ] Annotate với `@Singleton`
- [ ] Implement `setActive()`, `pause()`, `resume()`, `setIdle()`
- [ ] Test pause/resume logic: pause time được tính đúng
- [ ] Test edge cases: pause khi đã paused, resume khi không paused

**⚠️ Pitfalls:**
- ❌ **Sai:** Không tính `pauseDuration` khi resume → `totalPausedDurationMs` sai
- ❌ **Sai:** Không check `current is RecordingState.Active` → crash khi state là Idle
- ✅ **Đúng:** Luôn check state trước khi update, tính pause duration chính xác

---

#### Step 1.4: Tạo PlaybackSessionRepository.kt
**File:** `app/src/main/java/com/yourname/smartrecorder/data/repository/PlaybackSessionRepository.kt`

**Nội dung:**
```kotlin
package com.yourname.smartrecorder.data.repository

import com.yourname.smartrecorder.domain.state.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSessionRepository @Inject constructor() {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    
    /**
     * Start playback
     * Called by PlaybackForegroundService when playback starts
     */
    fun setPlaying(
        recordingId: String,
        positionMs: Long = 0L,
        durationMs: Long,
        isLooping: Boolean = false
    ) {
        _state.value = PlaybackState.Playing(
            recordingId = recordingId,
            positionMs = positionMs,
            durationMs = durationMs,
            isLooping = isLooping
        )
    }
    
    /**
     * Update playback position
     * Called by PlaybackForegroundService during playback
     */
    fun updatePosition(positionMs: Long) {
        val current = _state.value
        when (current) {
            is PlaybackState.Playing -> {
                _state.value = current.copy(positionMs = positionMs)
            }
            is PlaybackState.Paused -> {
                _state.value = current.copy(positionMs = positionMs)
            }
            is PlaybackState.Idle -> {
                // Ignore position update if not playing
            }
        }
    }
    
    /**
     * Pause playback
     * Called by PlaybackForegroundService when pause is requested
     */
    fun pause() {
        val current = _state.value
        if (current is PlaybackState.Playing) {
            _state.value = PlaybackState.Paused(
                recordingId = current.recordingId,
                positionMs = current.positionMs,
                durationMs = current.durationMs,
                isLooping = current.isLooping
            )
        }
    }
    
    /**
     * Resume playback
     * Called by PlaybackForegroundService when resume is requested
     */
    fun resume() {
        val current = _state.value
        if (current is PlaybackState.Paused) {
            _state.value = PlaybackState.Playing(
                recordingId = current.recordingId,
                positionMs = current.positionMs,
                durationMs = current.durationMs,
                isLooping = current.isLooping
            )
        }
    }
    
    /**
     * Stop playback
     * Called by PlaybackForegroundService when playback stops
     */
    fun setIdle() {
        _state.value = PlaybackState.Idle
    }
    
    /**
     * Update looping state
     */
    fun setLooping(isLooping: Boolean) {
        val current = _state.value
        when (current) {
            is PlaybackState.Playing -> {
                _state.value = current.copy(isLooping = isLooping)
            }
            is PlaybackState.Paused -> {
                _state.value = current.copy(isLooping = isLooping)
            }
            is PlaybackState.Idle -> {
                // Ignore if not playing
            }
        }
    }
    
    /**
     * Get current state (for testing/debugging)
     */
    fun getCurrentState(): PlaybackState = _state.value
}
```

**Checklist:**
- [ ] Tạo file `PlaybackSessionRepository.kt`
- [ ] Annotate với `@Singleton`
- [ ] Implement tất cả methods: `setPlaying()`, `updatePosition()`, `pause()`, `resume()`, `setIdle()`, `setLooping()`
- [ ] Test state transitions: Idle → Playing → Paused → Playing → Idle
- [ ] Test edge cases: pause khi đã paused, resume khi không paused

**⚠️ Pitfalls:**
- ❌ **Sai:** Không update position trong Paused state → position bị stale
- ❌ **Sai:** Không preserve `isLooping` khi pause/resume → mất state
- ✅ **Đúng:** Luôn preserve tất cả fields khi transition state

---

### Phase 2: Integrate Repository vào Services

#### Step 2.1: Update RecordingForegroundService

**File:** `app/src/main/java/com/yourname/smartrecorder/core/service/RecordingForegroundService.kt`

**Thay đổi:**

1. **Inject Repository:**
```kotlin
@AndroidEntryPoint
class RecordingForegroundService : Service() {
    @Inject
    lateinit var recordingStateManager: RecordingStateManager
    
    @Inject
    lateinit var notificationDeepLinkHandler: NotificationDeepLinkHandler
    
    @Inject
    lateinit var recordingSessionRepository: RecordingSessionRepository  // ← Thêm
    
    // ... existing code
}
```

2. **Update startRecording():**
```kotlin
fun startRecording(recordingId: String, fileName: String) {
    if (isRecording) {
        AppLogger.logRareCondition(TAG_SERVICE, 
            "Attempted to start recording while already recording", 
            "recordingId=$recordingId")
        return
    }
    
    isRecording = true
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
    
    startForeground(NOTIFICATION_ID, createNotification(0, true))
    recordingStateManager.setRecordingActive(recordingId, fileName, recordingStartTime)
}
```

3. **Update pauseRecording():**
```kotlin
fun pauseRecording() {
    if (!isRecording || isPaused) {
        AppLogger.logRareCondition(TAG_SERVICE, 
            "Attempted to pause when not recording or already paused")
        return
    }
    
    isPaused = true
    AppLogger.logCritical(TAG_SERVICE, "Recording paused in foreground service")
    
    // ⚠️ CRITICAL: Update repository state
    recordingSessionRepository.pause()
    
    // ... existing pause logic
}
```

4. **Update resumeRecording():**
```kotlin
fun resumeRecording() {
    if (!isRecording || !isPaused) {
        AppLogger.logRareCondition(TAG_SERVICE, 
            "Attempted to resume when not recording or not paused")
        return
    }
    
    isPaused = false
    AppLogger.logCritical(TAG_SERVICE, "Recording resumed in foreground service")
    
    // ⚠️ CRITICAL: Update repository state
    recordingSessionRepository.resume()
    
    // ... existing resume logic
}
```

5. **Update stopRecording():**
```kotlin
fun stopRecording() {
    if (!isRecording) {
        AppLogger.logRareCondition(TAG_SERVICE, 
            "Attempted to stop recording when not recording")
        return
    }
    
    val duration = System.currentTimeMillis() - recordingStartTime
    AppLogger.logCritical(TAG_SERVICE, "Recording stopped in foreground service", 
        "duration=${duration}ms")
    
    isRecording = false
    isPaused = false
    recordingStartTime = 0L
    lastBackgroundTime = 0L
    
    // ⚠️ CRITICAL: Update repository state FIRST
    recordingSessionRepository.setIdle()
    
    recordingStateManager.clearRecordingState()
    
    // Send broadcast to ViewModel
    sendBroadcast(BROADCAST_STOP)
}
```

6. **Update onDestroy():**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    AppLogger.logService(TAG_SERVICE, "RecordingForegroundService", "onDestroy")
    
    // ⚠️ CRITICAL: Update repository state if recording was active
    if (isRecording) {
        AppLogger.logRareCondition(TAG_SERVICE, 
            "Service destroyed while recording", 
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
```

**Checklist:**
- [ ] Inject `RecordingSessionRepository` vào service
- [ ] Update `startRecording()` → gọi `repository.setActive()`
- [ ] Update `pauseRecording()` → gọi `repository.pause()`
- [ ] Update `resumeRecording()` → gọi `repository.resume()`
- [ ] Update `stopRecording()` → gọi `repository.setIdle()`
- [ ] Update `onDestroy()` → gọi `repository.setIdle()` nếu đang recording
- [ ] Test: Service start → repository state = Active
- [ ] Test: Service pause → repository state = Active(isPaused=true)
- [ ] Test: Service resume → repository state = Active(isPaused=false)
- [ ] Test: Service stop → repository state = Idle
- [ ] Test: Service destroy → repository state = Idle

**⚠️ Pitfalls:**
- ❌ **Sai:** Update repository state SAU khi update local variables → race condition
- ✅ **Đúng:** Update repository state TRƯỚC hoặc CÙNG LÚC với local state
- ❌ **Sai:** Không update repository trong `onDestroy()` → state bị stale nếu service bị kill
- ✅ **Đúng:** Luôn cleanup repository state trong `onDestroy()`

---

#### Step 2.2: Update PlaybackForegroundService

**File:** `app/src/main/java/com/yourname/smartrecorder/core/service/PlaybackForegroundService.kt`

**Thay đổi tương tự RecordingForegroundService:**

1. **Inject Repository:**
```kotlin
@Inject
lateinit var playbackSessionRepository: PlaybackSessionRepository  // ← Thêm
```

2. **Update startPlayback():**
```kotlin
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
    
    startForeground(NOTIFICATION_ID, createNotification(0, duration, true))
}
```

3. **Update stopPlayback():**
```kotlin
fun stopPlayback() {
    if (!isPlaying) {
        AppLogger.logRareCondition(TAG_SERVICE, 
            "Attempted to stop playback when not playing")
        return
    }
    
    AppLogger.logCritical(TAG_SERVICE, "Playback stopped in foreground service", 
        "recordingId=$currentRecordingId, title=$currentTitle, finalPosition=$currentPosition")
    
    isPlaying = false
    currentTitle = ""
    currentRecordingId = null
    currentPosition = 0L
    totalDuration = 0L
    
    // ⚠️ CRITICAL: Update repository state
    playbackSessionRepository.setIdle()
}
```

4. **Update updateNotification() - BroadcastReceiver:**
```kotlin
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
```

5. **Update onDestroy():**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    AppLogger.logService(TAG_SERVICE, "PlaybackForegroundService", "onDestroy")
    
    // ⚠️ CRITICAL: Update repository state if playing
    if (isPlaying) {
        AppLogger.logRareCondition(TAG_SERVICE, 
            "Service destroyed while playing", 
            "title=$currentTitle, position=$currentPosition")
        playbackSessionRepository.setIdle()
    }
    
    // Unregister BroadcastReceiver
    try {
        unregisterReceiver(notificationUpdateReceiver)
    } catch (e: IllegalArgumentException) {
        // Receiver not registered, ignore
    }
}
```

**Checklist:**
- [ ] Inject `PlaybackSessionRepository` vào service
- [ ] Update `startPlayback()` → gọi `repository.setPlaying()`
- [ ] Update `stopPlayback()` → gọi `repository.setIdle()`
- [ ] Update `updateNotification()` receiver → update repository position/pause state
- [ ] Update `onDestroy()` → gọi `repository.setIdle()` nếu đang playing
- [ ] Test: Service start → repository state = Playing
- [ ] Test: Service update position → repository position updated
- [ ] Test: Service pause → repository state = Paused
- [ ] Test: Service resume → repository state = Playing
- [ ] Test: Service stop → repository state = Idle
- [ ] Test: Service destroy → repository state = Idle

**⚠️ Pitfalls:**
- ❌ **Sai:** Không update repository trong `updateNotification()` → position không sync
- ✅ **Đúng:** Update repository mỗi khi notification được update
- ❌ **Sai:** Không check `currentRecordingId != null` → crash
- ✅ **Đúng:** Luôn check null trước khi update repository

---

### Phase 3: Refactor ViewModels

#### Step 3.1: Refactor RecordViewModel

**File:** `app/src/main/java/com/yourname/smartrecorder/ui/record/RecordViewModel.kt`

**Thay đổi lớn:**

1. **Remove local state variables:**
```kotlin
// ❌ XÓA các biến này:
// private var currentRecording: Recording? = null
// private var startTimeMs: Long = 0L
// private var totalPausedDurationMs: Long = 0L
// private var pauseStartTimeMs: Long = 0L
// @Volatile private var isStarting: Boolean = false
// @Volatile private var isPaused: Boolean = false
```

2. **Inject Repository:**
```kotlin
@HiltViewModel
class RecordViewModel @Inject constructor(
    // ... existing dependencies
    private val recordingSessionRepository: RecordingSessionRepository,  // ← Thêm
    // ...
) : ViewModel() {
```

3. **Expose repository state:**
```kotlin
// Expose recording state from repository
val recordingState: StateFlow<RecordingState> = 
    recordingSessionRepository.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordingState.Idle
        )
```

4. **Derive UI state from repository:**
```kotlin
// Combine repository state with other UI state
val uiState: StateFlow<RecordUiState> = combine(
    recordingState,
    // ... other state flows
) { recordingState, /* ... */ ->
    RecordUiState(
        isRecording = recordingState is RecordingState.Active && !recordingState.isPaused,
        isPaused = recordingState is RecordingState.Active && recordingState.isPaused,
        durationMs = when (recordingState) {
            is RecordingState.Active -> recordingState.getElapsedMs()
            else -> 0L
        },
        // ... other fields
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = RecordUiState()
)
```

5. **Simplify onStartClick():**
```kotlin
fun onStartClick() {
    // Check if already recording
    if (recordingState.value is RecordingState.Active) {
        AppLogger.w(TAG_VIEWMODEL, "RecordViewModel: Start rejected - already recording")
        return
    }
    
    AppLogger.logViewModel(TAG_RECORDING, "RecordViewModel", "onStartClick", null)
    
    viewModelScope.launch {
        try {
            // Start recording - UseCase will start service, service will update repository
            val outputDir = getRecordingsDirectory()
            currentRecording = startRecording(outputDir)
            
            // Service will update repository state, UI will react automatically
        } catch (e: Exception) {
            // Error handling
        }
    }
}
```

6. **Simplify onPauseClick():**
```kotlin
fun onPauseClick() {
    val currentState = recordingState.value
    if (currentState !is RecordingState.Active) {
        AppLogger.w(TAG_VIEWMODEL, "RecordViewModel: Pause rejected - not recording")
        return
    }
    
    viewModelScope.launch {
        if (currentState.isPaused) {
            // Resume
            resumeRecording()
        } else {
            // Pause
            pauseRecording()
        }
        // Service will update repository, UI will react automatically
    }
}
```

7. **Simplify onStopClick():**
```kotlin
fun onStopClick() {
    val currentState = recordingState.value
    if (currentState !is RecordingState.Active) {
        AppLogger.w(TAG_VIEWMODEL, "RecordViewModel: Stop rejected - not recording")
        return
    }
    
    viewModelScope.launch {
        // Stop recording - UseCase will stop service, service will update repository
        stopRecordingAndSave(currentState.recordingId, currentState.getElapsedMs())
        // Service will update repository to Idle, UI will react automatically
    }
}
```

8. **Update timer logic:**
```kotlin
private fun startTimer() {
    timerJob?.cancel()
    timerJob = viewModelScope.launch {
        try {
            while (true) {
                delay(50)
                
                val currentState = recordingState.value
                if (currentState !is RecordingState.Active) {
                    break
                }
                
                val elapsed = currentState.getElapsedMs()
                val amplitude = if (!currentState.isPaused) {
                    try {
                        audioRecorder.getAmplitude()
                    } catch (e: Exception) {
                        0
                    }
                } else {
                    0
                }
                
                _uiState.update { 
                    it.copy(
                        durationMs = elapsed,
                        amplitude = amplitude
                    )
                }
                
                // Update notification every second
                if (elapsed % 1000 < 50) {
                    foregroundServiceManager.updateRecordingNotification(
                        elapsed, 
                        currentState.isPaused
                    )
                }
            }
        } catch (e: Exception) {
            // Timer cancelled or error
        }
    }
}
```

**Checklist:**
- [ ] Inject `RecordingSessionRepository`
- [ ] Remove local state variables (currentRecording, startTimeMs, etc.)
- [ ] Expose `recordingState` từ repository
- [ ] Derive `uiState` từ `recordingState`
- [ ] Simplify `onStartClick()` - chỉ start, không quản lý state
- [ ] Simplify `onPauseClick()` - chỉ pause/resume, không quản lý state
- [ ] Simplify `onStopClick()` - chỉ stop, không quản lý state
- [ ] Update timer logic - dùng `recordingState.getElapsedMs()`
- [ ] Remove BroadcastReceiver (không cần nữa vì observe repository)
- [ ] Test: Start recording → UI update từ repository state
- [ ] Test: Pause recording → UI update từ repository state
- [ ] Test: Resume recording → UI update từ repository state
- [ ] Test: Stop recording → UI update từ repository state
- [ ] Test: Navigate away and back → UI vẫn sync với service

**⚠️ Pitfalls:**
- ❌ **Sai:** Vẫn giữ local state variables → duplicate state, không sync
- ✅ **Đúng:** Xóa hết local state, chỉ dùng repository state
- ❌ **Sai:** Không dùng `getElapsedMs()` → duration không đúng khi paused
- ✅ **Đúng:** Luôn dùng `getElapsedMs()` để tính duration chính xác
- ❌ **Sai:** Vẫn dùng BroadcastReceiver → không cần nữa, observe repository là đủ
- ✅ **Đúng:** Xóa BroadcastReceiver, chỉ observe repository state

---

#### Step 3.2: Refactor TranscriptViewModel

**File:** `app/src/main/java/com/yourname/smartrecorder/ui/transcript/TranscriptViewModel.kt`

**Thay đổi tương tự RecordViewModel:**

1. **Inject Repository:**
```kotlin
@HiltViewModel
class TranscriptViewModel @Inject constructor(
    // ... existing dependencies
    private val playbackSessionRepository: PlaybackSessionRepository,  // ← Thêm
    // ...
) : ViewModel() {
```

2. **Expose playback state:**
```kotlin
// Expose playback state from repository
val playbackState: StateFlow<PlaybackState> = 
    playbackSessionRepository.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlaybackState.Idle
        )
```

3. **Derive UI state from repository:**
```kotlin
// Combine repository state with other UI state
val uiState: StateFlow<TranscriptUiState> = combine(
    playbackState,
    // ... other state flows (recording, segments, notes, etc.)
) { playbackState, /* ... */ ->
    TranscriptUiState(
        isPlaying = playbackState is PlaybackState.Playing,
        isLooping = when (playbackState) {
            is PlaybackState.Playing -> playbackState.isLooping
            is PlaybackState.Paused -> playbackState.isLooping
            else -> false
        },
        currentPositionMs = when (playbackState) {
            is PlaybackState.Playing -> playbackState.positionMs
            is PlaybackState.Paused -> playbackState.positionMs
            else -> 0L
        },
        // ... other fields
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = TranscriptUiState()
)
```

4. **Simplify togglePlayPause():**
```kotlin
fun togglePlayPause() {
    val recording = _uiState.value.recording ?: return
    val playbackState = playbackState.value
    
    viewModelScope.launch {
        when (playbackState) {
            is PlaybackState.Playing -> {
                // Pause
                audioPlayer.pause()
                // Service will update repository, UI will react automatically
            }
            is PlaybackState.Paused -> {
                // Resume
                audioPlayer.resume()
                // Service will update repository, UI will react automatically
            }
            is PlaybackState.Idle -> {
                // Start playing
                val file = File(recording.filePath)
                if (!file.exists()) {
                    _uiState.update { it.copy(error = "Audio file not found") }
                    return@launch
                }
                
                // Start service - service will update repository
                foregroundServiceManager.startPlaybackService(
                    recording.id,
                    recording.title.ifEmpty { "Recording" },
                    recording.durationMs
                )
                
                audioPlayer.play(file) { /* ... */ }
                // Service will update repository to Playing, UI will react automatically
            }
        }
    }
}
```

5. **Update position update logic:**
```kotlin
private fun startPositionUpdates() {
    positionUpdateJob?.cancel()
    positionUpdateJob = viewModelScope.launch {
        while (true) {
            delay(100)
            
            val playbackState = playbackState.value
            if (playbackState !is PlaybackState.Playing) {
                break
            }
            
            val position = audioPlayer.getCurrentPosition()
            val recording = _uiState.value.recording
            
            // Update UI
            _uiState.update { it.copy(currentPositionMs = position.toLong()) }
            updateCurrentSegment(position.toLong())
            
            // Update service notification (service will update repository)
            if (recording != null && position % 1000 < 100) {
                foregroundServiceManager.updatePlaybackNotification(
                    recording.id,
                    position.toLong(),
                    recording.durationMs,
                    isPaused = false
                )
            }
            
            // Check if finished
            if (!playbackState.isLooping && recording != null && position >= recording.durationMs) {
                // Finished
                audioPlayer.stop()
                foregroundServiceManager.stopPlaybackService()
                // Service will update repository to Idle, UI will react automatically
                break
            }
        }
    }
}
```

**Checklist:**
- [ ] Inject `PlaybackSessionRepository`
- [ ] Expose `playbackState` từ repository
- [ ] Derive `uiState` từ `playbackState`
- [ ] Simplify `togglePlayPause()` - chỉ control AudioPlayer, không quản lý state
- [ ] Update position update logic - dùng `playbackState`
- [ ] Remove local `isPlaying` state (nếu có)
- [ ] Test: Start playback → UI update từ repository state
- [ ] Test: Pause playback → UI update từ repository state
- [ ] Test: Resume playback → UI update từ repository state
- [ ] Test: Stop playback → UI update từ repository state
- [ ] Test: Navigate away and back → UI vẫn sync với service

**⚠️ Pitfalls:**
- ❌ **Sai:** Vẫn giữ local `isPlaying` state → duplicate state, không sync
- ✅ **Đúng:** Xóa local state, chỉ dùng repository state
- ❌ **Sai:** Không check `playbackState` trước khi control AudioPlayer → race condition
- ✅ **Đúng:** Luôn check repository state trước khi thao tác

---

### Phase 4: Fix Navigation & Activity

#### Step 4.1: Update AndroidManifest.xml

**File:** `app/src/main/AndroidManifest.xml`

**Thay đổi:**
```xml
<activity
    android:name=".MainActivity"
    android:launchMode="singleTop"  <!-- ← Thêm -->
    android:exported="true"
    ... >
    <!-- ... -->
</activity>
```

**Checklist:**
- [ ] Thêm `android:launchMode="singleTop"` vào MainActivity
- [ ] Test: App đang chạy → tap notification → Activity không recreate
- [ ] Test: App đang chạy → tap notification → `onNewIntent()` được gọi

**⚠️ Pitfalls:**
- ❌ **Sai:** Không thêm `launchMode="singleTop"` → Activity vẫn có thể recreate
- ✅ **Đúng:** Thêm `launchMode="singleTop"` để prevent recreate khi đã ở top

---

#### Step 4.2: Update NotificationDeepLinkHandler

**File:** `app/src/main/java/com/yourname/smartrecorder/core/notification/NotificationDeepLinkHandler.kt`

**Thay đổi:**
```kotlin
fun createPendingIntent(route: String): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        // ⚠️ CRITICAL: Dùng SINGLE_TOP thay vì CLEAR_TOP
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("notification_route", route)
    }
    
    return PendingIntent.getActivity(
        context,
        route.hashCode(),
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
```

**Checklist:**
- [ ] Đổi `FLAG_ACTIVITY_CLEAR_TOP` → `FLAG_ACTIVITY_SINGLE_TOP`
- [ ] Test: App đang chạy → tap notification → Activity không recreate
- [ ] Test: App đang chạy → tap notification → `onNewIntent()` được gọi

**⚠️ Pitfalls:**
- ❌ **Sai:** Chỉ dùng `FLAG_ACTIVITY_SINGLE_TOP` → có thể không clear stack
- ✅ **Đúng:** Dùng cả `SINGLE_TOP` và `CLEAR_TOP` để vừa không recreate vừa clear stack

---

#### Step 4.3: Verify MainActivity.onNewIntent()

**File:** `app/src/main/java/com/yourname/smartrecorder/MainActivity.kt`

**Kiểm tra:**
- [ ] `onNewIntent()` đã có và gọi `handleNotificationDeepLink()`
- [ ] `setIntent(intent)` được gọi để update intent
- [ ] StateFlow được update đúng

**Checklist:**
- [ ] Verify `onNewIntent()` implementation
- [ ] Test: App đang chạy → tap notification → `onNewIntent()` được gọi
- [ ] Test: Navigation hoạt động đúng

---

### Phase 5: Testing & Validation

#### Step 5.1: Unit Tests

**Test RecordingSessionRepository:**
- [ ] Test `setActive()` → state = Active
- [ ] Test `pause()` → state = Active(isPaused=true)
- [ ] Test `resume()` → state = Active(isPaused=false), pause duration được tính đúng
- [ ] Test `setIdle()` → state = Idle
- [ ] Test `getElapsedMs()` với các trường hợp: recording, paused, resumed

**Test PlaybackSessionRepository:**
- [ ] Test `setPlaying()` → state = Playing
- [ ] Test `pause()` → state = Paused
- [ ] Test `resume()` → state = Playing
- [ ] Test `updatePosition()` → position updated
- [ ] Test `setIdle()` → state = Idle
- [ ] Test `setLooping()` → isLooping updated

---

#### Step 5.2: Integration Tests

**Test Recording Flow:**
1. [ ] Start recording → Service state = Active → Repository state = Active → ViewModel state = Active → UI shows recording
2. [ ] Pause recording → Service state = Active(isPaused=true) → Repository state = Active(isPaused=true) → ViewModel state = Active(isPaused=true) → UI shows paused
3. [ ] Resume recording → Service state = Active(isPaused=false) → Repository state = Active(isPaused=false) → ViewModel state = Active(isPaused=false) → UI shows recording
4. [ ] Stop recording → Service state = Idle → Repository state = Idle → ViewModel state = Idle → UI shows stopped
5. [ ] Navigate away during recording → Service vẫn chạy → Repository state = Active → Navigate back → ViewModel state = Active → UI shows recording (sync đúng)

**Test Playback Flow:**
1. [ ] Start playback → Service state = Playing → Repository state = Playing → ViewModel state = Playing → UI shows playing
2. [ ] Pause playback → Service state = Paused → Repository state = Paused → ViewModel state = Paused → UI shows paused
3. [ ] Resume playback → Service state = Playing → Repository state = Playing → ViewModel state = Playing → UI shows playing
4. [ ] Stop playback → Service state = Idle → Repository state = Idle → ViewModel state = Idle → UI shows stopped
5. [ ] Navigate away during playback → Service vẫn chạy → Repository state = Playing → Navigate back → ViewModel state = Playing → UI shows playing (sync đúng)

**Test Notification Navigation:**
1. [ ] Recording → Tap notification → Navigate to RecordScreen → UI shows recording (sync đúng)
2. [ ] Playback → Tap notification → Navigate to TranscriptScreen → UI shows playing (sync đúng)
3. [ ] App đang chạy → Tap notification → Activity không recreate → ViewModel state giữ nguyên → UI sync đúng

**Test Process Death:**
1. [ ] Recording → Kill app → Service vẫn chạy → Repository state = Active → Restart app → ViewModel state = Active → UI shows recording (sync đúng)
2. [ ] Playback → Kill app → Service vẫn chạy → Repository state = Playing → Restart app → ViewModel state = Playing → UI shows playing (sync đúng)

---

#### Step 5.3: Edge Cases

**Test Edge Cases:**
1. [ ] Start recording khi đang recording → Ignore (không crash)
2. [ ] Pause khi không recording → Ignore (không crash)
3. [ ] Resume khi không paused → Ignore (không crash)
4. [ ] Stop khi không recording → Ignore (không crash)
5. [ ] Start playback khi đang playing → Ignore (không crash)
6. [ ] Pause khi không playing → Ignore (không crash)
7. [ ] Resume khi không paused → Ignore (không crash)
8. [ ] Stop khi không playing → Ignore (không crash)
9. [ ] Service bị kill đột ngột → Repository state = Idle → UI shows stopped
10. [ ] Multiple ViewModels observe cùng repository → Tất cả sync đúng

---

## 🚨 Critical Pitfalls & How to Avoid

### Pitfall 1: Race Condition - Update State Order

**❌ SAI:**
```kotlin
// Service
isRecording = true
recordingStartTime = System.currentTimeMillis()
// ... later ...
recordingSessionRepository.setActive(...)  // ← Update repository SAU
```

**✅ ĐÚNG:**
```kotlin
// Service
recordingSessionRepository.setActive(...)  // ← Update repository TRƯỚC
isRecording = true
recordingStartTime = System.currentTimeMillis()
```

**Lý do:** ViewModel có thể observe repository state trước khi service update local state → race condition.

---

### Pitfall 2: Duplicate State - Không Xóa Local State

**❌ SAI:**
```kotlin
// ViewModel
private var isRecording: Boolean = false  // ← Vẫn giữ local state
val recordingState: StateFlow<RecordingState> = repository.state

// UI dùng cả 2 → không sync
```

**✅ ĐÚNG:**
```kotlin
// ViewModel
// ❌ XÓA local state
val recordingState: StateFlow<RecordingState> = repository.state

// UI chỉ dùng recordingState
```

**Lý do:** Duplicate state → không sync → bug khó debug.

---

### Pitfall 3: Stale State - Không Cleanup trong onDestroy()

**❌ SAI:**
```kotlin
// Service
override fun onDestroy() {
    super.onDestroy()
    // Không cleanup repository → state bị stale
}
```

**✅ ĐÚNG:**
```kotlin
// Service
override fun onDestroy() {
    if (isRecording) {
        recordingSessionRepository.setIdle()  // ← Cleanup
    }
    super.onDestroy()
}
```

**Lý do:** Service bị kill → state vẫn là Active → ViewModel mới thấy "đang recording" nhưng thực tế không.

---

### Pitfall 4: Wrong Elapsed Time Calculation

**❌ SAI:**
```kotlin
// ViewModel
val elapsed = System.currentTimeMillis() - startTimeMs  // ← Không trừ pause time
```

**✅ ĐÚNG:**
```kotlin
// ViewModel
val elapsed = when (recordingState) {
    is RecordingState.Active -> recordingState.getElapsedMs()  // ← Dùng helper method
    else -> 0L
}
```

**Lý do:** Không trừ pause time → duration sai → user thấy duration tăng khi paused.

---

### Pitfall 5: Activity Recreate - Không Dùng singleTop

**❌ SAI:**
```xml
<!-- AndroidManifest.xml -->
<activity android:name=".MainActivity" />
<!-- Không có launchMode -->
```

**✅ ĐÚNG:**
```xml
<!-- AndroidManifest.xml -->
<activity 
    android:name=".MainActivity"
    android:launchMode="singleTop" />
```

**Lý do:** Activity recreate → ViewModel recreate → state mất (dù có repository, nhưng vẫn tốt hơn nếu không recreate).

---

## 📊 Migration Checklist (Tổng Hợp)

### Phase 1: State Models & Repositories
- [ ] Tạo `RecordingState.kt`
- [ ] Tạo `PlaybackState.kt`
- [ ] Tạo `RecordingSessionRepository.kt`
- [ ] Tạo `PlaybackSessionRepository.kt`
- [ ] Test repositories với unit tests

### Phase 2: Integrate vào Services
- [ ] Inject `RecordingSessionRepository` vào `RecordingForegroundService`
- [ ] Update `startRecording()` → `repository.setActive()`
- [ ] Update `pauseRecording()` → `repository.pause()`
- [ ] Update `resumeRecording()` → `repository.resume()`
- [ ] Update `stopRecording()` → `repository.setIdle()`
- [ ] Update `onDestroy()` → `repository.setIdle()` nếu cần
- [ ] Inject `PlaybackSessionRepository` vào `PlaybackForegroundService`
- [ ] Update `startPlayback()` → `repository.setPlaying()`
- [ ] Update `stopPlayback()` → `repository.setIdle()`
- [ ] Update `updateNotification()` → `repository.updatePosition()`
- [ ] Update `onDestroy()` → `repository.setIdle()` nếu cần

### Phase 3: Refactor ViewModels
- [ ] Inject `RecordingSessionRepository` vào `RecordViewModel`
- [ ] Xóa local state variables trong `RecordViewModel`
- [ ] Expose `recordingState` từ repository
- [ ] Derive `uiState` từ `recordingState`
- [ ] Simplify `onStartClick()`, `onPauseClick()`, `onStopClick()`
- [ ] Update timer logic → dùng `getElapsedMs()`
- [ ] Xóa BroadcastReceiver (không cần nữa)
- [ ] Inject `PlaybackSessionRepository` vào `TranscriptViewModel`
- [ ] Expose `playbackState` từ repository
- [ ] Derive `uiState` từ `playbackState`
- [ ] Simplify `togglePlayPause()`
- [ ] Update position update logic → dùng `playbackState`

### Phase 4: Fix Navigation
- [ ] Thêm `launchMode="singleTop"` vào `AndroidManifest.xml`
- [ ] Update `NotificationDeepLinkHandler` → dùng `FLAG_ACTIVITY_SINGLE_TOP`
- [ ] Verify `MainActivity.onNewIntent()` hoạt động đúng

### Phase 5: Testing
- [ ] Unit tests cho repositories
- [ ] Integration tests cho recording flow
- [ ] Integration tests cho playback flow
- [ ] Test notification navigation
- [ ] Test process death recovery
- [ ] Test edge cases

---

## 🎯 Success Criteria

**Migration thành công khi:**

1. ✅ **Service là source of truth** - Service update repository, ViewModel chỉ observe
2. ✅ **ViewModel recreate không mất state** - ViewModel mới observe repository → state sync đúng
3. ✅ **Navigation không làm mất state** - Tap notification → navigate → UI vẫn sync với service
4. ✅ **Process death recovery** - App bị kill → restart → UI vẫn sync với service (nếu service vẫn chạy)
5. ✅ **No duplicate state** - Không có state trong ViewModel, chỉ trong repository
6. ✅ **No race conditions** - Service update repository trước khi update local state

---

## 📝 Notes

- **Backward compatibility:** Có thể cần giữ `RecordingStateManager` cho auto-save logic (nếu cần)
- **Performance:** Repository dùng `StateFlow` → efficient, không có overhead
- **Testing:** Dễ test hơn vì repository có thể mock được
- **Future:** Có thể thêm persistence (DataStore) để survive process death hoàn toàn

---

## 🔄 Rollback Plan

Nếu migration có vấn đề:

1. **Quick rollback:** Revert các commit về trước migration
2. **Partial rollback:** Giữ repository nhưng không dùng trong ViewModel (tạm thời)
3. **Gradual migration:** Migrate từng phần (recording trước, playback sau)

---

**Kế hoạch này đảm bảo migration an toàn, không bỏ sót, và đạt chuẩn production-ready!** 🚀


