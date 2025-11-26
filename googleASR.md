# Google ASR Implementation Guide

## 📋 Tổng Quan

Tài liệu này mô tả chi tiết cách app sử dụng **Google Speech Recognition API** (Android `SpeechRecognizer`) để tạo nguyên liệu (raw transcription) cho bước xử lý tiếp theo. Tài liệu tập trung vào:

- ✅ Cách khởi tạo và quản lý SpeechRecognizer
- ✅ Tối ưu Intent configuration
- ✅ Continuous listening với auto-restart
- ✅ Tắt tiếng hệ thống (mute beep)
- ✅ Lấy và xử lý kết quả (partial & final)
- ✅ Error handling và recovery
- ✅ Các tối ưu performance

**⚠️ Lưu ý:** Tài liệu này KHÔNG bao gồm logic sau ASR (SpeechAligner, matching, scoring, etc.)

---

## 🏗️ Kiến Trúc

### Core Components

```
SpeechRecognitionManager (Main Implementation)
├── SpeechRecognizer (Android API)
├── RecognitionListener (Callback Interface)
├── AudioManager (Beep Suppression)
└── Intent Configuration (Optimized Settings)
```

### File Location

- **Main Implementation:** `app/src/main/java/com/example/realtalkenglishwithAI/features/pronunciation/ui/SpeechRecognitionManager.kt`
- **Interface:** `app/src/main/java/com/example/realtalkenglishwithAI/features/home/ui/storyreading/domain/SpeechRecognizer.kt`

---

## 1. Khởi Tạo SpeechRecognizer

### 1.1. Kiểm Tra Availability

```kotlin
val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)
```

**Lưu ý:**
- Google Speech Recognition yêu cầu **Google Play Services**
- Một số thiết bị có thể không có Google Speech Recognition
- Luôn kiểm tra `isAvailable` trước khi sử dụng

### 1.2. Kiểm Tra Permission

```kotlin
val hasPermission = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.RECORD_AUDIO
) == PackageManager.PERMISSION_GRANTED
```

**CRITICAL:** Permission `RECORD_AUDIO` là **bắt buộc**. Không có permission → recognizer sẽ không hoạt động.

### 1.3. Tạo Recognizer Instance

```kotlin
private fun createRecognizer() {
    try {
        // ✅ OPTIMIZATION: Reuse existing recognizer if available and valid
        if (speechRecognizer != null && !needsRecreation) {
            ProductionLogger.d(TAG, "Reusing existing recognizer instance")
            listener.onReady(true)
            return
        }
        
        // Only destroy and recreate if needed
        if (needsRecreation) {
            speechRecognizer?.destroy()
            speechRecognizer = null
            needsRecreation = false
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }
        ProductionLogger.d(TAG, "SpeechRecognizer created successfully")
        listener.onReady(true)
    } catch (e: Exception) {
        ProductionLogger.e(TAG, "Failed to create SpeechRecognizer", e)
        listener.onReady(false)
    }
}
```

**Tối ưu:**
- ✅ **Reuse recognizer:** Không tạo mới mỗi lần, reuse instance để giảm overhead
- ✅ **Recreation flag:** Chỉ recreate khi có critical error (mark `needsRecreation = true`)
- ✅ **Error handling:** Catch exception và notify listener

### 1.4. Warmup Recognizer (Pre-initialization)

```kotlin
private fun warmupRecognizer() {
    if (isWarmedUp || speechRecognizer == null || isListeningActive) {
        return
    }
    
    mainHandler.post {
        if (isListeningActive || speechRecognizer == null) {
            return@post
        }
        
        try {
            ProductionLogger.d(TAG, "Warming up recognizer...")
            val intent = getOptimizedRecognizerIntent()
            speechRecognizer?.startListening(intent)
            
            // Cancel immediately after short delay to warm up the engine
            mainHandler.postDelayed({
                if (!isListeningActive && speechRecognizer != null) {
                    speechRecognizer?.cancel()
                    isWarmedUp = true
                    ProductionLogger.d(TAG, "Recognizer warmup complete")
                }
            }, 100)  // Very short delay, just enough to initialize
        } catch (e: Exception) {
            ProductionLogger.w(TAG, "Warmup failed (non-critical)", e)
            isWarmedUp = true
        }
    }
}
```

**Mục đích:**
- ✅ **Giảm latency:** Pre-initialize engine trước khi user click mic
- ✅ **Smooth UX:** User không phải chờ khi bắt đầu listening
- ✅ **Non-blocking:** Chỉ warmup khi recognizer không đang listening

**Timing:**
- Warmup được gọi sau 200ms sau khi tạo recognizer
- Nếu user bắt đầu listening trước khi warmup xong → warmup bị skip

---

## 2. Intent Configuration (Tối Ưu)

### 2.1. Optimized Intent Setup

```kotlin
private fun getOptimizedRecognizerIntent(): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        // 1. OPTIMIZATION: Free-form model for natural sentences
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

        // 2. Language configuration
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

        // 3. OPTIMIZATION: Partial results for low latency
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

        // 4. OPTIMIZATION: Multiple alternatives for confidence extraction
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

        // 5. CRITICAL: Offline preference (FREE, no API cost)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, shouldPreferOffline)
        
        // 6. OPTIMIZATION: Continuous listening configuration
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L)

        // 7. PRODUCTION: Biasing strings (contextual hints)
        if (isBiasingSupported && biasingStrings.isNotEmpty()) {
            ProductionLogger.d(TAG, "Applying ${biasingStrings.size} biasing strings to intent.")
            putStringArrayListExtra("android.speech.extra.BIASING_STRINGS", ArrayList(biasingStrings))
        }
    }
}
```

### 2.2. Chi Tiết Các Tham Số

#### **LANGUAGE_MODEL_FREE_FORM**
- ✅ **Mục đích:** Nhận diện câu tự nhiên (không phải command)
- ✅ **Lợi ích:** Accuracy cao hơn cho continuous speech
- ❌ **Không dùng:** `LANGUAGE_MODEL_WEB_SEARCH` (cho search queries)

#### **EXTRA_PARTIAL_RESULTS = true**
- ✅ **CRITICAL:** Bật partial results để có real-time feedback
- ✅ **Latency:** Giảm perceived latency (user thấy kết quả ngay)
- ✅ **UX:** Better user experience (không phải chờ final result)

#### **EXTRA_MAX_RESULTS = 3**
- ✅ **Mục đích:** Lấy top-3 alternatives để extract confidence
- ✅ **Alternatives:** Dùng cho matching (so khớp với expected text)
- ✅ **Balance:** 3 là số lượng hợp lý (không quá nhiều, không quá ít)

#### **Continuous Listening Parameters**

| Parameter | Value | Mục Đích |
|-----------|-------|----------|
| `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` | 2500ms | Thời gian im lặng để kết thúc câu |
| `EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS` | 2000ms | Thời gian im lặng có thể kết thúc |
| `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS` | 10000ms | Thời gian tối thiểu để nhận diện |

**Lưu ý:**
- ⚠️ Các giá trị này ảnh hưởng đến khi nào ASR trả về final result
- ⚠️ Quá ngắn → nhiều false positives
- ⚠️ Quá dài → user phải chờ lâu

#### **Biasing Strings (Contextual Hints)**

```kotlin
if (isBiasingSupported && biasingStrings.isNotEmpty()) {
    putStringArrayListExtra("android.speech.extra.BIASING_STRINGS", ArrayList(biasingStrings))
}
```

**Mục đích:**
- ✅ **Context-aware:** Tăng accuracy cho từ trong context (ví dụ: từ trong story)
- ✅ **Dynamic:** Có thể update biasing strings trong lúc listening (graceful restart)
- ⚠️ **Limitation:** Không phải tất cả devices hỗ trợ (fallback nếu không support)

**Cách update biasing:**
```kotlin
override fun updateBiasingStrings(newStrings: List<String>) {
    val distinctNewStrings = newStrings.distinct()
    if (biasingStrings == distinctNewStrings) return

    biasingStrings = distinctNewStrings
    ProductionLogger.d(TAG, "Updating ASR bias list with ${biasingStrings.size} words.")

    if (isListeningActive && isBiasingSupported) {
        restartListeningGracefully()  // Restart với biasing mới
    }
}
```

---

## 3. Continuous Listening với Auto-Restart

### 3.1. Bắt Đầu Listening

```kotlin
override fun startListening() {
    if (isListeningActive) return

    // ✅ FIX: Ensure recognizer exists before starting
    ensureRecognizer()

    // CRITICAL: Check permission before starting
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) {
        ProductionLogger.e(TAG, "RECORD_AUDIO permission not granted!")
        listener.onError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, isCritical = true)
        return
    }

    ProductionLogger.d(TAG, "startListening: User initiated start.")
    isListeningActive = true
    resetAllCounters()
    partialResultsCache = emptyList()
    resetDebounceState()
    
    internalStart()
}
```

### 3.2. Internal Start Logic

```kotlin
private fun internalStart() {
    mainHandler.post {
        if (!isAvailable || !isListeningActive) return@post
        lastStartTime = System.currentTimeMillis()
        
        // ✅ ENHANCED LOGGING: Increment sessionId and record session start time
        sessionId++
        sessionStartTime = System.currentTimeMillis()
        
        listener.onStateChanged(RecognitionState.LISTENING)
        muteBeep()  // ✅ CRITICAL: Mute beep trước khi start

        try {
            speechRecognizer?.startListening(getOptimizedRecognizerIntent())
        } catch (e: Exception) {
            ProductionLogger.e(TAG, "startListening failed", e)
            unmuteAllBeepStreams()
        }
    }
}
```

### 3.3. Auto-Restart Loop

**Nguyên tắc:** Sau mỗi final result hoặc error recoverable → tự động restart để tiếp tục listening.

#### **Restart sau Final Results**

```kotlin
override fun onResults(results: Bundle?) {
    if (!isListeningActive) return
    resetAllCounters()

    if (results == null) {
        restartListeningLoop()  // ✅ Restart nếu không có results
        return
    }

    // ... process results ...
    
    // Clear cache and restart
    partialResultsCache = emptyList()
    restartListeningLoop()  // ✅ CRITICAL: Restart để tiếp tục listening
}
```

#### **Restart sau Recoverable Errors**

```kotlin
override fun onError(error: Int) {
    // ... error handling ...
    
    // Handle recoverable errors
    val isRecoverable = error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_AUDIO
    
    if (isRecoverable) {
        handleRecoverableError()
        isRestarting = true
        listener.onRestartStateChanged(true)
        restartListeningLoop()  // ✅ Restart sau recoverable error
        return
    }
}
```

#### **Restart Function**

```kotlin
private fun restartListeningLoop() {
    if (isListeningActive) {
        internalStart()  // ✅ Simple: Chỉ cần gọi internalStart() lại
    }
}
```

**Lưu ý:**
- ✅ **Seamless:** User không cần click mic lại
- ✅ **Continuous:** Listening liên tục cho đến khi user stop
- ✅ **State management:** Chỉ restart nếu `isListeningActive = true`

### 3.4. Graceful Restart (Cho Biasing Update)

```kotlin
private fun restartListeningGracefully() {
    if (!isListeningActive || !isBiasingSupported) return

    mainHandler.post {
        ProductionLogger.d(TAG, "Initiating graceful restart for biasing update...")
        isPerformingGracefulRestart = true
        isRestarting = true
        listener.onRestartStateChanged(true)
        speechRecognizer?.cancel()  // ✅ Cancel trước, sau đó restart
    }
}
```

**Flow:**
1. Cancel recognizer hiện tại
2. `onError(ERROR_CLIENT)` được gọi (expected)
3. Ignore error và restart sau 250ms delay
4. `onReadyForSpeech()` → restart complete

---

## 4. Tắt Tiếng Hệ Thống (Mute Beep)

### 4.1. Vấn Đề

Google Speech Recognition phát **beep sound** khi:
- Bắt đầu listening (`onReadyForSpeech`)
- Kết thúc recognition (`onResults`)

**Vấn đề:**
- ❌ **Distracting:** Beep làm gián đoạn user experience
- ❌ **Noisy:** Nhiều beep trong continuous listening
- ❌ **Unprofessional:** Không phù hợp với production app

### 4.2. Giải Pháp: Mute System Sounds

```kotlin
private fun muteBeep() {
    if (isMuted) return
    ProductionLogger.d(TAG, "Muting streams with strategy: $beepStrategy")
    try {
        // ✅ CRITICAL: Mute notification stream (beep sound)
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_NOTIFICATION, 
            AudioManager.ADJUST_MUTE, 
            0
        )
        
        // ✅ HEAVY_DUTY: Mute system stream nếu cần
        if (beepStrategy == BeepSuppressionStrategy.HEAVY_DUTY) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_SYSTEM, 
                AudioManager.ADJUST_MUTE, 
                0
            )
        }
        isMuted = true
    } catch (se: SecurityException) {
        ProductionLogger.w(TAG, "Mute not allowed by system policy.", se)
    }
}
```

### 4.3. Unmute Khi Stop

```kotlin
private fun unmuteAllBeepStreams() {
    if (!isMuted) return
    ProductionLogger.d(TAG, "Unmuting all streams.")
    try {
        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
        audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        isMuted = false
    } catch (se: SecurityException) {
        ProductionLogger.w(TAG, "Unmute not allowed by system policy.", se)
    }
}
```

### 4.4. Adaptive Strategy

```kotlin
private enum class BeepSuppressionStrategy { 
    DEFAULT,      // Chỉ mute notification stream
    HEAVY_DUTY    // Mute cả system stream
}
```

**Logic:**
- **DEFAULT:** Bắt đầu với strategy nhẹ
- **HEAVY_DUTY:** Escalate nếu có consecutive quick failures (< 1500ms)
- **Persistence:** Lưu strategy vào SharedPreferences để reuse

**Khi nào escalate:**
```kotlin
private fun handleRecoverableError() {
    val timeSinceStart = System.currentTimeMillis() - lastStartTime
    if (beepStrategy != BeepSuppressionStrategy.HEAVY_DUTY && timeSinceStart < 1500) {
        quickFailureCount++
        if (quickFailureCount >= 2) {
            ProductionLogger.w(TAG, "Consecutive quick failures. Escalating to HEAVY_DUTY strategy.")
            beepStrategy = BeepSuppressionStrategy.HEAVY_DUTY
            saveStrategy(beepStrategy)
            quickFailureCount = 0
        }
    } else {
        quickFailureCount = 0
    }
}
```

### 4.5. Timing

**Mute:**
- ✅ Gọi `muteBeep()` **trước** `startListening()`
- ✅ Mute được giữ trong suốt listening session

**Unmute:**
- ✅ Gọi `unmuteAllBeepStreams()` khi:
  - User stop listening
  - Critical error xảy ra
  - Recognizer bị destroy

---

## 5. Lấy Kết Quả Từ ASR

### 5.1. Partial Results (Real-time)

```kotlin
override fun onPartialResults(partialResults: Bundle?) {
    if (!isListeningActive) return
    resetAllCounters()

    if (partialResults == null) return

    // 🔥 P1.4: Adaptive Smart Debounce với Burst Detection
    val now = System.currentTimeMillis()
    val timeSinceLastPartial = now - lastPartialTime
    
    // Detect burst (multiple partials in quick succession)
    if (timeSinceLastPartial < 200L) {
        partialBurstCount++
    } else {
        partialBurstCount = 0
    }
    lastPartialTime = now
    
    // Calculate adaptive debounce delay
    val debounceDelay = when {
        partialBurstCount >= 5 -> 100L  // Heavy burst → wait longer
        partialBurstCount >= 3 -> 50L   // Moderate burst → short wait
        else -> 0L                      // Normal → no delay (realtime)
    }
    
    if (debounceDelay > 0L) {
        // Debounce: Schedule delayed processing
        partialDebouncer.postDelayed({
            if (isListeningActive && pendingPartialBundle != null) {
                processPartialResultsInternal(pendingPartialBundle!!)
                pendingPartialBundle = null
            }
        }, debounceDelay)
    } else {
        // Process immediately (no burst detected)
        processPartialResultsInternal(partialResults)
    }
}
```

**Tối ưu:**
- ✅ **Debounce:** Giảm spam khi ASR trả về nhiều partial results liên tiếp
- ✅ **Burst detection:** Phát hiện burst và delay processing
- ✅ **Adaptive:** Delay phụ thuộc vào mức độ burst

### 5.2. Process Partial Results

```kotlin
private fun processPartialResultsInternal(partialResults: Bundle) {
    // Extract tokens với confidence
    val partialTokens = extractTokensWithConfidence(partialResults)
    
    // Skip empty results early
    if (partialTokens.isEmpty()) {
        return
    }
    
    // Cache for merging with final results
    partialResultsCache = partialTokens

    // Apply noise filtering
    val cleanedTokens = NoiseFilter.clean(partialTokens)
    
    if (cleanedTokens.isEmpty()) {
        return
    }

    // ✅ OPTIMIZATION: Skip duplicate partial results
    val partialText = cleanedTokens.joinToString(" ") { it.text }
    if (partialText == lastSentPartial) {
        duplicateSkipCount++
        return  // Skip duplicate
    }
    
    lastSentPartial = partialText
    duplicateSkipCount = 0

    // Send to listener
    listener.onPartialResults(cleanedTokens)
}
```

**Tối ưu:**
- ✅ **Duplicate detection:** Skip duplicate partial results
- ✅ **Noise filtering:** Lọc filler words, low confidence tokens
- ✅ **Caching:** Cache partial results để merge với final results

### 5.3. Final Results

```kotlin
override fun onResults(results: Bundle?) {
    if (!isListeningActive) return
    resetAllCounters()

    if (results == null) {
        restartListeningLoop()
        return
    }

    // Extract tokens với confidence
    val finalTokens = extractTokensWithConfidence(results)
    
    // ✅ OPTIMIZATION: Merge với partial results cache
    val mergedTokens = if (partialResultsCache.isNotEmpty()) {
        ProductionLogger.d(TAG, "Merging partialCache=${partialResultsCache.size} tokens, finalTokens=${finalTokens.size} tokens")
        mergeResults(partialResultsCache, finalTokens)
    } else {
        finalTokens
    }

    // Apply noise filtering
    val cleanedTokens = NoiseFilter.clean(mergedTokens)

    if (cleanedTokens.isNotEmpty()) {
        listener.onFinalResults(cleanedTokens)
    }

    // Clear cache and restart
    partialResultsCache = emptyList()
    restartListeningLoop()
}
```

**Tối ưu:**
- ✅ **Merge:** Merge partial + final để có kết quả tốt hơn
- ✅ **Confidence boost:** Boost confidence nếu token xuất hiện trong cả partial và final
- ✅ **Noise filtering:** Lọc noise trước khi gửi cho listener

### 5.4. Extract Tokens với Confidence

```kotlin
private fun extractTokensWithConfidence(results: Bundle): List<RecognizedToken> {
    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?: return emptyList()

    // ✅ TOP-3 ALTERNATIVES: Extract top-3 alternatives từ ASR
    val top3Alternatives = matches.take(3)
    val confidenceScores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
    
    // Get top result and confidence
    val topResult = top3Alternatives.firstOrNull() ?: return emptyList()
    val topConfidence = confidenceScores?.firstOrNull() ?: estimateConfidenceFromRank(0)

    // Split top result into tokens
    val topTokens = topResult.split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    
    // ✅ TOP-3 ALTERNATIVES: Extract alternatives từ top-2 và top-3
    return topTokens.mapIndexed { tokenIndex, tokenText ->
        val wordAlternatives = mutableListOf<String>()
        
        // Extract từ top-2 (index 1) nếu exists
        if (top3Alternatives.size > 1) {
            val alt2Tokens = top3Alternatives[1].split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokenIndex < alt2Tokens.size) {
                val alt2Word = alt2Tokens[tokenIndex]
                if (alt2Word.lowercase() != tokenText.lowercase()) {
                    wordAlternatives.add(alt2Word)
                }
            }
        }
        
        // Extract từ top-3 (index 2) nếu exists
        if (top3Alternatives.size > 2) {
            val alt3Tokens = top3Alternatives[2].split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokenIndex < alt3Tokens.size) {
                val alt3Word = alt3Tokens[tokenIndex]
                if (alt3Word.lowercase() != tokenText.lowercase() && 
                    (wordAlternatives.isEmpty() || alt3Word.lowercase() != wordAlternatives[0].lowercase())) {
                    wordAlternatives.add(alt3Word)
                }
            }
        }
        
        RecognizedToken(
            text = tokenText,
            confidence = topConfidence,
            alternatives = wordAlternatives
        )
    }
}
```

**Mục đích:**
- ✅ **Extract top-3 alternatives:** Lấy top-3 kết quả từ ASR
- ✅ **Token-level alternatives:** Extract alternatives cho từng token
- ✅ **Confidence:** Lấy confidence score từ ASR hoặc estimate từ rank

**RecognizedToken Structure:**
```kotlin
data class RecognizedToken(
    val text: String,                    // Main text
    val confidence: Float,                // Confidence score (0.0 - 1.0)
    val alternatives: List<String>       // Alternative words từ top-2, top-3
)
```

---

## 6. Error Handling và Recovery

### 6.1. Error Types

| Error Code | Constant | Mô Tả | Recoverable? |
|------------|----------|-------|--------------|
| 2 | `ERROR_CLIENT` | Client-side error | ✅ (Graceful restart) |
| 3 | `ERROR_SERVER` | Server error | ❌ Critical |
| 4 | `ERROR_RECOGNIZER_BUSY` | Recognizer đang busy | ✅ (Retry) |
| 5 | `ERROR_INSUFFICIENT_PERMISSIONS` | Không có permission | ❌ Critical |
| 6 | `ERROR_NETWORK_TIMEOUT` | Network timeout | ❌ Critical |
| 7 | `ERROR_NETWORK` | Network error | ❌ Critical |
| 8 | `ERROR_AUDIO` | Audio recording error | ✅ (Retry) |
| 9 | `ERROR_SPEECH_TIMEOUT` | Không có speech input | ✅ (Retry) |
| 10 | `ERROR_NO_MATCH` | Không match được | ✅ (Retry) |
| 13 | Unknown error | Unknown error | ❌ Critical |

### 6.2. Error Handling Logic

```kotlin
override fun onError(error: Int) {
    if (!isListeningActive) {
        // Ignore errors khi đã stop
        unmuteAllBeepStreams()
        return
    }
    
    // Handle graceful restart
    if (isPerformingGracefulRestart && error == SpeechRecognizer.ERROR_CLIENT) {
        ProductionLogger.d(TAG, "Graceful restart: Ignored expected ERROR_CLIENT.")
        isPerformingGracefulRestart = false
        mainHandler.postDelayed({ restartListeningLoop() }, 250L)
        return
    }
    
    // Handle recognizer busy (biasing issue)
    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
        busyErrorCounter++
        
        if (isBiasingSupported && busyErrorCounter >= 1) {
            // Disable biasing permanently nếu quá nhiều busy errors
            ProductionLogger.e(TAG, "Too many busy errors. Disabling biasing feature permanently.")
            isBiasingSupported = false
            prefs.edit().putBoolean(PREF_KEY_BIASING_SUPPORT, false).apply()
            listener.onBiasingDisabled()
            isRestarting = true
            listener.onRestartStateChanged(true)
            restartListeningLoop()
            return
        }
        
        // Retry sau 300ms
        isRestarting = true
        listener.onRestartStateChanged(true)
        mainHandler.postDelayed({ restartListeningLoop() }, 300L)
        return
    }
    
    // Handle audio errors
    if (error == SpeechRecognizer.ERROR_AUDIO) {
        consecutiveAudioErrorCount++
        logDetailedAudioError()
    }
    
    // Handle recoverable errors
    val isRecoverable = error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_AUDIO
    
    if (isRecoverable) {
        if (error == SpeechRecognizer.ERROR_NO_MATCH) {
            listener.onError(error, isCritical = false)
        }
        handleRecoverableError()
        isRestarting = true
        listener.onRestartStateChanged(true)
        restartListeningLoop()
        return
    }
    
    // Critical error
    ProductionLogger.e(TAG, "Critical speech error: $error. Shutting down.")
    unmuteAllBeepStreams()
    isListeningActive = false
    needsRecreation = true  // ✅ Mark recognizer for recreation
    listener.onError(error, isCritical = true)
    listener.onStateChanged(RecognitionState.ERROR)
}
```

### 6.3. Recovery Strategies

#### **Recoverable Errors → Auto Restart**
- ✅ `ERROR_SPEECH_TIMEOUT`: Không có speech → restart
- ✅ `ERROR_NO_MATCH`: Không match được → restart
- ✅ `ERROR_AUDIO`: Audio error → restart

#### **Recognizer Busy → Retry với Delay**
- ✅ Retry sau 300ms
- ✅ Nếu quá nhiều busy errors → disable biasing

#### **Critical Errors → Stop và Recreate**
- ❌ `ERROR_SERVER`: Server error → stop
- ❌ `ERROR_NETWORK`: Network error → stop
- ❌ `ERROR_INSUFFICIENT_PERMISSIONS`: Permission → stop
- ❌ Error 13 (Unknown): Unknown error → stop

**Recreation:**
- ✅ Mark `needsRecreation = true` khi có critical error
- ✅ Next time `createRecognizer()` → recreate instance mới

---

## 7. Các Tối Ưu Khác

### 7.1. Duplicate Detection

```kotlin
// ✅ OPTIMIZATION: Skip duplicate partial results
if (partialText == lastSentPartial) {
    duplicateSkipCount++
    if (duplicateSkipCount % 10 == 0) {
        ProductionLogger.d(TAG, "Skipped $duplicateSkipCount duplicate partial results")
    }
    return
}

lastSentPartial = partialText
duplicateSkipCount = 0
```

**Lợi ích:**
- ✅ Giảm CPU usage (không process duplicate)
- ✅ Giảm memory usage (ít token objects)
- ✅ Giảm UI updates (ít recompositions)
- ✅ Cleaner logs

### 7.2. Noise Filtering

```kotlin
object NoiseFilter {
    private val fillerWords = setOf(
        "uh", "um", "er", "ah", "oh", "mm", "hmm", "mhm",
        "the the", "a a", "and and",
        "huh", "hah", "eh"
    )
    
    private const val MIN_CONFIDENCE_THRESHOLD = 0.35f
    
    fun clean(tokens: List<RecognizedToken>): List<RecognizedToken> {
        return tokens
            .let { filterTokens(it) }
            .let { removeRepeatedTokens(it) }
    }
    
    private fun filterTokens(tokens: List<RecognizedToken>): List<RecognizedToken> {
        return tokens.filter { token ->
            val normalized = token.text.lowercase().trim()
            
            // Remove filler words
            if (normalized in fillerWords) return@filter false
            
            // Remove very short tokens with low confidence
            if (normalized.length <= 1 && token.confidence < 0.5f) return@filter false
            
            // Remove low confidence tokens
            if (token.confidence < MIN_CONFIDENCE_THRESHOLD) return@filter false
            
            // Remove punctuation-only tokens
            if (normalized.replace(Regex("[^a-z0-9]"), "").isEmpty()) return@filter false
            
            true
        }
    }
}
```

**Lợi ích:**
- ✅ Lọc filler words ("uh", "um", etc.)
- ✅ Lọc low confidence tokens
- ✅ Lọc punctuation-only tokens
- ✅ Remove repeated tokens

### 7.3. Session Tracking

```kotlin
// ✅ ENHANCED LOGGING: SessionId cho correlating bursts/restarts
private var sessionId = 0
private var sessionStartTime = 0L

// Trong internalStart()
sessionId++
sessionStartTime = System.currentTimeMillis()
```

**Mục đích:**
- ✅ **Correlation:** Track các events trong cùng session
- ✅ **Debugging:** Dễ debug khi có vấn đề
- ✅ **Analytics:** Có thể dùng cho analytics

### 7.4. State Management

**Flags quan trọng:**
- `isListeningActive`: Recognizer đang listening?
- `isMuted`: Beep đã được mute?
- `isRestarting`: Đang trong quá trình restart?
- `isPerformingGracefulRestart`: Đang graceful restart?
- `needsRecreation`: Cần recreate recognizer?
- `isWarmedUp`: Recognizer đã được warmup?

**Lưu ý:**
- ✅ Tất cả flags đều `@Volatile` để thread-safe
- ✅ Reset flags khi cần thiết
- ✅ Check flags trước khi thực hiện operations

---

## 8. Best Practices

### 8.1. Lifecycle Management

```kotlin
override fun destroy() {
    ProductionLogger.d(TAG, "destroy() called — cleaning up")
    isListeningActive = false
    isPerformingGracefulRestart = false
    isWarmedUp = false
    needsRecreation = true
    mainHandler.removeCallbacksAndMessages(null)
    
    mainHandler.post {
        speechRecognizer?.destroy()
    }
    unmuteAllBeepStreams()
}
```

**Lưu ý:**
- ✅ **Always destroy:** Gọi `destroy()` khi không dùng nữa
- ✅ **Cleanup:** Remove callbacks, unmute, reset flags
- ✅ **Thread safety:** Post destroy operation lên main thread

### 8.2. Permission Handling

```kotlin
// ✅ CRITICAL: Check permission TRƯỚC khi start
val hasPermission = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.RECORD_AUDIO
) == PackageManager.PERMISSION_GRANTED

if (!hasPermission) {
    listener.onError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, isCritical = true)
    return
}
```

**Lưu ý:**
- ✅ Check permission ở **init time** và **start time**
- ✅ Notify listener nếu không có permission
- ✅ Không start nếu không có permission

### 8.3. Thread Safety

```kotlin
// ✅ Tất cả operations trên main thread
mainHandler.post {
    // Operations here
}

// ✅ Volatile flags cho thread safety
@Volatile private var isListeningActive = false
@Volatile private var isMuted = false
```

**Lưu ý:**
- ✅ SpeechRecognizer operations phải trên main thread
- ✅ Use Handler để post operations
- ✅ Volatile flags cho state management

### 8.4. Error Recovery

```kotlin
// ✅ Recoverable errors → auto restart
if (isRecoverable) {
    handleRecoverableError()
    restartListeningLoop()
    return
}

// ✅ Critical errors → stop và recreate
if (isCritical) {
    needsRecreation = true
    isListeningActive = false
    listener.onError(error, isCritical = true)
}
```

**Lưu ý:**
- ✅ Phân biệt recoverable vs critical errors
- ✅ Auto restart cho recoverable errors
- ✅ Stop và recreate cho critical errors

---

## 9. Tóm Tắt

### ✅ Điểm Mạnh

1. **Continuous Listening:** Auto-restart sau mỗi result/error
2. **Beep Suppression:** Tắt tiếng hệ thống để UX tốt hơn
3. **Optimized Intent:** Configuration tối ưu cho accuracy và latency
4. **Error Recovery:** Tự động recover từ recoverable errors
5. **Performance:** Nhiều tối ưu (warmup, debounce, duplicate detection, noise filtering)
6. **Alternatives Extraction:** Lấy top-3 alternatives để matching tốt hơn

### ⚠️ Lưu Ý

1. **Permission:** Luôn check `RECORD_AUDIO` permission
2. **Thread Safety:** Tất cả operations trên main thread
3. **Lifecycle:** Luôn destroy recognizer khi không dùng
4. **Error Handling:** Phân biệt recoverable vs critical errors
5. **State Management:** Quản lý flags cẩn thận

### 📝 Checklist

- [x] Check availability trước khi tạo recognizer
- [x] Check permission trước khi start
- [x] Mute beep trước khi start listening
- [x] Auto-restart sau final results
- [x] Auto-restart sau recoverable errors
- [x] Recreate recognizer sau critical errors
- [x] Unmute beep khi stop/destroy
- [x] Extract top-3 alternatives
- [x] Filter noise và duplicates
- [x] Track session để debugging

---

## 📚 References

- **Main Implementation:** `SpeechRecognitionManager.kt`
- **Interface:** `SpeechRecognizer.kt`
- **Model:** `RecognizedToken.kt`
- **Android Docs:** [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- **Android Docs:** [RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent)

---

**Last Updated:** 2025-01-21  
**Version:** 1.0

