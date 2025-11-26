Nếu gọi là **app ghi âm – note – transcript “chuẩn, ngon”**, thì về cơ bản nó phải làm được 4 chuyện lớn:

> **1. Ghi nhanh – không mất file – không crash**
> **2. Nghe lại và tìm lại cực dễ**
> **3. Gắn note / context rõ ràng (meeting nào, môn nào, bài nào…)**
> **4. Transcript đủ tốt để đọc như note, có thể chỉnh sửa**

Mình chia nhỏ theo **UI – chức năng – logic – luồng** cho bạn dễ map vào app hiện tại nhé.

---

## 1. Yêu cầu về chức năng (feature)

### 1.1. Ghi âm (Record)

* Bấm 1 cái là ghi được ngay (từ Home).
* Hiển thị:

  * Thời gian đã ghi
  * Waveform / level bar để user thấy app đang sống
* Các hành động cơ bản:

  * Tạm dừng / tiếp tục
  * Đánh dấu mốc (marker) trong lúc ghi: ví dụ “Câu 1”, “Part B”, “Note quan trọng”
  * Huỷ ghi (discard) vs Lưu (save)
* Xử lý gián đoạn:

  * Có **auto-save** tạm để app crash / hết pin vẫn không mất file
  * Xử lý cuộc gọi tới / chuyển app nền → vẫn ghi được / resume hợp lý

### 1.2. Lưu & quản lý file ghi âm

* Mỗi recording có:

  * Tên (auto: *“Ghi âm 2025-11-26 21:03”*, cho phép đổi tên)
  * Thời lượng, ngày giờ
  * Tag / thư mục / “context” (VD: *“Thi tiếng Anh – Đề số 5”*, *“Cuộc họp dự án”*)
  * Trạng thái transcript: *Chưa transcript / Đang xử lý / Đã xong*
* Có **thùng rác (Trash)**:

  * Xóa nhầm có thể khôi phục
* Hỗ trợ:

  * Favorite / Pin (ghi âm quan trọng)
  * Merge / Split recordings (nâng cao, optional)

### 1.3. Transcript

* Với mỗi recording:

  * Có nút **“Transcript”** ngay trên chi tiết ghi âm.
  * Transcript hiển thị dạng **text chia đoạn**, sync với time:

    * Tap vào câu → nhảy tới thời điểm đó
    * Kéo progress → highlight đoạn text tương ứng
* Cho phép:

  * Sửa text (edit transcript)
  * Copy / share text
  * Xuất file (TXT / DOCX / PDF) – optional
* Nếu dùng STT offline:

  * Có trạng thái xử lý + loader
  * Có nút “Transcript lại” khi user đổi language / model

### 1.4. Note & Highlight

* Trong transcript:

  * User bôi đậm đoạn quan trọng
  * Thêm **inline note** (comment bên cạnh 1 câu / đoạn)
* Trong chế độ playback:

  * Có thể thêm **marker** + note text: “Đoạn này thầy giải thích rất hay”, “Câu dễ sai”

### 1.5. Tìm kiếm & tổ chức

* Tìm theo:

  * Tên file
  * Nội dung transcript (full-text search)
  * Tag / thư mục
* Bộ lọc:

  * Theo ngày (hôm nay, tuần này, tháng này…)
  * Theo độ dài (ngắn / dài)
  * Theo context (Exam, Meeting, Class, Personal…)
* Thư mục / Collection:

  * Ví dụ: “Môn Thanh Nhạc”, “Thi Toeic”, “Họp Team”

### 1.6. Chia sẻ & backup (tuỳ triết lý offline của bạn)

* Share:

  * Chia sẻ file audio
  * Chia sẻ transcript (text / PDF)
* Backup (offline-first nhưng vẫn nên nghĩ):

  * Tùy chọn export toàn bộ data (audio + JSON transcript) để nén thành 1 file gửi lên Drive / PC

---

## 2. Yêu cầu UI (cảm giác “ngon” của người dùng)

### 2.1. Màn hình chính (Home / Library)

* Danh sách ghi âm:

  * Tên, thời lượng, ngày
  * Badges: *Transcript ✓*, *Has notes*, *Pinned*
* Action nổi bật:

  * **Floating Record Button** hoặc bottom center button: luôn thấy nút ghi
* Quick filters:

  * Chip: *Tất cả / Thi / Học / Họp / Quan trọng*
  * Search bar phía trên

### 2.2. Màn hình ghi âm (Record Screen)

* Layout tối giản:

  * Timer to, chính giữa
  * Waveform hoặc level bar
  * Nút:

    * Record/Pause (nút lớn, dễ bấm)
    * Marker (nút nhỏ hơn)
    * Save / Discard rõ ràng (khi dừng)
* UX:

  * Khi user bấm Back → cảnh báo nếu chưa lưu
  * Transition mượt sang màn hình chi tiết sau khi lưu

### 2.3. Màn hình chi tiết ghi âm (Recording Detail)

* Header:

  * Tên ghi âm (có thể sửa)
  * Menu: xóa, share, move to folder, pin
* Tabs hoặc sections:

  * **Tab 1 – Playback**: Player + markers timeline
  * **Tab 2 – Transcript**: text + highlight + notes
* Ở tab Transcript:

  * Mỗi đoạn text có time nhỏ (e.g. [01:23])
  * Tap vào text → play từ đoạn đó
  * Long-press → “Thêm note / Highlight / Copy”

### 2.4. Micro-interactions (cảm giác pro)

* Hiệu ứng nhỏ:

  * Waveform chuyển động theo audio
  * Haptic nhẹ khi bấm nút ghi, marker
* State rõ ràng:

  * Đang ghi / Đang tạm dừng / Đang transcript / Lỗi
* Empty states:

  * Khi chưa có ghi âm: hướng dẫn ngắn + nút “Bắt đầu ghi”

---

## 3. Yêu cầu về logic & kiến trúc

### 3.1. Data model (gợi ý)

* `Recording`:

  * `id`
  * `title`
  * `filePath`
  * `duration`
  * `createdAt`
  * `tags` / `folderId`
  * `isPinned`
  * `transcriptStatus` (NONE, PENDING, DONE, ERROR)
* `TranscriptSegment`:

  * `id`
  * `recordingId`
  * `startTimeMs`
  * `endTimeMs`
  * `text`
* `Note`:

  * `id`
  * `recordingId`
  * `segmentId?`
  * `timeMs?`
  * `content`
* `Marker`:

  * `id`
  * `recordingId`
  * `timeMs`
  * `label`

### 3.2. Logic “chuẩn ngon” cần có

* **Không bao giờ mất dữ liệu**:

  * Auto-save khi:

    * Start ghi → tạo record tạm
    * Mỗi X giây → flush metadata
  * Nếu app crash → lần sau mở lại thấy “Ghi âm chưa hoàn tất, bạn muốn lưu không?”
* **Xử lý permission**:

  * Micro + Storage:

    * Flow xin quyền rõ ràng, có giải thích
* **Queue transcript**:

  * Ghi xong nhiều file → transcript lần lượt (có hàng đợi)
  * Trạng thái từng file rõ ràng

### 3.3. Flow logic với các trạng thái đặc biệt

* Khi user đang transcript mà tắt app:

  * Resume job khi mở lại
* Khi transcript fail:

  * Hiển thị lỗi + nút thử lại
* Khi ghi âm quá dài:

  * Nên có warning / gợi ý chia nhỏ

---

## 4. Luồng người dùng chính (User Flows)

### Flow 1: Mở app → Ghi nhanh (Quick Capture)

1. Mở app → Home (danh sách ghi âm)
2. Bấm nút Record ở giữa/bottom
3. Đang ghi:

   * Có waveform + timer
   * Có thể bấm marker tại các đoạn quan trọng
4. Bấm Stop → màn hình “Lưu ghi âm”:

   * Tên auto-suggest
   * Chọn Folder / Tag (optional)
5. Bấm Lưu → trả về màn chi tiết hoặc Library:

   * Nếu transcript auto → hiển thị “Đang tạo transcript…”

### Flow 2: Ghi âm để học / thi → Gắn transcript → Note

1. Chọn folder “Thi Toeic”
2. Bấm Record
3. Ghi đề thi / thầy chữa bài
4. Lưu → auto transcript
5. Mở chi tiết → Tab Transcript:

   * Bôi đậm đoạn “Đáp án chính thức”
   * Thêm note: “Câu 3 dễ nhầm, để ý từ vựng này”
6. Lần sau ôn thi:

   * Search “Câu 3 dễ nhầm” → nhảy tới đoạn đó

### Flow 3: Tìm lại một đoạn nói cụ thể

1. Mở app → Search “vàng 9999”
2. Kết quả:

   * 3 ghi âm có text chứa “vàng 9999”
3. Chọn ghi âm → tab Transcript
4. Tap vào đoạn text → play audio tại đúng chỗ đó

---

## 5. Non-functional (nhưng rất quan trọng)

* **Độ ổn định**:

  * Không crash khi ghi lâu
  * Xử lý low storage (hết dung lượng) → báo trước
* **Hiệu năng**:

  * List nhiều ghi âm vẫn mượt
  * Transcript chạy nền không làm lag UI
* **Privacy**:


---

## 1. Xung đột với app khác

### 1.1. Tranh chấp **microphone**

* Nguyên tắc: **1 thời điểm chỉ 1 app được dùng mic** (hoặc OS cho nhưng kết quả thường tệ).
* App ghi âm “chuẩn” phải:

  * Không cho phép **2 recorder chạy song song** trong chính app.
  * Nhận biết khi **app khác chiếm mic** (cuộc gọi, app ghi âm khác, app họp online…):

    * Dừng ghi tạm thời (pause)
    * Hoặc stop + auto-save đoạn đã ghi lại

**Về UX:**

* Khi mất mic → show message kiểu:

  > “Micro vừa bị ứng dụng khác chiếm. Đã lưu đoạn ghi âm trước đó để tránh mất dữ liệu.”

### 1.2. Tranh chấp **audio output**

* Khi app đang **playback** recording mà user bật:

  * Spotify / YouTube / Zalo voice message…
* App nên:

  * **Tự dừng playback** khi mất audio focus (đừng cố audio chồng audio).
  * Sau khi focus quay lại (app khác stop) → có thể:

    * Không auto play lại (tránh bất ngờ)
    * Hoặc hỏi/cho user tự bấm Play.

---

## 2. Bị dừng đột ngột (app bị kill, swipe, crash)

Ở góc nhìn user:

> “Tôi đang ghi 1 buổi cực quan trọng → app tự thoát → MẤT HẾT = app vứt đi.”

Muốn **“chuẩn, ngon”** thì phải thiết kế sao cho *cùng lắm mất vài giây cuối*, không bao giờ mất trắng.

### 2.1. Khi app bị user **vuốt khỏi đa nhiệm (force close)**

* Về logic:

  * Ghi âm nên chạy trong **Foreground Service** (trên Android):

    * Khi user thật sự *swipe kill*, OS sẽ hủy service → bạn có `onDestroy`/`onTaskRemoved` để:

      * Đóng stream audio
      * Flush và **commit file** (temp -> final)
      * Cập nhật trạng thái record thành “Đã lưu (bị dừng đột ngột)”

* UX vòng sau:

  * Khi mở lại app:

    * Nếu phát hiện file tạm → hiện banner:

      > “Phát hiện 1 ghi âm chưa hoàn tất lần trước. Đã khôi phục thành ‘Ghi âm hồi phục – 26/11, 21:10’.”

### 2.2. Khi app **crash / ANR**

* Crash = giống như rút điện → bạn coi như không có callback nào.

* Cách “chống mất dữ liệu”:

  * **Ghi trực tiếp ra file** trên ổ (streaming) chứ không buffer hết trong RAM.
  * Dùng file **.tmp**:

    * Đang ghi → `session_123.tmp`
    * Khi user bấm Stop / hoặc lúc recovery → rename thành `session_123.m4a`
  * Metadata (title, tags, v.v.) có thể chưa kịp lưu → nhưng **file audio vẫn còn**.

* Khi app mở lại:

  * Scan thư mục recordings:

    * Nếu có `.tmp` hoặc file meta thiếu → tạo một `Recording` “Recovered …” và cho user quyết định xóa hay giữ.

---

## 3. Sập nguồn / hết pin / reboot

Về bản chất, điều này = **crash toàn hệ thống**, nên xử lý y như trên:

### 3.1. Thiết kế để **power loss-safe**

* Quy tắc vàng:

  * **Không đợi đến cuối mới lưu tất cả.**
  * Audio data phải được stream xuống file **liên tục**.
  * Tối thiểu: mỗi vài trăm ms – vài giây là flush một chunk.

* Khi máy khởi động lại, user mở app:

  * App nhìn thấy file audio chưa hoàn chỉnh → coi như “Recovered recording”.
  * Coi như 1 recording bình thường, chỉ có thể nó bị “c cụt” ở cuối → chấp nhận được.

---

## 4. Một số edge case quan trọng khác

### 4.1. Cuộc gọi đến / app họp online

Kịch bản:

* Bạn đang ghi buổi giảng → có cuộc gọi đến.

Nên làm gì?

* Khi detect incoming call chiếm audio:

  * **Tự pause recording + auto-save** phần đã có.
* Sau cuộc gọi:

  * App có thể:

    * Không tự ghi lại (an toàn hơn)
    * Hiện thông báo:

      > “Đoạn ghi âm đã được lưu lại trước khi có cuộc gọi. Bấm Ghi để tiếp tục ghi một file mới.”

**Nếu muốn xịn hơn:**

* Cho phép **merge** 2 recording liên tiếp:

  * `Buổi giảng A (phần 1)` + `Buổi giảng A (phần 2)` → Merge.

### 4.2. Bluetooth / tai nghe bị rớt

* Đối với **playback**:

  * Nếu đang nghe bằng Bluetooth mà tai nghe tắt:

    * Dừng playback là hợp lý (tránh phát to ra loa giữa lớp/họp).
* Đối với **record** (mic Bluetooth):

  * Nếu nguồn mic thay đổi:

    * Nên cảnh báo:

      > “Nguồn ghi âm vừa chuyển từ Bluetooth sang micro của máy.”

### 4.3. Hết dung lượng lưu trữ

* Đây là case rất hay bị bỏ qua nhưng cực đau:

  * Đang ghi 60 phút → đến phút 58 hết dung lượng → app crash/stop → file hỏng.

**Cách làm chuẩn:**

* Trước khi bắt đầu ghi:

  * Check free space > ngưỡng tối thiểu (VD: 100MB).
* Khi đang ghi:

  * Bắt exception “write failed”:

    * **Stop ngay ghi âm**
    * Đóng file
    * Thông báo:

      > “Bộ nhớ máy đã đầy. Đã lưu lại phần ghi âm đến phút 57:32. Vui lòng giải phóng bộ nhớ để tiếp tục.”

### 4.4. Ghi âm + STT cùng lúc / nhiều engine dùng mic

Với app của bạn (ghi âm + transcript, có thể còn STT realtime):

* Không nên:

  * Vừa ghi bằng `MediaRecorder` (hoặc tương đương)
  * Vừa cho Vosk/Whisper hút trực tiếp từ mic cùng lúc

* Thiết kế an toàn:

  * **Chỉ 1 nguồn đọc mic**:

    * Hoặc record thô (PCM/WAV) → xong rồi feed file vào STT
    * Hoặc STT realtime → trích xuất audio từ đó (nâng cao)

* Nếu bạn có nhiều chế độ (Record only / Record + STT):

  * Hệ thống phải có **global RecordingState**:

    * `Idle / Recording / STTListening / Both-but-via-same-engine`
  * Khi một engine muốn dùng mic phải hỏi state:

    * Nếu đang bận → từ chối hoặc stop engine kia trước.

---

## 5. Tóm tắt lại cho bạn dễ checklist

Để app **ghi âm – note – transcript chuẩn, ngon, đáng tin**, về vụ **xung đột – dừng đột ngột – sập nguồn** nên đảm bảo:

1. **Không mất dữ liệu khi:**

   * App bị kill / crash / vuốt khỏi đa nhiệm
   * Sập nguồn / hết pin
   * Cuộc gọi đến, app khác chiếm mic
     👉 Giải pháp: stream audio trực tiếp vào file `.tmp`, auto-save, recovery flow.

2. **Xử lý audio focus và mic:**

   * Playback: tôn trọng app khác, dừng khi mất focus.
   * Recording: phát hiện khi bị app khác chiếm mic, auto-stop/pause + lưu.

3. **Recovery flow rõ ràng:**

   * Khi mở lại app → auto detect file tạm → chuyển thành recording “hồi phục” + giải thích cho user.

4. **Xử lý storage & lỗi I/O:**

   * Check free space trước khi ghi
   * Bắt lỗi ghi file → stop an toàn và thông báo “đã lưu tới thời điểm X”.

5. **Không cho hai chức năng dùng mic conflict:**

   * Ghi âm, STT, voice call, voice chat trong chính app cần có global state, không để 2 thứ hút mic riêng rẽ.

---