# Kế hoạch sửa lỗi và bổ sung tính năng ghi âm

## ✅ ĐÃ SỬA

### 1. Sửa lỗi getAmplitude()
- ✅ Thêm thread-safe với synchronized
- ✅ Check isRecording state trước khi gọi maxAmplitude
- ✅ Cải thiện error handling với IllegalStateException
- ✅ Giảm log spam (chỉ log mỗi 5000 thay vì 1000)

**File**: `AudioRecorderImpl.kt`

---

## 🔧 CẦN SỬA TIẾP

### 1. Thêm chức năng Discard Recording (CRITICAL - theo yêu cầu)

**Vấn đề**: Không có cách để hủy recording đang ghi mà không lưu.

**Cần làm**:
1. Tạo `DiscardRecordingUseCase`:
   - Stop recording
   - Xóa file audio tạm
   - Xóa recording khỏi database (nếu đã lưu tạm)
   - Cleanup resources

2. Thêm `onDiscardClick()` trong `RecordViewModel`:
   - Gọi use case
   - Stop timer
   - Stop foreground service
   - Stop auto-save
   - Reset UI state

3. Thêm nút Discard trong `RecordScreen`:
   - Hiển thị khi đang recording
   - Dialog xác nhận trước khi hủy

**Files cần tạo/sửa**:
- `domain/usecase/DiscardRecordingUseCase.kt` (mới)
- `ui/record/RecordViewModel.kt` (thêm method)
- `ui/screens/RecordScreen.kt` (thêm nút + dialog)

---

### 2. Sửa file naming format (HIGH)

**Yêu cầu từ logtest.md:35**: "Ghi âm 2025-11-26 21:03"

**Hiện tại**: 
- File: `recording_001.3gp`
- Title sau khi stop: "Recording MMM dd, yyyy"

**Cần**: 
- File: Vẫn dùng số thứ tự để tránh conflict: `recording_001.3gp`
- Title: Format "Ghi âm YYYY-MM-DD HH:mm" ngay khi start

**Files cần sửa**:
- `domain/usecase/StartRecordingUseCase.kt` - Set title ngay khi start
- Format: `SimpleDateFormat("Ghi âm yyyy-MM-dd HH:mm", Locale.getDefault())`

---

### 3. Recovery flow UI (MEDIUM)

**Vấn đề**: Auto-save đã có nhưng chưa có UI để recover khi app mở lại sau crash.

**Cần làm**:
1. Detect .tmp files khi app khởi động:
   - Scan recordings directory
   - Tìm files .tmp hoặc recordings chưa complete

2. Hiển thị Recovery Dialog:
   - List các recordings chưa hoàn tất
   - Cho phép user chọn: Recover, Delete, Ignore

3. Recovery logic:
   - Rename .tmp → .3gp
   - Update database nếu cần
   - Set title "Ghi âm hồi phục - YYYY-MM-DD HH:mm"

**Files cần tạo/sửa**:
- `domain/usecase/DetectRecoveryRecordingsUseCase.kt` (mới)
- `domain/usecase/RecoverRecordingUseCase.kt` (mới)
- `ui/components/RecoveryDialog.kt` (mới)
- `SmartRecorderApp.kt` hoặc `MainActivity.kt` - Check khi app start

---

### 4. Tags/Folders khi lưu (MEDIUM)

**Yêu cầu từ logtest.md:37**: "Tag / thư mục / context"

**Hiện tại**: Database có tag system nhưng chưa có UI để gắn khi lưu.

**Cần làm**:
1. Dialog chọn tags/folders khi Stop:
   - Hiển thị sau khi stop recording
   - Cho phép chọn tags từ danh sách
   - Cho phép chọn folder (nếu có)
   - Có thể bỏ qua

2. Hoặc cho phép edit sau:
   - Nút Edit tags trong Recording detail
   - Hiện tại chưa có UI này

**Files cần tạo/sửa**:
- `ui/components/SaveRecordingDialog.kt` (mới) - Dialog chọn tags/folders
- `ui/screens/RecordScreen.kt` - Show dialog sau khi stop
- `RecordViewModel.kt` - Handle save với tags

**Note**: Có thể làm sau, không critical.

---

### 5. Cải thiện error messages (LOW)

**Cần làm**:
- Thêm error messages chi tiết hơn cho user
- Phân biệt các loại lỗi: Permission, Storage, MediaRecorder, etc.

**Files cần sửa**:
- `RecordViewModel.kt` - Error messages
- `AudioRecorderImpl.kt` - Throw exceptions với messages rõ ràng

---

## 📋 IMPLEMENTATION ORDER

### Phase 1: Critical fixes (Ưu tiên cao nhất)
1. ✅ **DONE**: Sửa getAmplitude()
2. **NEXT**: Thêm Discard Recording
3. **NEXT**: Sửa file naming format

### Phase 2: Important features (Sau Phase 1)
4. Recovery flow UI
5. Tags/Folders khi lưu (optional, có thể làm sau)

### Phase 3: Nice to have (Sau Phase 2)
6. Cải thiện error messages
7. Handle cuộc gọi đến (khó, cần PhoneStateListener)
8. Warning thời gian ghi dài
9. Upgrade MediaRecorder format (AAC + M4A)

---

## 🧪 TESTING CHECKLIST

Sau khi sửa, cần test:

### Recording basics
- [ ] Start recording hoạt động
- [ ] Timer đếm đúng
- [ ] Waveform hiển thị (sau khi sửa getAmplitude)
- [ ] Pause/Resume hoạt động
- [ ] Stop và lưu thành công

### Discard (sau khi implement)
- [ ] Discard hủy và xóa file
- [ ] Dialog xác nhận hiển thị đúng
- [ ] UI reset về trạng thái ban đầu sau discard

### File naming (sau khi sửa)
- [ ] Title format đúng "Ghi âm YYYY-MM-DD HH:mm"
- [ ] Title được set ngay khi start recording

### Error handling
- [ ] Permission denied hiển thị error rõ ràng
- [ ] Storage full hiển thị error rõ ràng
- [ ] MediaRecorder errors được catch và hiển thị

---

## 📝 NOTES

- getAmplitude() đã được sửa để thread-safe và an toàn hơn
- Discard là tính năng quan trọng nhất cần làm tiếp theo
- File naming format cần sửa để đúng yêu cầu
- Recovery flow có thể làm sau nếu không urgent

