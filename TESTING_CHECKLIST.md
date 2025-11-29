# Testing Checklist - Repository Pattern Migration

## ✅ Đã test
- [x] Recording pause/resume ở RecordScreen
- [x] Playback pause/resume ở LibraryScreen và TranscriptScreen
- [x] Playback cho recording mới thu âm ở TranscriptScreen
- [x] Cross-screen playback sync
- [x] Recording timer auto-start khi navigate back từ LibraryScreen/HistoryScreen
- [x] Recording stop button hoạt động sau khi navigate away và quay lại
- [x] Recording UI sync đúng khi navigate từ media control notification
- [x] Recording currentRecording restore từ repository state khi ViewModel recreate

---

## 🔔 Notification Navigation Tests

### Recording Notification
1. **Start recording → Ẩn app → Tap notification**
   - [x] Navigate đến RecordScreen
   - [x] UI sync đúng (đang recording, timer chạy)
   - [x] Có thể pause/resume từ UI
   - [x] Có thể stop từ UI

2. **Start recording → Pause → Ẩn app → Tap notification**
   - [ ] Navigate đến RecordScreen
   - [ ] UI sync đúng (đang paused, timer dừng)
   - [ ] Có thể resume từ UI
   - [ ] Có thể stop từ UI

3. **Start recording → Ẩn app → Tap pause button trong notification**
   - [ ] Recording pause
   - [ ] Notification update (icon đổi sang play)
   - [ ] Tap notification → Navigate đến RecordScreen → UI sync đúng (paused)

4. **Start recording → Pause → Ẩn app → Tap play button trong notification**
   - [ ] Recording resume
   - [ ] Notification update (icon đổi sang pause)
   - [ ] Tap notification → Navigate đến RecordScreen → UI sync đúng (recording)

5. **Start recording → Ẩn app → Tap stop button trong notification**
   - [ ] Recording stop
   - [ ] Notification biến mất
   - [ ] Navigate đến RecordScreen → UI sync đúng (idle)

### Playback Notification (TranscriptScreen)
1. **Start playback ở TranscriptScreen → Ẩn app → Tap notification**
   - [ ] Navigate đến TranscriptScreen với đúng recordingId
   - [ ] UI sync đúng (đang playing, position đúng)
   - [ ] Có thể pause/resume từ UI
   - [ ] Có thể seek từ UI

2. **Start playback → Pause → Ẩn app → Tap notification**
   - [ ] Navigate đến TranscriptScreen với đúng recordingId
   - [ ] UI sync đúng (đang paused, position đúng)
   - [ ] Có thể resume từ UI

3. **Start playback → Ẩn app → Tap pause button trong notification**
   - [ ] Playback pause
   - [ ] Notification update (icon đổi sang play)
   - [ ] Tap notification → Navigate đến TranscriptScreen → UI sync đúng (paused)

4. **Start playback → Pause → Ẩn app → Tap play button trong notification**
   - [ ] Playback resume
   - [ ] Notification update (icon đổi sang pause)
   - [ ] Tap notification → Navigate đến TranscriptScreen → UI sync đúng (playing)

5. **Start playback → Ẩn app → Tap stop button trong notification**
   - [ ] Playback stop
   - [ ] Notification biến mất
   - [ ] Navigate đến TranscriptScreen → UI sync đúng (idle)

### Playback Notification (LibraryScreen)
1. **Start playback ở LibraryScreen → Ẩn app → Tap notification**
   - [ ] Navigate đến TranscriptScreen với đúng recordingId
   - [ ] UI sync đúng (đang playing, position đúng)
   - [ ] Có thể pause/resume từ UI
   - [ ] Có thể seek từ UI

2. **Start playback → Pause → Ẩn app → Tap notification**
   - [ ] Navigate đến TranscriptScreen với đúng recordingId
   - [ ] UI sync đúng (đang paused, position đúng)
   - [ ] Có thể resume từ UI

3. **Start playback → Ẩn app → Tap pause button trong notification**
   - [ ] Playback pause
   - [ ] Notification update (icon đổi sang play)
   - [ ] Tap notification → Navigate đến TranscriptScreen → UI sync đúng (paused)

4. **Start playback → Pause → Ẩn app → Tap play button trong notification**
   - [ ] Playback resume
   - [ ] Notification update (icon đổi sang pause)
   - [ ] Tap notification → Navigate đến TranscriptScreen → UI sync đúng (playing)

---

## 🔄 Cross-Screen State Sync Tests

### Recording State
1. **Start recording ở RecordScreen → Navigate to LibraryScreen → Navigate back**
   - [x] UI sync đúng (đang recording, timer chạy)
   - [x] Có thể pause/resume từ UI
   - [x] Có thể stop từ UI

2. **Start recording → Pause → Navigate away → Navigate back**
   - [ ] UI sync đúng (đang paused, timer dừng)
   - [ ] Có thể resume từ UI

### Playback State
1. **Start playback ở LibraryScreen → Navigate to TranscriptScreen (cùng recording)**
   - [ ] UI sync đúng (đang playing, position đúng)
   - [ ] Có thể pause/resume từ TranscriptScreen
   - [ ] Position updates đúng

2. **Start playback ở LibraryScreen → Navigate to TranscriptScreen (recording khác)**
   - [ ] TranscriptScreen không hiển thị playback state (vì recording khác)
   - [ ] Có thể play recording mới từ TranscriptScreen
   - [ ] Playback cũ stop, playback mới start

3. **Start playback ở TranscriptScreen → Navigate to LibraryScreen**
   - [ ] Card của recording đang play hiển thị playback state
   - [ ] Có thể pause/resume từ LibraryScreen
   - [ ] Position updates đúng

4. **Start playback ở TranscriptScreen → Navigate to LibraryScreen → Navigate back**
   - [ ] UI sync đúng (đang playing, position đúng)
   - [ ] Có thể pause/resume từ UI

---

## 🔄 Process Death Recovery Tests

### Recording
1. **Start recording → Kill app (swipe away) → Restart app**
   - [ ] Service vẫn chạy (recording tiếp tục)
   - [ ] Navigate đến RecordScreen → UI sync đúng (đang recording)
   - [ ] Có thể pause/resume từ UI
   - [ ] Có thể stop từ UI

2. **Start recording → Pause → Kill app → Restart app**
   - [ ] Service vẫn chạy (recording paused)
   - [ ] Navigate đến RecordScreen → UI sync đúng (đang paused)
   - [ ] Có thể resume từ UI

### Playback
1. **Start playback → Kill app → Restart app**
   - [ ] Service vẫn chạy (playback tiếp tục)
   - [ ] Navigate đến TranscriptScreen → UI sync đúng (đang playing)
   - [ ] Có thể pause/resume từ UI
   - [ ] Position updates đúng

2. **Start playback → Pause → Kill app → Restart app**
   - [ ] Service vẫn chạy (playback paused)
   - [ ] Navigate đến TranscriptScreen → UI sync đúng (đang paused)
   - [ ] Có thể resume từ UI

---

## 🎯 Edge Cases

### Recording
1. **Start recording → Navigate away → Start recording mới (không stop cái cũ)**
   - [ ] Recording cũ stop tự động
   - [ ] Recording mới start
   - [ ] UI sync đúng

2. **Start recording → Pause → Navigate away → Resume từ notification**
   - [ ] Recording resume
   - [ ] Navigate đến RecordScreen → UI sync đúng (recording)

3. **Start recording → Ẩn app → Tap notification → Pause → Tap notification lại**
   - [ ] Navigate đến RecordScreen
   - [ ] UI sync đúng (paused)

### Playback
1. **Start playback ở LibraryScreen → Navigate to TranscriptScreen (recording khác) → Play**
   - [ ] Playback cũ stop
   - [ ] Playback mới start
   - [ ] UI sync đúng ở cả 2 màn hình

2. **Start playback → Ẩn app → Tap notification → Pause → Tap notification lại**
   - [ ] Navigate đến TranscriptScreen
   - [ ] UI sync đúng (paused)

3. **Start playback → Seek → Ẩn app → Tap notification**
   - [ ] Navigate đến TranscriptScreen
   - [ ] UI sync đúng (position đúng sau khi seek)

4. **Start playback → Loop on → Ẩn app → Tap notification**
   - [ ] Navigate đến TranscriptScreen
   - [ ] UI sync đúng (looping on)
   - [ ] Playback loop đúng

---

## 🔍 Notification UI Tests

### Recording Notification
1. **Notification hiển thị đúng:**
   - [ ] Title: "Recording..."
   - [ ] Duration: Timer chạy đúng
   - [ ] Icon: Mic icon
   - [ ] Actions: Pause/Resume, Stop
   - [ ] Không có sound/vibration spam

2. **Notification update:**
   - [ ] Duration update mỗi giây
   - [ ] Icon đổi khi pause/resume
   - [ ] Không có flickering

### Playback Notification
1. **Notification hiển thị đúng:**
   - [ ] Title: Recording title
   - [ ] Position/Duration: Đúng format
   - [ ] Icon: Play/Pause icon
   - [ ] Actions: Pause/Resume, Stop
   - [ ] Progress bar: Update đúng

2. **Notification update:**
   - [ ] Position update mỗi giây
   - [ ] Icon đổi khi pause/resume
   - [ ] Progress bar update đúng
   - [ ] Không có flickering

---

## 🚨 Error Handling Tests

### Recording
1. **File không tồn tại:**
   - [ ] Error message hiển thị
   - [ ] Recording không start

2. **File không đọc được:**
   - [ ] Error message hiển thị
   - [ ] Recording không start

3. **File rỗng (0 bytes):**
   - [ ] Error message hiển thị
   - [ ] Recording không start

### Playback
1. **File không tồn tại:**
   - [ ] Error message hiển thị
   - [ ] Playback không start

2. **File không đọc được:**
   - [ ] Error message hiển thị
   - [ ] Playback không start

3. **File rỗng (0 bytes):**
   - [ ] Error message hiển thị
   - [ ] Playback không start

---

## 📱 Lock Screen Controls Tests

1. **Start playback → Lock screen**
   - [ ] Lock screen controls hiển thị
   - [ ] Có thể pause/resume từ lock screen
   - [ ] Có thể seek từ lock screen (nếu supported)

2. **Start playback → Pause → Lock screen**
   - [ ] Lock screen controls hiển thị (paused state)
   - [ ] Có thể resume từ lock screen

3. **Start playback → Lock screen → Tap play/pause**
   - [ ] Playback pause/resume đúng
   - [ ] Notification update đúng
   - [ ] Unlock → UI sync đúng

---

## 🎯 Priority Test Order

### High Priority (Test ngay)
1. ✅ Notification navigation (recording & playback)
2. ✅ Cross-screen state sync
3. ✅ Process death recovery

### Medium Priority
4. Edge cases
5. Error handling
6. Lock screen controls

### Low Priority
7. Notification UI details
8. Performance tests

---

## 📝 Test Notes

- Test trên thiết bị thật (không phải emulator)
- Test với nhiều recording khác nhau
- Test với recording mới thu âm và recording đã transcript
- Test với recording ngắn (< 10s) và dài (> 5 phút)
- Test với app trong background lâu (> 30 phút)
- Test với nhiều lần navigate qua lại giữa các màn hình

