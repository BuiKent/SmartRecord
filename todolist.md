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

