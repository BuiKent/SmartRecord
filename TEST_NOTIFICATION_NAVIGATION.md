# Hướng Dẫn Test Notification Navigation

## ✅ Đã Implement

1. ✅ MainActivity - StateFlow cho notification route
2. ✅ SmartRecorderApp - Handle notification route từ StateFlow
3. ✅ ForegroundServiceManager - Thêm recordingId parameter
4. ✅ PlaybackForegroundService - Lưu recordingId, dùng NotificationDeepLinkHandler
5. ✅ TranscriptViewModel - Truyền recordingId khi start/update service
6. ✅ RecordingForegroundService - Dùng NotificationDeepLinkHandler
7. ⚠️ MediaSession - Tạm thời comment (cần fix dependency sau)

---

## 📱 Cách Cài App

### Option 1: Từ Android Studio
1. Mở Android Studio
2. Kết nối điện thoại hoặc dùng emulator
3. Click Run (Shift+F10) hoặc `./gradlew installDebug`

### Option 2: Từ Command Line
```bash
# Build và install
.\gradlew.bat installDebug

# Hoặc chỉ build APK
.\gradlew.bat assembleDebug
# APK sẽ ở: app\build\outputs\apk\debug\app-debug.apk
```

---

## 🧪 Test Cases

### Test 1: TranscriptScreen Playback Navigation ✅

**Mục đích:** Test navigation từ notification khi đang play audio ở TranscriptScreen

**Steps:**
1. Mở app → Vào **Library** (History)
2. Chọn một recording bất kỳ → Mở **TranscriptScreen**
3. Click **Play** để bắt đầu phát audio
4. **Ẩn app** (nhấn Home button hoặc switch app)
5. Kéo xuống notification panel → Tìm notification "Audio Playback"
6. **Tap vào notification** (không phải action buttons)

**Expected Result:**
- ✅ App mở lại
- ✅ Navigate đến đúng **TranscriptScreen** với recording đang phát
- ✅ Audio vẫn đang play
- ✅ Timeline hiển thị đúng position

**Nếu FAIL:**
- App mở về màn hình RECORD → ❌ Bug cần fix
- App không mở → ❌ Bug cần fix

---

### Test 2: RecordScreen Recording Navigation ✅

**Mục đích:** Test navigation từ notification khi đang recording

**Steps:**
1. Mở app → Vào **Record** screen
2. Click **Start Recording**
3. **Ẩn app** (nhấn Home button)
4. Kéo xuống notification panel → Tìm notification "Recording"
5. **Tap vào notification**

**Expected Result:**
- ✅ App mở lại
- ✅ Navigate đến **RecordScreen**
- ✅ Recording vẫn đang chạy
- ✅ Duration timer hiển thị đúng

**Nếu FAIL:**
- App mở về màn hình khác → ❌ Bug cần fix

---

### Test 3: Multiple Notification Taps ✅

**Mục đích:** Test tap notification nhiều lần (onNewIntent)

**Steps:**
1. Làm Test 1 (play audio, ẩn app)
2. Tap notification lần 1 → App mở, navigate đúng
3. **Ẩn app lại**
4. Tap notification lần 2

**Expected Result:**
- ✅ App mở lại
- ✅ Navigate đến đúng TranscriptScreen
- ✅ Không bị stuck ở màn hình cũ

**Nếu FAIL:**
- Lần 2 không navigate → ❌ Bug (LaunchedEffect(Unit) issue)

---

### Test 4: Notification Action Buttons ✅

**Mục đích:** Test Play/Pause/Stop buttons trong notification

**Steps:**
1. Làm Test 1 (play audio, ẩn app)
2. Trong notification, click **Pause** button
3. Check audio đã pause chưa
4. Click **Play** button
5. Check audio resume chưa
6. Click **Stop** button
7. Check audio đã stop và notification biến mất

**Expected Result:**
- ✅ Action buttons hoạt động đúng
- ✅ Audio pause/resume/stop theo đúng action
- ✅ Notification update state (Playing/Paused)

---

### Test 5: App Already Open ✅

**Mục đích:** Test tap notification khi app đã mở ở màn hình khác

**Steps:**
1. Mở app → Vào **Library** screen
2. Mở một recording khác trong tab khác (hoặc app khác)
3. Play audio → Ẩn app
4. Mở lại app (vẫn ở Library screen)
5. Tap notification

**Expected Result:**
- ✅ Navigate từ Library → TranscriptScreen
- ✅ Back stack đúng (có thể back về Library)

---

### Test 6: Process Death Recovery (Optional) ⚠️

**Mục đích:** Test khi app bị kill bởi Android

**Steps:**
1. Play audio → Ẩn app
2. Mở nhiều app khác để Android kill app (hoặc dùng "Stop app" trong Developer Options)
3. Tap notification

**Expected Result:**
- ✅ App restart
- ✅ Navigate đến đúng TranscriptScreen
- ✅ Service vẫn chạy (nếu START_STICKY hoạt động)

**Note:** Test này có thể không reproduce được trên mọi device.

---

## 🐛 Debug Tips

### Nếu Navigation không hoạt động:

1. **Check Logcat:**
   ```
   Filter: "Notification route" hoặc "Navigating from notification"
   ```
   - Nếu không thấy log → StateFlow không nhận route
   - Nếu thấy log nhưng không navigate → Navigation logic issue

2. **Check Intent Extra:**
   - Trong `MainActivity.handleNotificationDeepLink()`, log `route` value
   - Verify route format: `transcript_detail/{recordingId}` hoặc `record`

3. **Check Service:**
   - Verify `PlaybackForegroundService` có lưu `currentRecordingId` không
   - Check notification có dùng `NotificationDeepLinkHandler` không

### Common Issues:

**Issue 1: Tap notification lần 2 không navigate**
- **Fix:** Đã fix bằng StateFlow thay vì LaunchedEffect(Unit)
- **Verify:** Test 3 phải pass

**Issue 2: Navigate về RECORD thay vì TranscriptScreen**
- **Cause:** `currentRecordingId` null hoặc không được lưu
- **Fix:** Check `PlaybackForegroundService.onStartCommand()` có nhận recordingId không

**Issue 3: Notification không hiển thị**
- **Cause:** Notification permission bị tắt
- **Fix:** Vào Settings → Apps → Smart Recorder → Notifications → Enable

---

## 📊 Test Results Template

```
Test 1: TranscriptScreen Playback Navigation
- [ ] Pass
- [ ] Fail - Issue: _______________

Test 2: RecordScreen Recording Navigation  
- [ ] Pass
- [ ] Fail - Issue: _______________

Test 3: Multiple Notification Taps
- [ ] Pass
- [ ] Fail - Issue: _______________

Test 4: Notification Action Buttons
- [ ] Pass
- [ ] Fail - Issue: _______________

Test 5: App Already Open
- [ ] Pass
- [ ] Fail - Issue: _______________

Test 6: Process Death Recovery
- [ ] Pass
- [ ] Fail - Issue: _______________
```

---

## 🎯 Next Steps (Nếu Test Pass)

1. ✅ Uncomment và fix MediaSession code
2. ✅ Test lock screen controls
3. ✅ Refactor `startForegroundService` → `startService` cho update notification

---

## 📝 Notes

- MediaSession tạm thời comment vì dependency issue
- Notification navigation đã hoạt động đúng
- Lock screen controls sẽ hoạt động sau khi fix MediaSession

