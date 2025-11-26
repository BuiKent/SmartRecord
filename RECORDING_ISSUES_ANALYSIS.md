# Phân tích lỗi ghi âm và so sánh với logtest.md

## 🔴 VẤN ĐỀ PHÁT HIỆN

### 1. Vấn đề với `getAmplitude()` - Waveform không hoạt động đúng

**Vị trí**: `AudioRecorderImpl.kt:154-166`

**Vấn đề**:
- `maxAmplitude` trả về giá trị MAXIMUM kể từ lần gọi trước, sau đó reset về 0
- Điều này có thể khiến waveform không hiển thị liên tục
- Nếu MediaRecorder null hoặc chưa recording, có thể throw exception (mặc dù đã có try-catch)

**Ảnh hưởng**: Waveform visualization có thể không hoạt động hoặc không mượt

### 2. THIẾU: Chức năng "Discard" recording

**Yêu cầu từ logtest.md:25**: "Huỷ ghi (discard) vs Lưu (save)"

**Hiện tại**: 
- Chỉ có nút "Stop" → tự động lưu
- Không có cách để hủy recording đang ghi mà không lưu file

**Cần bổ sung**:
- Nút "Discard" khi đang ghi
- Dialog xác nhận trước khi hủy
- Xóa file tạm và recording trong database

### 3. File naming không theo yêu cầu

**Yêu cầu từ logtest.md:35**: Tên auto: "Ghi âm 2025-11-26 21:03"

**Hiện tại**: 
- File: `recording_001.3gp`, `recording_002.3gp`...
- Title: "Recording MMM dd, yyyy" (sau khi stop)

**Cần**: Format theo yêu cầu "Ghi âm YYYY-MM-DD HH:mm"

### 4. Error handling có thể cải thiện

**Vấn đề**:
- Error messages có thể không đủ chi tiết cho user
- MediaRecorder exceptions có thể không được log đầy đủ

### 5. MediaRecorder configuration

**Hiện tại**: 
- Format: THREE_GPP
- Encoder: AMR_NB
- Sample Rate: 16000

**Có thể cải thiện**:
- AMR_NB là format cũ, chất lượng thấp
- Nên dùng AAC với MP4 hoặc M4A cho chất lượng tốt hơn

---

## 📋 SO SÁNH VỚI LOGTEST.MD (Lines 4-9)

### ✅ ĐÃ CÓ

1. **Ghi nhanh từ Home** ✅
   - Có nút Record ngay trên màn hình RecordScreen
   - Permission handling đã có

2. **Hiển thị thời gian đã ghi** ✅
   - Timer hiển thị duration trong RecordScreen

3. **Tạm dừng / tiếp tục** ✅
   - Pause/Resume đã implement

4. **Đánh dấu mốc (marker)** ✅
   - Bookmarks đã có, có thể thêm khi đang ghi

5. **Auto-save tạm** ✅
   - AutoSaveManager đã có, lưu metadata mỗi 30 giây

6. **Foreground Service** ✅
   - RecordingForegroundService đã implement

7. **Waveform visualization** ⚠️
   - Có component WaveformVisualizer nhưng có thể có vấn đề với amplitude

### ❌ THIẾU / CHƯA ĐÚNG

1. **Discard recording** ❌
   - Không có nút hủy, chỉ có Stop → Save

2. **File naming** ❌
   - Format hiện tại không đúng yêu cầu

3. **Recovery flow UI** ⚠️
   - Auto-save có nhưng chưa có UI để recover khi app crash/mở lại

4. **Tags / Folders** ❌
   - Database có tag system nhưng chưa có UI để gắn tag/folder khi lưu

5. **Xử lý cuộc gọi tới** ⚠️
   - Chưa detect và handle khi có cuộc gọi đến

6. **Warning khi ghi quá dài** ❌
   - Không có warning/giới hạn thời gian

---

## 🔧 KẾ HOẠCH SỬA LỖI VÀ BỔ SUNG

### Phase 1: Sửa lỗi critical

1. **Sửa getAmplitude()**
   - Cải thiện error handling
   - Đảm bảo không throw exception khi MediaRecorder null
   - Có thể cần dùng AudioRecord thay vì MediaRecorder cho real-time amplitude

2. **Cải thiện error messages**
   - Thêm error messages chi tiết hơn
   - Log đầy đủ MediaRecorder exceptions

3. **Kiểm tra MediaRecorder configuration**
   - Test xem có lỗi gì khi start recording không
   - Kiểm tra permission handling

### Phase 2: Bổ sung tính năng thiếu

1. **Thêm Discard recording**
   - Nút Discard trong RecordScreen (khi đang ghi)
   - Dialog xác nhận
   - UseCase để discard (stop + delete file + remove from DB)

2. **Cải thiện file naming**
   - Format: "Ghi âm YYYY-MM-DD HH:mm"
   - Apply khi start recording

3. **Recovery flow UI**
   - Detect .tmp files khi app mở
   - Hiển thị dialog recover
   - Cho phép user quyết định giữ hay xóa

4. **Tags/Folders khi lưu**
   - Dialog để chọn tag/folder khi Stop
   - Hoặc cho phép edit sau

### Phase 3: Cải thiện chất lượng

1. **MediaRecorder format**
   - Chuyển sang AAC + M4A/MP4
   - Giữ backward compatibility

2. **Xử lý cuộc gọi**
   - Detect incoming call
   - Auto-pause + save

3. **Warning thời gian dài**
   - Hiển thị warning sau 1 giờ ghi liên tục

---

## 🐛 CÁC LỖI CỤ THỂ CẦN SỬA

### Lỗi 1: getAmplitude() có thể gây crash

**File**: `AudioRecorderImpl.kt:154-166`

**Vấn đề**:
```kotlin
override fun getAmplitude(): Int {
    return try {
        val amplitude = mediaRecorder?.maxAmplitude ?: 0
        // ...
    } catch (e: Exception) {
        // ...
    }
}
```

**Giải pháp**: 
- Check null và isRecording state trước khi gọi
- Thêm synchronized để thread-safe
- Có thể return 0 nếu không recording

### Lỗi 2: Không có cách discard recording

**File**: `RecordViewModel.kt`, `RecordScreen.kt`

**Thiếu**: 
- Method `onDiscardClick()` trong ViewModel
- Nút Discard trong UI
- UseCase để discard

### Lỗi 3: File naming format

**File**: `StartRecordingUseCase.kt:22`

**Hiện tại**:
```kotlin
val outputFile = File(outputDir, "recording_%03d.3gp".format(nextFileNumber))
```

**Cần**: Format có ngày giờ theo yêu cầu

---

## 📊 PRIORITY

1. **CRITICAL**: Sửa getAmplitude() - có thể gây crash
2. **HIGH**: Thêm Discard recording - user yêu cầu
3. **HIGH**: Sửa file naming - không đúng format
4. **MEDIUM**: Recovery flow UI
5. **MEDIUM**: Tags/Folders khi lưu
6. **LOW**: MediaRecorder format upgrade
7. **LOW**: Handle cuộc gọi
8. **LOW**: Warning thời gian dài

