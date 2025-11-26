# TODO List - Smart Recorder App Improvements

## 📋 Tổng Quan

Tài liệu này liệt kê các task cần thực hiện để cải thiện UI/UX và tính năng của app Smart Recorder.

---

## 🎨 UI/UX Design Improvements (Priority: High)

### 🎯 Task UI.1: Bo tròn các khung vuông và giảm màu nền không cần thiết
- **Files:** 
  - `app/src/main/java/com/yourname/smartrecorder/ui/screens/RecordScreen.kt`
  - `app/src/main/java/com/yourname/smartrecorder/ui/screens/TranscriptScreen.kt`
  - `app/src/main/java/com/yourname/smartrecorder/ui/screens/LibraryScreen.kt`
  - `app/src/main/java/com/yourname/smartrecorder/ui/components/RecordingCard.kt`
- **Mô tả:** 
  - Bo tròn tất cả các card, button, khung chữ nhật
  - Giảm màu nền không cần thiết (background colors)
  - Tạo UI tươi sáng, đẹp hơn
- **Cách làm:**
  1. Thêm `shape = RoundedCornerShape(16.dp)` hoặc `MaterialTheme.shapes.medium` cho các Card
  2. Thêm `shape = RoundedCornerShape(12.dp)` cho các Button
  3. Xóa hoặc giảm opacity của background colors không cần thiết
  4. Sử dụng Material 3 color scheme với độ tương phản tốt
  5. Đảm bảo consistency giữa các màn hình
- **Priority:** High
- **Estimated Time:** 2-3 giờ
- **User Feedback:** "Tôi thích bo tròn và ít màu nền không cần thiết, các khung vuông chữ nhật tôi không thích"

### 🎯 Task UI.2: Bo tròn Floating Action Buttons ở Transcript Screen
- **File:** `app/src/main/java/com/yourname/smartrecorder/ui/screens/TranscriptScreen.kt`
- **Mô tả:** 
  - Bo tròn các floating action buttons (Copy, Subtitle, People)
  - Đảm bảo shape đẹp và nhất quán
- **Cách làm:**
  1. Sử dụng `FloatingActionButton` với `shape = CircleShape` (mặc định)
  2. Hoặc `ExtendedFloatingActionButton` với `shape = RoundedCornerShape(28.dp)`
  3. Đảm bảo spacing và elevation phù hợp
- **Priority:** High
- **Estimated Time:** 30 phút
- **User Feedback:** "Các icon floating ở transcript screen cũng thế --> bo tròn lại cho tôi"

### 🎯 Task UI.3: Chuyển nền tươi sáng, đẹp đẽ hơn
- **Files:** 
  - `app/src/main/java/com/yourname/smartrecorder/ui/theme/Color.kt`
  - `app/src/main/java/com/yourname/smartrecorder/ui/theme/Theme.kt`
- **Mô tả:** 
  - Cập nhật color scheme để tươi sáng hơn
  - Giảm màu xám, tăng độ tương phản
  - Tạo cảm giác fresh và modern
- **Cách làm:**
  1. Review và update Material 3 color scheme
  2. Sử dụng màu sáng hơn cho background
  3. Tăng contrast cho text và icons
  4. Test trên light và dark mode
- **Priority:** High
- **Estimated Time:** 1-2 giờ
- **User Feedback:** "Chuyển nền tươi sáng, đẹp đẽ hơn"

### 🎯 Task UI.4: Sửa logic màu cho Card Transcribing/Uploading
- **File:** `app/src/main/java/com/yourname/smartrecorder/ui/screens/RecordScreen.kt`
- **Mô tả:** 
  - Thay đổi logic màu từ pha loãng (interpolation) sang fill theo progress
  - Khi upload xong và bắt đầu: toàn màu đỏ
  - Khi progress tăng: màu xanh fill từ đầu, chiếm diện tích màu đỏ
  - Có ranh giới rõ ràng giữa xanh và đỏ
- **Cách làm:**
  1. Thay thế color interpolation bằng `LinearProgressIndicator` với 2 segments
  2. Hoặc sử dụng `Box` với 2 `Box` con (xanh và đỏ) với `fillMaxWidth(fraction = progress)`
  3. Logic:
     - `progress = 0%` → 100% đỏ
     - `progress = 33.33%` → 33.33% xanh (đầu), 66.67% đỏ (cuối)
     - `progress = 100%` → 100% xanh
  4. Sử dụng `Color(0xFF2196F3)` cho xanh, `MaterialTheme.colorScheme.error` cho đỏ
- **Priority:** High
- **Estimated Time:** 1 giờ
- **User Feedback:** 
  - "Ko phải màu bị pha loãng, đậm như này mà là theo kiểu upload xong và bắt đầu thì thanh đó toàn màu đỏ"
  - "Khi tiến trình xong được 33,3333% thì 1/3 thanh đầu sẽ xanh, còn lại đỏ, có ranh giới"
  - "% xong tới đâu thì màu xanh fill, chiếm diện tích màu đỏ tới đó"

### 🎯 Task UI.5: Bo tròn và căn giữa text cho Cards ở Record Screen
- **File:** `app/src/main/java/com/yourname/smartrecorder/ui/screens/RecordScreen.kt`
- **Mô tả:** 
  - Bo tròn các card "Upload audio file" và "Live Transcribe"
  - Căn giữa text trong card (cả icon và text)
- **Cách làm:**
  1. Thêm `shape = RoundedCornerShape(16.dp)` cho Card
  2. Sử dụng `Arrangement.Center` trong Row/Column
  3. Đảm bảo text alignment là center
  4. Test trên nhiều kích thước màn hình
- **Priority:** High
- **Estimated Time:** 30 phút
- **User Feedback:** 
  - "Mấy card ở floating ở màn hình record này cũng đang chưa bo tròn"
  - "Chữ Upload audio file và Live transcribe chưa căn giữa card"

---

## 🐛 Bug Fixes & Rare Conditions (Priority: Critical)

### 🎯 Task BUG.1: Fix Recording State Stuck khi ViewModel Cleared
- **Files:** 
  - `app/src/main/java/com/yourname/smartrecorder/ui/record/RecordViewModel.kt`
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioRecorder.kt`
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioRecorderImpl.kt`
- **Mô tả:** 
  - **Vấn đề:** Khi ViewModel bị clear (configuration change, app kill, etc.) trong khi recording đang chạy, `AudioRecorderImpl` vẫn giữ state `isRecording = true` và `MediaRecorder` vẫn đang chạy. Khi user cố start recording mới → lỗi "Recording already in progress".
  - **Nguyên nhân:** `RecordViewModel.onCleared()` chỉ stop service và auto-save, KHÔNG gọi `audioRecorder.stopRecording()` hoặc reset state. Comment nói "AudioRecorder cleanup is handled by singleton lifecycle" nhưng điều này SAI vì singleton vẫn giữ state.
  - **Evidence từ log:** 
    - Line 3635: "ViewModel cleared while recording active"
    - Line 3637: "Recording foreground service stopped"
    - Line 3640: "Service destroyed while recording"
    - Line 3820, 3841, 3861, 3889, 4096, 4140: "Recording already in progress" errors khi user cố start recording mới
- **Cách làm:**
  1. **Thêm method `forceReset()` vào AudioRecorder interface:**
     ```kotlin
     interface AudioRecorder {
         // ... existing methods
         suspend fun forceReset() // Force cleanup without saving file
     }
     ```
  2. **Implement `forceReset()` trong AudioRecorderImpl:**
     - Release MediaRecorder nếu đang chạy
     - Reset `isRecording = false`
     - Clear `outputFile = null`
     - Handle exceptions gracefully (MediaRecorder có thể đã invalid)
  3. **Sửa `RecordViewModel.onCleared()`:**
     - Nếu `isRecording = true` và `currentRecording != null`:
       - Force save recording trước (nếu có thể)
       - Gọi `audioRecorder.forceReset()` để cleanup state
       - Stop service và auto-save
     - Log warning về việc recording bị interrupt
  4. **Thêm recovery logic trong `onStartClick()`:**
     - Trước khi start, check nếu `audioRecorder.isRecording == true` nhưng ViewModel state là `isRecording = false`:
       - Gọi `forceReset()` để cleanup
       - Log rare condition
- **Priority:** Critical
- **Estimated Time:** 2-3 giờ
- **Test Cases:**
  1. **Test 1: ViewModel cleared during recording**
     - Start recording
     - Simulate ViewModel cleared (rotate screen, kill app)
     - Verify: `AudioRecorderImpl.isRecording == false`
     - Try start new recording → should succeed
  2. **Test 2: Service destroyed but recording active**
     - Start recording
     - Stop service manually
     - Verify: Recording state reset
  3. **Test 3: Start recording after rare condition**
     - Trigger rare condition (ViewModel cleared)
     - Wait a few seconds
     - Start new recording → should work without "already in progress" error
  4. **Test 4: Multiple rapid start attempts**
     - Start recording
     - Immediately clear ViewModel
     - Immediately try start new recording → should handle gracefully
- **Trade-offs:**
  - **PRO:** Fix rare condition, prevent stuck state
  - **CON:** Có thể mất recording nếu ViewModel cleared (nhưng đã có auto-save)
  - **RECOMMENDATION:** Implement với force save trước khi reset

### 🎯 Task BUG.2: Fix Playback State Stuck khi ViewModel Cleared
- **Files:** 
  - `app/src/main/java/com/yourname/smartrecorder/ui/transcript/TranscriptViewModel.kt`
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioPlayer.kt`
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioPlayerImpl.kt`
- **Mô tả:** 
  - Tương tự BUG.1 nhưng cho playback
  - `TranscriptViewModel.onCleared()` đã có logic stop playback (line 696-707), nhưng cần verify:
    - AudioPlayer state có được reset không?
    - Có exception handling đầy đủ không?
    - Có recovery logic khi start playback mới không?
- **Cách làm:**
  1. Review `TranscriptViewModel.onCleared()` - đã có stop logic
  2. Verify `AudioPlayerImpl.stop()` có reset state đầy đủ
  3. Thêm recovery logic trong `playRecording()` nếu cần
  4. Test tương tự BUG.1
- **Priority:** High
- **Estimated Time:** 1 giờ
- **Test Cases:**
  1. Start playback
  2. Clear ViewModel
  3. Verify: Playback stopped, state reset
  4. Start new playback → should work

---

## 📊 Logging & Observability (Priority: Critical)

### 🎯 Task LOG.1: Thêm Logging để Phát Hiện Rare Conditions, Leaks, Locks, Crashes
- **Mô tả:** 
  - Thêm logging chi tiết tại mọi điểm có thể xảy ra rare condition, memory leak, deadlock, hoặc crash
  - Mục tiêu: Phát hiện sớm vấn đề, debug dễ dàng, prevent production issues
- **Priority:** Critical
- **Estimated Time:** 4-6 giờ

#### 1.1. Memory Leak Detection Logging

**1.1.1. ViewModel Lifecycle Logging**
- **Files:**
  - `app/src/main/java/com/yourname/smartrecorder/ui/record/RecordViewModel.kt`
  - `app/src/main/java/com/yourname/smartrecorder/ui/transcript/TranscriptViewModel.kt`
  - `app/src/main/java/com/yourname/smartrecorder/ui/library/LibraryViewModel.kt`
- **Logging Points:**
  1. **onCleared()** - Log tất cả state khi ViewModel cleared:
     ```kotlin
     AppLogger.logLifecycle(TAG_VIEWMODEL, "RecordViewModel", "onCleared", 
         "isRecording=$isRecording, currentRecording=${currentRecording?.id}, " +
         "timerJobActive=${timerJob?.isActive}, isStarting=$isStarting, isPaused=$isPaused")
     ```
  2. **Coroutine Jobs** - Log khi job được cancel:
     ```kotlin
     timerJob?.invokeOnCompletion { cause ->
         AppLogger.logLifecycle(TAG_VIEWMODEL, "RecordViewModel", "TimerJob completed", 
             "cause=${cause?.message}, isActive=${timerJob?.isActive}")
     }
     ```
  3. **State Changes** - Log mọi state transition:
     ```kotlin
     _uiState.update { newState ->
         val oldState = _uiState.value
         AppLogger.d(TAG_VIEWMODEL, "State changed: isRecording ${oldState.isRecording} -> ${newState.isRecording}")
         newState
     }
     ```

**1.1.2. Coroutine Leak Detection**
- **Files:** Tất cả ViewModels
- **Logging Points:**
  1. **viewModelScope.launch** - Log khi coroutine start và complete:
     ```kotlin
     viewModelScope.launch {
         val jobId = System.currentTimeMillis()
         AppLogger.logLifecycle(TAG_VIEWMODEL, "Coroutine started", "jobId=$jobId, operation=$operation")
         try {
             // ... operation
         } finally {
             AppLogger.logLifecycle(TAG_VIEWMODEL, "Coroutine completed", "jobId=$jobId")
         }
     }
     ```
  2. **Job Cancellation** - Log khi job bị cancel:
     ```kotlin
     job.invokeOnCompletion { cause ->
         if (cause is CancellationException) {
             AppLogger.logRareCondition(TAG_VIEWMODEL, "Coroutine cancelled", "jobId=$jobId, reason=${cause.message}")
         }
     }
     ```

**1.1.3. Listener/Callback Leak Detection**
- **Files:**
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioPlayerImpl.kt`
  - `app/src/main/java/com/yourname/smartrecorder/core/speech/GoogleASRManager.kt`
- **Logging Points:**
  1. **Listener Registration** - Log khi register/unregister:
     ```kotlin
     setOnCompletionListener {
         AppLogger.logLifecycle(TAG_AUDIO, "CompletionListener invoked", "file=${currentFile?.absolutePath}")
         onCompletion()
     }
     ```
  2. **Listener Cleanup** - Log khi release:
     ```kotlin
     mediaPlayer?.setOnCompletionListener(null)
     AppLogger.logLifecycle(TAG_AUDIO, "CompletionListener cleared")
     ```

#### 1.2. Deadlock Detection Logging

**1.2.1. Synchronized Block Monitoring**
- **Files:**
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioRecorderImpl.kt`
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioPlayerImpl.kt`
- **Logging Points:**
  1. **Lock Acquisition** - Log khi acquire lock:
     ```kotlin
     val lockStartTime = System.currentTimeMillis()
     AppLogger.d(TAG_AUDIO, "Acquiring lock -> thread=${Thread.currentThread().name}")
     synchronized(this@AudioRecorderImpl) {
         val lockWaitTime = System.currentTimeMillis() - lockStartTime
         if (lockWaitTime > 100) {
             AppLogger.logRareCondition(TAG_AUDIO, "Long lock wait time", "waitTime=${lockWaitTime}ms")
         }
         AppLogger.d(TAG_AUDIO, "Lock acquired -> waitTime=${lockWaitTime}ms")
         // ... operation
     }
     AppLogger.d(TAG_AUDIO, "Lock released")
     ```
  2. **Lock Duration** - Log thời gian giữ lock:
     ```kotlin
     val lockDuration = System.currentTimeMillis() - lockStartTime
     if (lockDuration > 500) {
         AppLogger.logRareCondition(TAG_AUDIO, "Long lock duration", "duration=${lockDuration}ms, operation=$operation")
     }
     ```

**1.2.2. Coroutine Dispatcher Monitoring**
- **Files:** Tất cả files sử dụng `withContext`
- **Logging Points:**
  1. **Dispatcher Switch** - Log khi switch dispatcher:
     ```kotlin
     AppLogger.d(TAG_AUDIO, "Switching to Dispatchers.IO -> thread=${Thread.currentThread().name}")
     withContext(Dispatchers.IO) {
         val switchTime = System.currentTimeMillis() - startTime
         if (switchTime > 50) {
             AppLogger.logRareCondition(TAG_AUDIO, "Slow dispatcher switch", "switchTime=${switchTime}ms")
         }
         // ... operation
     }
     ```

#### 1.3. Rare Condition Detection Logging

**1.3.1. State Inconsistency Detection**
- **Files:** Tất cả ViewModels và Singletons
- **Logging Points:**
  1. **State Validation** - Log khi state không consistent:
     ```kotlin
     fun validateState() {
         val state = _uiState.value
         val actualRecording = audioRecorder.isRecording
         if (state.isRecording != actualRecording) {
             AppLogger.logRareCondition(TAG_VIEWMODEL, "State inconsistency detected", 
                 "uiState.isRecording=${state.isRecording}, audioRecorder.isRecording=$actualRecording")
         }
     }
     ```
  2. **Null State Checks** - Log khi null không expected:
     ```kotlin
     val recording = currentRecording ?: run {
         AppLogger.logRareCondition(TAG_VIEWMODEL, "currentRecording is null when expected", 
             "isRecording=${_uiState.value.isRecording}")
         return
     }
     ```

**1.3.2. Race Condition Detection**
- **Files:**
  - `app/src/main/java/com/yourname/smartrecorder/ui/record/RecordViewModel.kt`
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioRecorderImpl.kt`
- **Logging Points:**
  1. **Concurrent Operations** - Log khi detect concurrent access:
     ```kotlin
     if (isStarting || _uiState.value.isRecording) {
         AppLogger.logRareCondition(TAG_VIEWMODEL, "Concurrent start attempt", 
             "isStarting=$isStarting, isRecording=${_uiState.value.isRecording}")
         return
     }
     ```
  2. **Double Start/Stop** - Log khi start/stop được gọi nhiều lần:
     ```kotlin
     if (isRecording) {
         AppLogger.logRareCondition(TAG_AUDIO, "Double start detected", 
             "thread=${Thread.currentThread().name}, stackTrace=${Thread.currentThread().stackTrace.take(5).joinToString()}")
         throw IllegalStateException("Recording already in progress")
     }
     ```

#### 1.4. Crash Prevention Logging

**1.4.1. Null Pointer Prevention**
- **Files:** Tất cả files
- **Logging Points:**
  1. **Null Checks** - Log trước khi access nullable:
     ```kotlin
     val file = outputFile ?: run {
         AppLogger.e(TAG_AUDIO, "Null outputFile when expected", 
             "isRecording=$isRecording, mediaRecorder=${mediaRecorder != null}")
         throw IllegalStateException("No recording file")
     }
     ```
  2. **Safe Access** - Log khi safe access fails:
     ```kotlin
     mediaPlayer?.let { player ->
         // ... operation
     } ?: AppLogger.logRareCondition(TAG_AUDIO, "MediaPlayer is null when expected", 
         "currentFile=${currentFile?.absolutePath}")
     ```

**1.4.2. Illegal State Prevention**
- **Files:** Tất cả files với state machines
- **Logging Points:**
  1. **State Transitions** - Log mọi state transition:
     ```kotlin
     fun transitionTo(newState: State) {
         val oldState = currentState
         if (!isValidTransition(oldState, newState)) {
             AppLogger.e(TAG_VIEWMODEL, "Invalid state transition", 
                 "from=$oldState, to=$newState, stackTrace=${Thread.currentThread().stackTrace.take(10).joinToString()}")
             throw IllegalStateException("Invalid transition: $oldState -> $newState")
         }
         AppLogger.d(TAG_VIEWMODEL, "State transition: $oldState -> $newState")
         currentState = newState
     }
     ```

**1.4.3. Exception Logging Enhancement**
- **Files:** Tất cả try-catch blocks
- **Logging Points:**
  1. **Exception Context** - Log thêm context khi exception:
     ```kotlin
     catch (e: Exception) {
         AppLogger.e(TAG_AUDIO, "Exception in operation", e, 
             "context: isRecording=$isRecording, mediaRecorder=${mediaRecorder != null}, " +
             "outputFile=${outputFile?.absolutePath}, thread=${Thread.currentThread().name}")
         throw e
     }
     ```

#### 1.5. Resource Leak Detection Logging

**1.5.1. MediaRecorder/MediaPlayer Resource Tracking**
- **Files:**
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioRecorderImpl.kt`
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioPlayerImpl.kt`
- **Logging Points:**
  1. **Resource Creation** - Log khi tạo resource:
     ```kotlin
     val resourceId = System.currentTimeMillis()
     AppLogger.logLifecycle(TAG_AUDIO, "MediaRecorder created", "resourceId=$resourceId")
     mediaRecorder = MediaRecorder().apply {
         // ... setup
     }
     ```
  2. **Resource Release** - Log khi release:
     ```kotlin
     AppLogger.logLifecycle(TAG_AUDIO, "MediaRecorder releasing", "resourceId=$resourceId")
     mediaRecorder?.release()
     mediaRecorder = null
     AppLogger.logLifecycle(TAG_AUDIO, "MediaRecorder released", "resourceId=$resourceId")
     ```
  3. **Resource Leak Detection** - Log nếu resource không được release:
     ```kotlin
     override fun finalize() {
         if (mediaRecorder != null) {
             AppLogger.e(TAG_AUDIO, "MediaRecorder leaked! Not released before GC", 
                 "resourceId=$resourceId, isRecording=$isRecording")
         }
     }
     ```

**1.5.2. File Handle Tracking**
- **Files:** Tất cả files làm việc với File
- **Logging Points:**
  1. **File Open** - Log khi mở file:
     ```kotlin
     AppLogger.d(TAG_AUDIO, "File opened", "path=${file.absolutePath}, size=${file.length()}, exists=${file.exists()}")
     ```
  2. **File Close** - Log khi đóng file:
     ```kotlin
     AppLogger.d(TAG_AUDIO, "File closed", "path=${file.absolutePath}")
     ```

**1.5.3. Database Connection Tracking**
- **Files:**
  - `app/src/main/java/com/yourname/smartrecorder/data/local/dao/*.kt`
  - `app/src/main/java/com/yourname/smartrecorder/data/repository/*.kt`
- **Logging Points:**
  1. **Query Execution** - Log mọi query:
     ```kotlin
     @Query("SELECT * FROM recordings")
     suspend fun getAllRecordings(): List<RecordingEntity> {
         val startTime = System.currentTimeMillis()
         AppLogger.logDatabase(TAG_DATABASE, "QUERY", "recordings", "getAllRecordings")
         return try {
             val result = // ... query
             val duration = System.currentTimeMillis() - startTime
             AppLogger.logDatabase(TAG_DATABASE, "QUERY_COMPLETE", "recordings", 
                 "getAllRecordings, duration=${duration}ms, count=${result.size}")
             result
         } catch (e: Exception) {
             AppLogger.e(TAG_DATABASE, "Query failed", e, "getAllRecordings")
             throw e
         }
     }
     ```

#### 1.6. Performance Monitoring Logging

**1.6.1. Operation Duration Tracking**
- **Files:** Tất cả critical operations
- **Logging Points:**
  1. **Long Operations** - Log nếu operation quá lâu:
     ```kotlin
     val startTime = System.currentTimeMillis()
     // ... operation
     val duration = System.currentTimeMillis() - startTime
     if (duration > 1000) {
         AppLogger.logPerformance(TAG_AUDIO, "Long operation", duration, "operation=$operation")
     }
     ```

**1.6.2. Memory Usage Tracking**
- **Files:** Heavy operations (transcription, model loading)
- **Logging Points:**
  1. **Memory Before/After** - Log memory usage:
     ```kotlin
     val runtime = Runtime.getRuntime()
     val memoryBefore = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 // MB
     // ... operation
     val memoryAfter = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 // MB
     val memoryDelta = memoryAfter - memoryBefore
     if (memoryDelta > 50) {
         AppLogger.logPerformance(TAG_TRANSCRIPT, "High memory usage", 0, 
             "before=${memoryBefore}MB, after=${memoryAfter}MB, delta=${memoryDelta}MB")
     }
     ```

#### 1.7. Implementation Strategy

**Phase 1: Critical Paths (2 giờ)**
1. AudioRecorderImpl - synchronized blocks, resource tracking
2. AudioPlayerImpl - synchronized blocks, resource tracking
3. RecordViewModel - lifecycle, coroutines, state validation
4. TranscriptViewModel - lifecycle, coroutines, state validation

**Phase 2: Secondary Paths (2 giờ)**
1. LibraryViewModel - lifecycle, coroutines
2. Database operations - query tracking
3. UseCases - operation tracking
4. Services - lifecycle tracking

**Phase 3: Enhancement (1-2 giờ)**
1. Performance monitoring
2. Memory usage tracking
3. Advanced state validation
4. Log aggregation helpers

#### 1.8. Test Cases

1. **Memory Leak Test:**
   - Start recording → rotate screen → verify logs show cleanup
   - Start playback → kill app → verify logs show resource release

2. **Deadlock Test:**
   - Rapid start/stop operations → verify lock wait times logged
   - Concurrent operations → verify no deadlock

3. **State Inconsistency Test:**
   - Trigger rare condition → verify state validation logs
   - Check logs for state mismatch warnings

4. **Resource Leak Test:**
   - Create/destroy resources multiple times → verify all released
   - Check logs for leaked resources

#### 1.9. Log Analysis Tools

1. **Log Patterns to Monitor:**
   - `[RARE]` - Rare conditions
   - `[LEAK]` - Potential leaks
   - `[LOCK]` - Lock issues
   - `[PERF]` - Performance issues

2. **Automated Alerts:**
   - Count `[RARE]` logs per session
   - Alert if lock wait > 500ms
   - Alert if memory delta > 100MB
   - Alert if operation duration > 5s

---

## 🔧 Resource Management & Permissions (Priority: Critical)

### 🎯 Task RES.1: Fix Pause/Stop Logic và Resource Release
- **Files:**
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioRecorderImpl.kt`
  - `app/src/main/java/com/yourname/smartrecorder/ui/record/RecordViewModel.kt`
  - `app/src/main/java/com/yourname/smartrecorder/core/service/RecordingForegroundService.kt`
- **Mô tả:** 
  - **Vấn đề hiện tại:**
    1. **Pause không release microphone:** `MediaRecorder.pause()` chỉ pause recording nhưng vẫn giữ microphone resource. User muốn: Pause = release mic, không dùng mic nữa.
    2. **Stop logic:** Stop đã release MediaRecorder và stop service - OK, nhưng cần verify mic được release hoàn toàn.
    3. **Resource leak:** Có thể mic không được release khi pause, gây chiếm quyền thiết bị.
  - **Yêu cầu:**
    - **Pause:** Dừng recording, release microphone, nhưng giữ service và state (để resume sau)
    - **Stop:** Dừng hết - release mic, stop service, clear state
- **Cách làm:**
  1. **Sửa Pause logic:**
     - Thay vì `mediaRecorder.pause()` (không release mic)
     - Implement: `stop()` MediaRecorder → `release()` → set state `isPaused = true`
     - Giữ `outputFile` và state để resume sau
     - Log: "Microphone released on pause"
  2. **Sửa Resume logic:**
     - Tạo MediaRecorder mới với cùng `outputFile`
     - Append mode nếu có thể, hoặc tạo file mới và merge sau
     - Log: "Microphone re-acquired on resume"
  3. **Verify Stop logic:**
     - Đảm bảo `mediaRecorder.release()` được gọi
     - Đảm bảo service stopped
     - Đảm bảo state cleared
     - Log: "All resources released on stop"
  4. **Add resource tracking:**
     - Log khi acquire/release mic
     - Log khi service start/stop
     - Verify không có resource leak
- **Priority:** Critical
- **Estimated Time:** 2-3 giờ
- **Test Cases:**
  1. Start recording → Pause → Verify mic released (check system)
  2. Pause → Resume → Verify mic re-acquired
  3. Start → Stop → Verify all resources released
  4. Pause → Kill app → Verify resources released

### 🎯 Task RES.2: Cải thiện Notification và Quick Settings Tile
- **Files:**
  - `app/src/main/java/com/yourname/smartrecorder/core/service/RecordingForegroundService.kt`
  - `app/src/main/java/com/yourname/smartrecorder/core/service/PlaybackForegroundService.kt`
  - Tạo mới: `app/src/main/java/com/yourname/smartrecorder/core/service/RecordingTileService.kt`
- **Mô tả:**
  - **Vấn đề hiện tại:**
    1. **Notification IMPORTANCE_LOW:** Có thể không hiển thị rõ, user không biết app đang recording
    2. **Thiếu Quick Settings Tile:** User không thể dừng recording từ Quick Settings
    3. **Notification actions:** Chỉ có "Stop", thiếu "Pause" action
    4. **Notification visibility:** Cần hiển thị rõ ràng khi app background
  - **Yêu cầu:**
    - Notification phải hiển thị rõ ràng (IMPORTANCE_DEFAULT hoặc HIGH khi recording)
    - Thêm Quick Settings Tile để user có thể stop/pause từ notification panel
    - Thêm "Pause" action vào notification
    - Notification phải persistent và không thể dismiss khi đang recording
- **Cách làm:**
  1. **Cải thiện Notification:**
     - Đổi `IMPORTANCE_LOW` → `IMPORTANCE_DEFAULT` (hoặc `HIGH` khi recording)
     - Thêm action "Pause" bên cạnh "Stop"
     - Thêm action "Resume" khi paused
     - Set `setOngoing(true)` khi recording (không thể dismiss)
     - Set `setOngoing(false)` khi paused (có thể dismiss)
     - Hiển thị rõ status: "Recording", "Paused", "Stopped"
  2. **Thêm Quick Settings Tile:**
     - Tạo `RecordingTileService` extends `TileService`
     - Hiển thị state: Recording/Paused/Stopped
     - Action: Tap để pause/resume, Long press để stop
     - Update tile state real-time
  3. **Notification content:**
     - Hiển thị duration, file name
     - Hiển thị status rõ ràng
     - Tap notification → mở app
  4. **Background notification:**
     - Đảm bảo notification luôn hiển thị khi recording
     - Update notification mỗi giây với duration
     - Log khi notification updated
- **Priority:** High
- **Estimated Time:** 3-4 giờ
- **Dependencies:**
  - Android API 24+ for TileService
  - Notification permissions (auto-granted for foreground service)
- **Test Cases:**
  1. Start recording → Check notification hiển thị
  2. Background app → Check notification vẫn hiển thị
  3. Tap notification → Verify app opens
  4. Tap "Pause" action → Verify recording paused
  5. Tap "Stop" action → Verify recording stopped
  6. Check Quick Settings Tile → Verify state correct

### 🎯 Task RES.3: Verify Resource Release và Prevent Resource Leaks
- **Files:**
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioRecorderImpl.kt`
  - `app/src/main/java/com/yourname/smartrecorder/core/audio/AudioPlayerImpl.kt`
  - `app/src/main/java/com/yourname/smartrecorder/ui/record/RecordViewModel.kt`
  - `app/src/main/java/com/yourname/smartrecorder/ui/transcript/TranscriptViewModel.kt`
- **Mô tả:**
  - **Vấn đề:** Cần verify tất cả resources được release đúng cách:
    1. MediaRecorder/MediaPlayer được release
    2. Microphone được release
    3. Service được stop
    4. File handles được close
    5. Coroutines được cancel
  - **Yêu cầu:**
    - Log mọi resource acquisition/release
    - Verify không có resource leak
    - Test trên nhiều scenarios
- **Cách làm:**
  1. **Add resource tracking:**
     - Log khi acquire mic: "Microphone acquired"
     - Log khi release mic: "Microphone released"
     - Log khi start/stop service
     - Log khi create/destroy MediaRecorder/MediaPlayer
  2. **Add verification:**
     - Check `isRecording` state consistency
     - Check MediaRecorder/MediaPlayer null after release
     - Check service stopped
  3. **Add cleanup in onCleared:**
     - Release all resources khi ViewModel cleared
     - Stop service nếu đang chạy
     - Cancel all coroutines
  4. **Test scenarios:**
     - Normal flow: Start → Stop
     - Pause flow: Start → Pause → Resume → Stop
     - Interrupt flow: Start → Kill app → Verify cleanup
     - Multiple rapid start/stop
- **Priority:** High
- **Estimated Time:** 2 giờ
- **Test Cases:**
  1. Start → Stop → Verify all resources released
  2. Start → Pause → Stop → Verify all resources released
  3. Start → Kill app → Verify cleanup logs
  4. Multiple recordings → Verify no resource leak

---

## 🏠 1. Màn Hình Home (RecordScreen)

### 1.1. UI Improvements

#### ✅ Task 1.1.1: Bỏ nền xám ở khung wave
- **File:** `app/src/main/java/com/yourname/smartrecorder/ui/components/WaveformVisualizer.kt`
- **Mô tả:** Xóa background color xám ở Box chứa waveform
- **Cách làm:** 
  - Xóa `.background()` modifier nếu có
  - Đảm bảo waveform hiển thị trên nền trong suốt
- **Priority:** Medium
- **Estimated Time:** 5 phút

#### ✅ Task 1.1.2: Sửa chữ "Bookmark" bị cắt
- **File:** `app/src/main/java/com/yourname/smartrecorder/ui/screens/RecordScreen.kt`
- **Mô tả:** 
  - Kiểm tra và sửa layout của button Bookmark
  - Đảm bảo text không bị cắt trên màn hình nhỏ
  - Kiểm tra chức năng bookmark có hoạt động đúng không
- **Cách làm:**
  - Thêm `maxLines = 1` và `overflow = TextOverflow.Ellipsis` nếu cần
  - Hoặc đổi thành icon-only button với tooltip
  - Test trên nhiều kích thước màn hình
- **Priority:** High
- **Estimated Time:** 15 phút
- **Testing:** 
  - Test bookmark khi đang recording
  - Kiểm tra xem audio có phát được sau khi bookmark không
  - Verify logic bookmark trong RecordViewModel

#### ✅ Task 1.1.3: Đổi icon Upload thành Folder
- **File:** `app/src/main/java/com/yourname/smartrecorder/ui/screens/RecordScreen.kt`
- **Mô tả:** Icon hiện tại đã là `Icons.Default.Folder` nhưng cần verify
- **Cách làm:**
  - Verify icon hiện tại
  - Đảm bảo icon folder hiển thị rõ ràng
- **Priority:** Low
- **Estimated Time:** 2 phút

#### ✅ Task 1.1.4: Đổi tên và tích hợp Google ASR cho card "Transcribe"
- **File:** 
  - `app/src/main/java/com/yourname/smartrecorder/ui/screens/RecordScreen.kt`
  - Tạo mới: `app/src/main/java/com/yourname/smartrecorder/core/speech/GoogleASRManager.kt`
  - Tạo mới: `app/src/main/java/com/yourname/smartrecorder/ui/realtime/RealtimeASRViewModel.kt`
- **Mô tả:** 
  - Đổi tên button "Transcribe" thành tên phù hợp hơn (ví dụ: "Live Transcribe", "Real-time STT")
  - Tích hợp Google Speech Recognition API cho realtime transcription
  - Khởi động liên tục, tắt tiếng beep
- **Cách làm:**
  1. Đọc và implement theo `googleASR.md`
  2. Tạo `GoogleASRManager` với:
     - Continuous listening với auto-restart
     - Tắt beep sound (AudioManager)
     - Partial results handling
     - Error recovery
  3. Tạo ViewModel cho realtime ASR
  4. Update RecordScreen để hiển thị live text
  5. Đổi tên button và icon phù hợp
- **Priority:** High
- **Estimated Time:** 4-6 giờ
- **Dependencies:** 
  - Google Play Services
  - RECORD_AUDIO permission
- **Trade-offs:**
  - PRO: Real-time feedback, không cần internet (offline mode)
  - CON: Chỉ hoạt động trên devices có Google Play Services
  - CON: Cần xử lý error cases (device không support)

---

## 📝 2. Màn Hình Transcripts (TranscriptScreen)

### 2.1. Inline Editing

#### ✅ Task 2.1.1: Thêm icon chỉnh sửa và inline editing
- **File:** `app/src/main/java/com/yourname/smartrecorder/ui/screens/TranscriptScreen.kt`
- **Mô tả:** 
  - Thêm icon edit vào mỗi segment
  - Click vào segment → chuyển sang edit mode
  - Edit trực tiếp trên dòng, không cần dialog
  - Lưu khi click dòng khác, vùng khác, hoặc icon tích
- **Cách làm:**
  1. Thêm state `editingSegmentId: Long?` vào TranscriptUiState
  2. Thêm icon Edit vào mỗi segment row
  3. Khi click edit → chuyển Text thành TextField
  4. Implement save logic:
     - Click outside → save
     - Click check icon → save
     - Update database qua UseCase
  5. Thêm debounce để tránh save quá nhiều
- **Priority:** High
- **Estimated Time:** 2-3 giờ
- **Trade-offs Analysis:**
  - **Performance:**
    - ✅ Inline editing nhẹ hơn dialog (không tạo dialog overlay)
    - ✅ Chỉ re-render segment đang edit
    - ⚠️ Cần debounce để tránh save quá nhiều (trade-off: delay vs performance)
  - **Memory:**
    - ✅ Không leak (state được quản lý bởi ViewModel)
    - ✅ TextField chỉ tồn tại khi editing
  - **UX:**
    - ✅ Nhanh hơn dialog
    - ✅ Context rõ ràng (thấy ngay text đang edit)
    - ⚠️ Có thể nhầm lẫn nếu không có visual feedback rõ ràng
  - **Recommendation:** 
    - Implement với debounce 500ms
    - Thêm visual feedback (highlight editing segment)
    - Auto-save khi blur (click outside)

### 2.2. Floating Action Buttons

#### ✅ Task 2.2.1: Sửa UI icon Pen/People thành Floating Buttons
- **File:** `app/src/main/java/com/yourname/smartrecorder/ui/screens/TranscriptScreen.kt`
- **Mô tả:**
  - Đưa 2 nút Pen và People xuống dưới bên phải màn hình
  - Thêm nút Copy bên cạnh
  - Sửa icon Pen thành icon phù hợp hơn (timeline/subtitle)
- **Cách làm:**
  1. Tạo FloatingActionButton group ở bottom right
  2. 3 buttons: Copy, Pen (timeline/subtitle), People
  3. Sử dụng `ExtendedFloatingActionButton` hoặc `FloatingActionButton` với proper spacing
  4. Đổi icon Pen thành `Icons.Default.Subtitles` hoặc `Icons.Default.Timeline`
- **Priority:** Medium
- **Estimated Time:** 1 giờ

#### ✅ Task 2.2.2: Logic Copy button
- **File:** `app/src/main/java/com/yourname/smartrecorder/ui/screens/TranscriptScreen.kt`
- **Mô tả:**
  - Khi ở mode Pen (timeline/subtitle): Copy = copy file subtitle (giống Share → Subtitle)
  - Khi ở mode People: Copy = copy file txt (giống Share → TXT)
- **Cách làm:**
  1. Check current mode (Pen/People)
  2. Generate text tương ứng (subtitle format hoặc txt format)
  3. Copy vào clipboard
  4. Show toast notification
- **Priority:** Medium
- **Estimated Time:** 30 phút

#### ✅ Task 2.2.3: Sửa icon People mode - hiển thị speaker labels
- **File:** `app/src/main/java/com/yourname/smartrecorder/ui/screens/TranscriptScreen.kt`
- **Mô tả:** 
  - Hiện tại code đã có logic hiển thị speaker (line 477-483)
  - Vấn đề: `segment.speaker` có thể null hoặc không được detect
  - Cần kiểm tra và fix logic detect speakers
- **Cách làm:**
  1. Kiểm tra logic detect speakers trong `GenerateTranscriptUseCase`
  2. Verify `detectSpeakers()` function có được gọi không
  3. Đảm bảo segments có `speaker` field được set
  4. Nếu speaker null → hiển thị "Unknown Speaker" hoặc "Speaker ?"
  5. Test với audio có nhiều người nói
- **Priority:** High
- **Estimated Time:** 1-2 giờ
- **Note:** 
  - Code hiện tại: `if (showSpeaker && segment.speaker != null)` → chỉ hiển thị khi speaker != null
  - Cần xử lý case speaker == null để không hiển thị dòng trống

### 2.3. Bottom Navigation

#### ✅ Task 2.3.1: Bottom menu luôn về màn hình chính
- **File:** 
  - `app/src/main/java/com/yourname/smartrecorder/ui/widgets/AppBottomBar.kt`
  - `app/src/main/java/com/yourname/smartrecorder/ui/SmartRecorderApp.kt`
- **Mô tả:**
  - Khi click vào icon trong bottom menu, luôn navigate về màn hình chính tương ứng
  - Dù route hiện tại đi vòng như nào (ví dụ: Record → Transcript → click Library → về Library)
- **Cách làm:**
  1. Sử dụng `popUpTo` với `inclusive = false` để clear back stack
  2. Hoặc sử dụng `popUpTo(route) { saveState = true }` và `restoreState = true`
  3. Đảm bảo mỗi bottom menu item có navigation riêng
- **Priority:** Medium
- **Estimated Time:** 30 phút

---

## 🔧 3. Implementation Details

### 3.1. Google ASR Integration

#### Files cần tạo:
1. `app/src/main/java/com/yourname/smartrecorder/core/speech/GoogleASRManager.kt`
   - Quản lý SpeechRecognizer
   - Continuous listening với auto-restart
   - Tắt beep sound
   - Error handling

2. `app/src/main/java/com/yourname/smartrecorder/ui/realtime/RealtimeASRViewModel.kt`
   - State management cho realtime ASR
   - Live text updates
   - Integration với recording

3. Update `app/src/main/java/com/yourname/smartrecorder/ui/screens/RecordScreen.kt`
   - Hiển thị live text từ ASR
   - Update button text và icon

#### Key Features từ googleASR.md:
- ✅ Continuous listening với auto-restart
- ✅ Tắt beep sound (AudioManager.setStreamMute)
- ✅ Partial results cho low latency
- ✅ Error recovery (recreate recognizer khi cần)
- ✅ Warmup recognizer để giảm latency
- ✅ Offline mode support

### 3.2. Inline Editing Implementation

#### State Management:
```kotlin
data class TranscriptUiState(
    // ... existing fields
    val editingSegmentId: Long? = null,
    val editingText: String = ""
)
```

#### Save Strategy:
- **Debounce:** 500ms sau khi user ngừng typing
- **Auto-save:** Khi click outside hoặc blur
- **Manual save:** Click check icon
- **Cancel:** ESC key hoặc back button

#### Database Update:
- Tạo `UpdateTranscriptSegmentUseCase`
- Update segment text trong database
- Trigger recomposition để hiển thị text mới

### 3.3. Floating Buttons Layout

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // Content
    
    // Floating buttons ở bottom right
    Row(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Copy button
        FloatingActionButton(...)
        
        // Pen/Subtitle button
        FloatingActionButton(...)
        
        // People button
        FloatingActionButton(...)
    }
}
```

---

## 📊 Priority & Timeline

### Phase 1: Quick Wins (1-2 giờ)
1. ✅ Bỏ nền xám waveform
2. ✅ Sửa chữ Bookmark bị cắt
3. ✅ Verify icon Upload
4. ✅ Bottom menu navigation fix

### Phase 2: Medium Priority (3-4 giờ)
1. ✅ Floating buttons UI
2. ✅ Copy button logic
3. ✅ Speaker labels display

### Phase 3: High Priority (4-6 giờ)
1. ✅ Inline editing
2. ✅ Google ASR integration

---

## 🧪 Testing Checklist

### Home Screen:
- [ ] Waveform không có nền xám
- [ ] Bookmark button text không bị cắt trên màn hình nhỏ
- [ ] Bookmark hoạt động đúng (audio phát được sau bookmark)
- [ ] Upload icon là folder
- [ ] Realtime ASR hoạt động (live text hiển thị)
- [ ] Không có beep sound khi ASR chạy

### Transcript Screen:
- [ ] Inline editing hoạt động
- [ ] Save khi click outside
- [ ] Save khi click check icon
- [ ] Không có memory leak
- [ ] Performance tốt (không lag khi edit)
- [ ] Floating buttons hiển thị đúng vị trí
- [ ] Copy button hoạt động đúng (subtitle/txt)
- [ ] Speaker labels hiển thị trong People mode
- [ ] Bottom menu luôn về màn hình chính

---

## 📝 Notes

### Trade-offs đã phân tích:
1. **Inline Editing:**
   - ✅ Performance: Nhẹ hơn dialog
   - ✅ UX: Nhanh và trực quan
   - ⚠️ Cần debounce để tránh save quá nhiều
   - ✅ Không có memory leak (state management đúng)

2. **Google ASR:**
   - ✅ Offline support
   - ✅ Low latency với partial results
   - ⚠️ Yêu cầu Google Play Services
   - ⚠️ Cần error handling tốt

3. **Floating Buttons:**
   - ✅ Dễ access
   - ✅ Không che nội dung (bottom right)
   - ⚠️ Có thể che một phần content trên màn hình nhỏ

---

## 🚀 Next Steps

1. Bắt đầu với Phase 1 (Quick Wins)
2. Test kỹ từng feature
3. Document các thay đổi
4. Update UI/UX guide nếu cần

