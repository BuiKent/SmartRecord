Ok, chốt lại: mình sẽ coi đây là app **ghi âm → transcript → note → học/ôn thi**, tất cả **offline, free, local-only**, dùng **Whisper on-device** + (nếu muốn) thêm 1 engine realtime nhưng vẫn ưu tiên offline.

Về mặt brand & nhìn trên icon / launcher

Tên package / appId thì cứ để dạng code:
com.yourname.smartrecorder là được.

Tên hiển thị ngoài launcher có thể rút gọn:

Trên icon chỉ hiện: SmartRecorder

Trong Play Store: Smart Recorder & Transcripts

Mình chia gợi ý thành các nhóm, để bạn dễ pick & làm roadmap.

---

## 1. Ghi âm & Transcript “xịn” hơn

### 1.1. Dual transcript: Live vs Final

(Phù hợp nếu bạn dùng 2 engine: 1 realtime, 1 Whisper refine)

* **Live Transcript (Realtime)**

  * Trong khi ghi âm: hiển thị chữ chạy realtime (“thô”).
  * Dùng:

    * Vosk / engine offline realtime **hoặc** Google ASR (nếu bạn cho phép user bật “Online mode”).
  * UI: 2 tab / 2 layer:

    * **Live**: chữ đổi liên tục, tốc độ cao.
    * **Final**: trống lúc đầu, chỉ hiện sau khi Whisper chạy xong.

* **Final Transcript (Whisper)**

  * Sau khi stop record:

    * Cắt audio thành chunks → feed Whisper → tạo transcript “chuẩn”.
    * Merge lại, căn theo thời gian.
  * UI:

    * Cho phép toggle “Hiển thị thay đổi so với Live” (highlight câu Whisper sửa khác với Live).

---

### 1.2. Marker & Bookmark trong lúc ghi

Tính năng nâng cấp UX ghi âm:

* Nút **“Đánh dấu” (Bookmark)** ngay trên waveform:

  * Nhấn 1 cái → tạo **mốc thời gian** (timestamp) + có thể nhập nhanh “Lưu ý: câu hỏi quan trọng”.
  * Sau khi transcript xong → tự gán marker này vào đoạn text tương ứng.
* Dùng cho:

  * Ghi bài giảng, meeting → mark đoạn “thầy nhấn mạnh”, “câu hỏi đề thi”, “task quan trọng”.

---

### 1.3. Multi-mode ghi âm

Cho user chọn “mục đích” trước khi ghi:

* **Mode: Cuộc họp / Meeting**

  * Auto đề xuất template export: Minutes (biên bản họp).
  * Gợi ý section: “Participants”, “Decisions”, “Action items”.
* **Mode: Bài giảng / Lecture**

  * Ưu tiên marker “Ví dụ”, “Định nghĩa”.
* **Mode: Phỏng vấn / Interview**

  * Cho phép user đánh dấu “Q” / “A” khi nói.
* **Mode: Ôn thi / Study**

  * Tập trung vào việc trích xuất câu hỏi, keyword, flashcard.

Tất cả logic này có thể **rule-based**, không cần AI online.

---
Concept UI & Flow
1.1. Flow tổng

Mở app ⇒ vào luôn màn Record.

Dưới đáy là bottom bar:

Trái: Library – danh sách bản ghi + transcript.

Giữa: Nút tròn Record – bấm cái là quay lại Record Screen.

Phải: Study – flashcards, ôn tập.

Trong Record screen:

Nút mic to ở giữa → Ghi âm ngay.

Hai nút phụ:

Import file MP3/WAV

Realtime transcript (sau dùng engine realtime thì gắn vào).

1.2. Màu sắc & style

Bạn có thể dùng Material3 mặc định hoặc chỉnh:

Primary: #6750A4 (chuẩn Material3)

Nền: colorScheme.background (trắng ngà / xám rất nhẹ)

Bottom bar: colorScheme.surface

Record FAB:

Kích thước: 56–64dp (mình chọn 64dp cho “xịn”)

Shape: CircleShape

Màu nền: colorScheme.primary

Icon mic màu: Color.White

Bottom bar:

Chiều cao: NavigationBar mặc định ~ 80dp.

Icon Library / Study:

Size: 24dp

Label: BodySmall

2. Code: cấu trúc & các file

Giả sử project đã tạo từ template Empty Activity + Compose + Material3.

2.1. build.gradle (Module: app) – thêm navigation-compose

Trong dependencies:

implementation(platform("androidx.compose:compose-bom:2024.04.01"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.navigation:navigation-compose:2.8.0")
implementation("androidx.activity:activity-compose:1.9.0")
implementation("androidx.compose.material:material-icons-extended")

3. MainActivity + SmartRecorderApp
MainActivity.kt
package com.example.smartrecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smartrecorder.ui.SmartRecorderApp
import com.example.smartrecorder.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                SmartRecorderApp()
            }
        }
    }
}


AppTheme là theme Material3 (bạn có thể dùng cái mặc định Android Studio tạo hoặc file theme bạn đang dùng).

4. App shell + Navigation + Bottom bar
4.1. Định nghĩa route

Tạo file: ui/navigation/AppDestinations.kt

package com.example.smartrecorder.ui.navigation

object AppRoutes {
    const val RECORD = "record"
    const val LIBRARY = "library"
    const val STUDY = "study"
}

4.2. SmartRecorderApp + NavHost + BottomBar + FAB

File: ui/SmartRecorderApp.kt

package com.example.smartrecorder.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartrecorder.ui.navigation.AppRoutes
import com.example.smartrecorder.ui.screens.LibraryScreen
import com.example.smartrecorder.ui.screens.RecordScreen
import com.example.smartrecorder.ui.screens.StudyScreen
import com.example.smartrecorder.ui.widgets.AppBottomBar
import com.example.smartrecorder.ui.widgets.RecordFab

@Composable
fun SmartRecorderApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.RECORD

    Scaffold(
        floatingActionButton = {
            RecordFab(
                onClick = {
                    // đi về màn Record từ bất cứ đâu
                    navController.navigate(AppRoutes.RECORD) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = {
            AppBottomBar(
                currentRoute = currentRoute,
                onLibraryClick = {
                    navController.navigate(AppRoutes.LIBRARY) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onStudyClick = {
                    navController.navigate(AppRoutes.STUDY) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.RECORD,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppRoutes.RECORD) {
                RecordScreen(
                    // callback đơn giản, chưa xử lý logic audio
                    onStartRecordClick = { /* TODO: bắt đầu ghi âm */ },
                    onImportAudioClick = { /* TODO: mở file picker */ },
                    onRealtimeSttClick = { /* TODO: mở màn realtime STT */ }
                )
            }
            composable(AppRoutes.LIBRARY) {
                LibraryScreen(
                    onRecordingClick = { recordingId ->
                        // TODO: điều hướng sang TranscriptDetailScreen sau này
                    }
                )
            }
            composable(AppRoutes.STUDY) {
                StudyScreen(
                    onStartPracticeClick = {
                        // TODO: mở màn luyện flashcards
                    }
                )
            }
        }
    }
}

5. Bottom bar + Record FAB
5.1. AppBottomBar.kt

File: ui/widgets/AppBottomBar.kt

package com.example.smartrecorder.ui.widgets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.smartrecorder.ui.navigation.AppRoutes

@Composable
fun AppBottomBar(
    currentRoute: String,
    onLibraryClick: () -> Unit,
    onStudyClick: () -> Unit
) {
    NavigationBar {
        // LEFT: Library
        NavigationBarItem(
            selected = currentRoute == AppRoutes.LIBRARY,
            onClick = onLibraryClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.LibraryBooks,
                    contentDescription = "Library"
                )
            },
            label = { Text("Library") },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
            )
        )

        // MIDDLE: chừa chỗ cho FAB ở giữa
        NavigationBarItem(
            selected = false,
            onClick = { /* do nothing - center FAB handle */ },
            icon = { /* empty chỗ cho FAB */ },
            label = { Text("") },
            enabled = false
        )

        // RIGHT: Study
        NavigationBarItem(
            selected = currentRoute == AppRoutes.STUDY,
            onClick = onStudyClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "Study"
                )
            },
            label = { Text("Study") },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
            )
        )
    }
}


Cái item giữa để trống là “slot” cho FAB tròn ở giữa đè lên.

5.2. RecordFab.kt

File: ui/widgets/RecordFab.kt

package com.example.smartrecorder.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecordFab(
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(64.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Record"
        )
    }
}

6. RecordScreen – giao diện “vào cái là ghi được”

File: ui/screens/RecordScreen.kt

package com.example.smartrecorder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

data class RecordUiState(
    val isRecording: Boolean = false,
    val durationMs: Long = 0L
)

@Composable
fun RecordScreen(
    uiState: RecordUiState = RecordUiState(),
    onStartRecordClick: () -> Unit,
    onImportAudioClick: () -> Unit,
    onRealtimeSttClick: () -> Unit
) {
    val isRecording = uiState.isRecording

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Smart Recorder",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ghi âm, transcript & ghi chú thông minh",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Waveform placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRecording) "Đang ghi âm..." else "Sẵn sàng ghi âm",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Big mic button
        ElevatedButton(
            onClick = onStartRecordClick,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .size(96.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Start Record"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Chạm để bắt đầu ghi âm",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Import / Realtime buttons
        Text(
            text = "Hoặc chọn cách khác:",
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = onImportAudioClick
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import file audio")
            }

            OutlinedButton(
                onClick = onRealtimeSttClick
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Realtime transcript")
            }
        }
    }
}


Tạm thời onStartRecordClick / onImportAudioClick / onRealtimeSttClick để TODO trong SmartRecorderApp. Sau này bạn gắn logic audio & file picker.

7. LibraryScreen & StudyScreen (placeholder)

Để app build được, làm đơn giản.

ui/screens/LibraryScreen.kt
package com.example.smartrecorder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun LibraryScreen(
    onRecordingClick: (String) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Library – Danh sách bản ghi (TODO)")
    }
}

ui/screens/StudyScreen.kt
package com.example.smartrecorder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun StudyScreen(
    onStartPracticeClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Study – Flashcards & Ôn tập (TODO)")
    }
}

8. Lưu ý để team dễ làm

Đi từng bước:

B1: Thêm dependencies (navigation-compose, material icons).

B2: Tạo MainActivity, SmartRecorderApp như trên.

B3: Tạo các file AppBottomBar, RecordFab, RecordScreen, LibraryScreen, StudyScreen.

B4: Build thử → chạy. Lúc này UI & điều hướng đã OK, chưa cần audio.

Audio & import file để sau:

onStartRecordClick: sau này gọi ViewModel.startRecording().

onImportAudioClick: dùng rememberLauncherForActivityResult(OpenDocument) để chọn .mp3/.wav.

onRealtimeSttClick: mở một screen khác (route mới) nếu bạn làm realtime.

Theme & icon:

Nếu bạn muốn app tone tím – trắng kiểu Material3, sửa trong Color.kt & Theme.kt hoặc giữ nguyên template Material3 tạo sẵn.


## 2. Quản lý ghi chú & lịch sử ghi âm (local mạnh)

### 2.1. Cấu trúc dữ liệu “Recording → Transcript → Notes”

Đề xuất schema (Room):

* `RecordingEntity`

  * id, title, createdAt, duration, filePath, mode (meeting/lecture/study…)
* `TranscriptSegmentEntity`

  * recordingId, startTimeMs, endTimeMs, text
* `NoteEntity`

  * recordingId, segmentId?, content, type (summary, todo, question, bookmark…)
* `TagEntity`

  * tagName
* `RecordingTagCrossRef`

  * recordingId, tagId

---

### 2.2. Tính năng quản lý nâng cao

* **Auto-title**:

  * Dùng heuristics đơn giản:

    * Lấy 5–10 từ xuất hiện nhiều nhất (trừ stopwords) + ngày giờ.
    * Ví dụ: `“Lecture - Cardiology - 2025-11-25”`.

* **Tags / Folder**:

  * Tag manual (user add #tag).
  * Auto tag từ keywords top TF-IDF (local):

    * “tim mạch”, “marketing”, “pháp luật”, “TOEIC”…

* **Pin & Archive**:

  * Pin recordings quan trọng lên top.
  * Archive recordings cũ (ẩn khỏi list chính nhưng vẫn search được).

* **Full-text search (Offline)**:

  * Dùng SQLite FTS (FTS4/FTS5):

    * Search theo:

      * Tiêu đề
      * Nội dung transcript
      * Notes
  * Kết quả:

    * Highlight đoạn chứa keyword.
    * Click là nhảy ngay đến timestamp tương ứng trong audio.

---

## 3. Smart Notes & “Thông minh nhưng không cần AI online”

Toàn bộ phần này có thể làm bằng **rule-based + thống kê đơn giản**.

### 3.1. Keyword Extraction (Local-only)

* Với từng recording:

  * Build vector từ (term frequency).
  * Loại bỏ stopwords, số, từ quá ngắn.
  * Lấy top 5–15 từ/phrase → hiển thị thành:

    * **Tag cloud** hoặc list “Keywords”.
* Dùng cho:

  * Gợi ý tag.
  * Filter list recordings theo keyword.

---

### 3.2. Rule-based Summary (Pseudo-AI)

Không cần LLM, chỉ cần heuristic:

* Chia transcript thành đoạn (paragraph) theo khoảng 2–4 câu hoặc 15–30s.
* Tính “điểm quan trọng” cho mỗi câu:

  * Xuất hiện từ khóa “kết luận”, “tóm lại”, “quan trọng”, “vì vậy”…
  * Câu ở gần cuối đoạn.
  * Câu dài hơn ngưỡng tối thiểu.
* Chọn top N câu → ghép thành **“Auto summary (beta)”**.
* User vẫn có thể sửa lại thủ công → lưu thành `NoteEntity(type=SUMMARY)`.

---

### 3.3. Detect câu hỏi → List “Question bank”

Đặc biệt hữu ích cho **ôn thi**:

* Quét transcript:

  * Câu kết thúc bằng “?”, hoặc có từ “tại sao”, “như thế nào”, “what”, “why”, “how”…
* Gom thành list:

  * **Question List**: tất cả câu hỏi xuất hiện trong bài giảng/meeting.
* Dùng để:

  * Ôn thi (danh sách câu hỏi)
  * Chuẩn bị flashcards.

---

### 3.4. Flashcards (local)

* User chọn 1 đoạn transcript:

  * Mark “Đây là Q” (mặt trước)
  * Mark “Đây là A” (mặt sau) → tạo card.
* Quản lý deck theo:

  * Recording
  * Tag
* Chế độ luyện tập:

  * Leitner / Spaced repetition đơn giản (lưu lại `nextReviewDate` trong Room).

---

## 4. Export & Share “pro” hơn

### 4.1. Format xuất text

* **Plain text** (.txt)
* **Markdown** (.md)

  * Hỗ trợ:

    * `# Title`
    * `## Summary`
    * `- Bullet points`
    * `> Quotes`
* **SRT / VTT subtitle**:

  * Nếu transcript có timestamps → xuất phụ đề:

    * Dùng khi muốn xem lại video/bài giảng với subtitle.
* (Optional) **DOCX**:

  * Sử dụng lib local (không online), nếu bạn thấy cần.

---

### 4.2. Template export

Cho user chọn template trước khi export:

1. **Meeting Minutes**

   * Header:

     * Date, Time, Attendees
   * Sections:

     * Decisions
     * Action Items (checkbox)
2. **Lecture Notes**

   * Outline theo headings (tự generate từ keywords/time):

     * I. Topic 1
     * II. Topic 2
3. **Interview Transcript**

   * Format:

     * `Q:` & `A:` (dựa trên markers hoặc detect câu hỏi).

Tất cả template có thể là **engine Markdown + 1 lớp render**.

---

### 4.3. Copy modes

* **Copy full text**
* **Copy only summary**
* **Copy chỉ đoạn được chọn** (select text, context menu → Copy with timestamp)
* **Copy dạng bullet points**:

  * Mỗi đoạn = 1 bullet: `• [00:12] Nội dung...`

---

## 5. Tương tác “smart” với Transcript (không cần AI)

### 5.1. Playback thông minh

* Bấm vào 1 câu → audio seek đến timestamp + play.
* Chế độ **Loop đoạn**:

  * Chọn đoạn text → “Loop” → audio loop từ start–end:

    * Rất tốt cho người học ngoại ngữ.

---

### 5.2. Mini “Search assistant” local

Không cần LLM, chỉ keyword search + UI tốt:

* User gõ: “kết luận”, “part 2”, “homework” → app:

  * Tìm tất cả câu chứa từ đó.
  * Hiển thị list:

    * `[00:12] Về phần kết luận, chúng ta...`
    * `[05:31] Bài tập về nhà là...`
* Gọi tên feature kiểu:

  * “Tìm trong transcript” hoặc “Jump to topic”.

Cảm giác như chat với app, nhưng thực ra chỉ là search nâng cao.

---

## 6. Whisper & xử lý file âm thanh đơn giản

### 6.1. Import file audio ngoài

* User có thể:

  * Chọn file `.mp3`, `.wav`, `.m4a` từ storage.
* App:

  * Chuẩn hóa:

    * Convert sang 16kHz mono (ffmpeg/lib local).
  * Chạy Whisper → tạo recording mới kiểu “Imported”.

---

### 6.2. Pre-processing cơ bản (local)

Không cần fancy DSP, chỉ mấy bước:

* Normalize volume.
* Optional:

  * Noise gate đơn giản (ngưỡng RMS).
* Cắt đầu/cuối im lặng.
* Cho phép user:

  * Trim thủ công bằng UI timeline đơn giản.

---

## 7. Roadmap gợi ý (để bạn dễ ưu tiên)

### Phase 1 – V1.0 (Release được ngay)

* Record + waveform + Live transcript (nếu có engine realtime).
* Whisper final transcript.
* Lịch sử recordings (Room).
* Export `.txt`, copy text.
* Basic search theo title.

### Phase 2 – V1.1 (Smart Note / Study)

* Bookmarks & markers.
* Full-text search (FTS).
* Keyword extraction + auto tags.
* Question list (detect “?”).
* Flashcards cơ bản.

### Phase 3 – V2.0 (Power user)

* Rule-based summary.
* Template export (Meeting, Lecture, Interview).
* SRT export + jump-to-sentence + loop playback.
* Import audio file + basic pre-processing.

---

Nếu bạn muốn, ở bước tiếp theo mình có thể:

Dưới đây là **bản vẽ FLOW tổng thể dạng doodle/hand-drawn**, bao quát toàn bộ luồng App của bạn:
**App Start → Record → Transcript → Notes → Export → History**

Mình cố tình vẽ theo style “phác thảo bảng trắng”, dễ nhìn – dễ mở rộng.

---

# 🗺️ **FLOW TỔNG THỂ — Audio Recorder → Transcript → Notes → Export**

*(Hình minh hoạ phong cách doodle - bên dưới là flow vẽ chi tiết bằng ASCII để bạn copy vào tài liệu.)*

---

# 🎨 **ASCII DOODLE FLOW – FULL PIPELINE**

```
                    ┌────────────────────────┐
                    │        APP START       │
                    └────────────┬───────────┘
                                 │
                                 ▼
                     ┌────────────────────┐
                     │  HOME / DASHBOARD  │
                     │  - New Recording   │
                     │  - History         │
                     │  - Settings        │
                     └─────────┬──────────┘
                               │
                               ▼
       ┌──────────────────────────────────────────┐
       │            NEW RECORDING FLOW            │
       └──────────────────────────────────────────┘

                    ┌────────────────────────┐
                    │   RECORD SCREEN        │
                    │   - Waveform live      │
                    │   - Timer              │
                    │   - Live Transcript    │
                    │   - Add Marker         │
                    └───────┬────────────────┘
                            │ (STOP)
                            ▼
              ┌──────────────────────────────────┐
              │  PROCESS AUDIO (LOCAL ENGINE)    │
              │  - Preprocess (trim noise)        │
              │  - Whisper transcription final    │
              │  - Merge timestamps               │
              └───────────┬──────────────────────┘
                          │
                          ▼
           ┌────────────────────────────────────┐
           │         TRANSCRIPT DETAIL          │
           │────────────────────────────────────│
           │  Transcript View:                  │
           │  - Live transcript (thô)           │
           │  - Final transcript (Whisper)      │
           │  - Jump to audio by tapping line   │
           │  - Loop A-B                        │
           │                                    │
           │  Notes & Smart Features:           │
           │  - Auto keywords (TF-IDF)          │
           │  - Summary (rule-based)            │
           │  - Question list (detect '?')      │
           │  - Flashcard builder               │
           │  - Markers review                  │
           └───────────┬────────────────────────┘
                       │
                       ├────► EXPORT
                       │
                       ▼
         ┌──────────────────────────────────────┐
         │               EXPORT                 │
         │--------------------------------------│
         │  EXPORT OPTIONS                      │
         │  - Text (.txt)                       │
         │  - Markdown (.md)                    │
         │  - SRT / VTT subtitle                │
         │  - Copy selected section             │
         │  - Templates: Meeting / Lecture      │
         │  - Share to apps                     │
         └─────────────┬────────────────────────┘
                       │
                       ▼
          ┌──────────────────────────────────────┐
          │              HISTORY                 │
          │--------------------------------------│
          │  Recording List                      │
          │  - Search (FTS)                      │
          │  - Tags / Auto-tags                  │
          │  - Pin / Archive                     │
          │  - Reopen Transcript                 │
          └──────────────────────────────────────┘
```

---

# 🧠 **Giải thích luồng theo góc nhìn User**

### **1. App Start → Home**

User thấy 3 chức năng chính:

* **Record**
* **History**
* **Settings**

---

### **2. Record (Live)**

* Ghi âm realtime
* Waveform hiển thị
* Live transcript (nếu bạn hỗ trợ)
* Thêm marker lúc đang ghi

---

### **3. Stop → Whisper xử lý → Final transcript**

App chạy pipeline local:

* Chuẩn hóa audio
* Whisper tạo transcript
* Kết hợp timestamps
* Lưu vào DB + file audio

---

### **4. Transcript Detail — khu “thông minh” nhất app**

Có 3 phần chính:

#### **A. Transcript**

* Live vs Final
* Jump-to-audio
* Highlight markers
* Loop đoạn

#### **B. Smart Notes**

* Auto keywords (Local TF-IDF)
* Summary rule-based
* Detect questions
* Flashcards
* Notes thủ công (Pin đoạn)

#### **C. Tools**

* Chia đoạn → chapter
* Merge/split đoạn
* Tag recording

---

### **5. Export**

User chọn 1 format:

* TXT
* Markdown
* SRT/VTT
* Template Meeting/Lecture/Interview
* Copy section

---

### **6. Lưu lịch sử**

Trong History user có thể:

* Tìm bằng FTS (rất mạnh)
* Xem keywords
* Pin
* Tag
* Archive
* Mở lại transcript

---

# 🎉 **Bonus: FLOW DẠNG GỌN TUYỆT ĐẸP (Material App Flow)**

```
APP START
   ↓
HOME
   ├─ New Recording
   │      ↓
   │   Live Recording → Stop → Whisper → Transcript
   │      ↓
   │   Smart Notes (Keywords / Summary / Questions / Flashcards)
   │      ↓
   │   Export
   │
   └─ History
          ↓
       Transcript Detail → Export
```

---

Ok, mình làm 2 phần đúng yêu cầu:

1. Gợi ý UI chi tiết (đơn giản, hiện đại, tiện dụng).
2. Skeleton code + gợi ý triển khai chức năng chính (theo kiểu Clean Architecture + MVVM).

---

## 1. Chi tiết UI – đẹp, gọn, dễ dùng

### 1.1. Nguyên tắc chung

* **Material3 + edge-to-edge** (status bar trong suốt, content tràn lên, dùng `windowInsets` chuẩn).
* **1 màu chủ đạo** (primary) + nền hơi xám/trắng ngà → tập trung vào waveform & text.
* **Tối giản icon**: dùng 1 bộ icon consistent (Material Icons).
* **Font**: 2 cấp chính:

  * Title / Heading: `TitleLarge / TitleMedium`
  * Nội dung: `BodyMedium / BodySmall`
* **Corner radius lớn** (16–24dp) cho card để cảm giác “hiện đại”.
* **Spacing chuẩn**: 8–12–16–24dp, không random.

---

### 1.2. Màn hình HOME

**Mục tiêu**: 1 chạm là ghi âm, đồng thời thấy nhanh mấy file gần nhất.

**Layout:**

* **TopAppBar**:

  * Title: `Smart Recorder`
  * Actions: `Search` (icon), `Settings`.
* **Body**:

  1. **Primary Action Card** (to + nổi):

     * Icon micro lớn
     * Text: “Bắt đầu ghi âm mới”
     * Subtitle nhỏ: “Ghi âm + transcript + ghi chú thông minh”
  2. **Last Recording Card** (nếu có):

     * Title: tên file mới nhất
     * Subtitle: thời lượng, ngày giờ
     * Một dòng preview transcript (snippet).
  3. **Danh sách “Gần đây”** (Recent recordings):

     * `LazyColumn`, mỗi item là 1 `RecordingCard`:

```text
[●]  Lecture - Tim mạch
     32:14 • 23/11/2025 • 3 tags
     "Hôm nay chúng ta tìm hiểu về…"
     Chips: [Meeting] [Important] [Cardio]
     Icons:  ▶ Play   📝 Transcript   ↗ Export
```

* **FAB** (tùy bạn):

  * Góc dưới phải: icon micro → cũng dẫn vào Record.

---

### 1.3. Màn hình RECORDING

**Mục tiêu**: tập trung, ít phân tâm, nhìn waveform đã mắt.

**Layout:**

* **Full-screen, tone tối** (vd: background gần dark grey, waveform sáng).

* **Top bar mỏng**:

  * Back (X nếu chưa lưu / confirm).
  * Ở giữa: Timer (`00:12 / 30:00`).
  * Ở phải: nút “Mode” (Meeting / Lecture / Study) dạng chip.

* **Phần giữa**: Waveform lớn

  * Chiếm ~50–60% chiều cao.
  * Có line vertical ở giữa (position indicator).
  * Nếu được: gradient nhẹ, animation mượt.

* **Ngay dưới waveform**:

  * **Marker strip** (optional):

    * Dạng chấm / tick nhỏ theo timeline.
    * Khi record: bấm “Bookmark” → thêm tick.

* **Phần dưới**: Live transcript & nút điều khiển

  * Box bo tròn với background `surfaceVariant`, height ~ 160–220dp, scrollable:

    * Title nhỏ: “Live transcript (beta)”
    * Text chạy theo thời gian.
  * Hàng nút điều khiển (center aligned):

    * `Bookmark` (flag icon)
    * `Record/Pause` (icon tròn to)
    * `Stop` (hình vuông)

UI pseudo:

```text
┌──────────────────────────────┐
│ ←  00:12 / 30:00        Mode │
├──────────────────────────────┤
│          WAVEFORM            │
│   (chiếm phần lớn màn hình)  │
│   ▂▃▆█▇▆▃▁▂▇█▆▃▂...         │
│    | (playhead)              │
│  markers:   ·    ·     ·     │
├──────────────────────────────┤
│ Live transcript (beta)       │
│ "Hôm nay chúng ta sẽ bàn..." │
│ ...                          │
├──────────────────────────────┤
│ [★ Bookmark]  [⏺]  [■ Stop] │
└──────────────────────────────┘
```

---

### 1.4. Màn hình TRANSCRIPT DETAIL

**Mục tiêu**: trung tâm trí tuệ của app – transcript, notes, summary, export.

* **TopAppBar**:

  * Title: tên file
  * Subtitle nhỏ: `32:14 • 23/11/2025`
  * Actions: `Edit title`, `More (Export, Delete, Move to folder...)`.

* **Phần player** (trên cùng body):

  * Play/Pause, timeline scrubber, speed (1.0x, 1.5x, 2.0x).
  * Toggle nhỏ “Loop đoạn đã chọn”.

* **Tabs**:

  * `Transcript` | `Notes` | `Summary & Questions`

#### Tab 1: Transcript

* Thanh nhỏ:

```text
[● Final transcript]    [○ Live version]
```

* Danh sách câu transcript chia theo timestamp:

```text
[00:02] Hôm nay chúng ta sẽ tìm hiểu về hệ tim mạch.
[00:10] Đầu tiên là cấu trúc của tim...
[00:22] Câu hỏi đặt ra là: tại sao... (?)  ← highlight Question
```

* Interaction:

  * Tap vào 1 câu → seek audio đến timestamp + play.
  * Long press → mở bottom sheet:

    * Add note
    * Add marker
    * Thêm vào flashcard (Q/A)
    * Copy đoạn

#### Tab 2: Notes

* Nút top: `+ New Note`
* List note:

```text
- [Note] Đoạn 00:22 là chỗ giải thích cơ chế bệnh.
- [Todo] Ôn lại từ "cardiomyopathy".
```

* Có filter chip: All / Note / Todo / Marker.

#### Tab 3: Summary & Questions

* Block 1: **Auto Summary** (rule-based)
* Block 2: **Keywords** (chips)
* Block 3: **Questions detected**:

```text
❓ "Tại sao huyết áp lại tăng khi..."
❓ "Làm thế nào để chẩn đoán sớm..."
```

---

### 1.5. Màn hình EXPORT (Bottom Sheet)

Mở từ Transcript Detail → bottom sheet:

```text
Export & Share
──────────────
( ) Plain Text (.txt)
( ) Markdown (.md)
( ) Subtitles (.srt)
( ) Lecture Notes Template
( ) Meeting Minutes Template

[ Copy   ]   [ Save to file ]   [ Share ]
```

---

### 1.6. Màn hình HISTORY

* **Search bar** trên cùng, full width: “Tìm transcript, notes, từ khoá…”
* **Filter chips**: All, Meeting, Lecture, Study, Tagged, Pinned.
* Danh sách card (như Home → Recent).

---

## 2. Skeleton code & gợi ý triển khai

Giả sử bạn đi theo Clean Architecture (data / domain / presentation).

### 2.1. Package gợi ý

```text
com.yourcompany.smartrecorder

- data
  - local
    - db/SmartRecorderDatabase.kt
    - dao/RecordingDao.kt
    - dao/TranscriptDao.kt
    - entity/RecordingEntity.kt
    - entity/TranscriptSegmentEntity.kt
    - entity/NoteEntity.kt
  - repository
    - RecordingRepositoryImpl.kt
    - TranscriptRepositoryImpl.kt
- domain
  - model
    - Recording.kt
    - TranscriptSegment.kt
    - Note.kt
  - repository
    - RecordingRepository.kt
    - TranscriptRepository.kt
  - usecase
    - StartRecordingUseCase.kt
    - StopRecordingAndSaveUseCase.kt
    - GenerateTranscriptUseCase.kt
    - ExtractKeywordsUseCase.kt
    - GenerateSummaryUseCase.kt
    - GetRecordingListUseCase.kt
    - GetRecordingDetailUseCase.kt
    - ExportTranscriptUseCase.kt
- core
  - audio
    - AudioRecorder.kt
    - AudioPlayer.kt
    - AudioPreprocessor.kt
  - stt
    - SttEngine.kt
    - WhisperSttEngine.kt
  - export
    - TextFormatter.kt
    - SrtFormatter.kt
- ui (presentation)
  - home
    - HomeScreen.kt
    - HomeViewModel.kt
  - record
    - RecordScreen.kt
    - RecordViewModel.kt
  - transcript
    - TranscriptScreen.kt
    - TranscriptViewModel.kt
  - history
    - HistoryScreen.kt
    - HistoryViewModel.kt
  - components
    - WaveformView.kt
    - RecordingCard.kt
    - TranscriptLineItem.kt
```

---

### 2.2. Entity & Model – ví dụ

```kotlin
// data/local/entity/RecordingEntity.kt
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val filePath: String,
    val createdAt: Long,
    val durationMs: Long,
    val mode: String,        // MEETING, LECTURE, STUDY...
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)

// data/local/entity/TranscriptSegmentEntity.kt
@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = CASCADE
        )
    ],
    indices = [Index("recordingId")]
)
data class TranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val isQuestion: Boolean = false
)
```

---

### 2.3. Repository interface

```kotlin
// domain/repository/RecordingRepository.kt
interface RecordingRepository {
    suspend fun insertRecording(recording: Recording)
    suspend fun updateRecording(recording: Recording)
    suspend fun getRecording(id: String): Recording?
    fun getRecordingsFlow(): Flow<List<Recording>>
    fun searchRecordings(query: String): Flow<List<Recording>>
}

// domain/repository/TranscriptRepository.kt
interface TranscriptRepository {
    suspend fun saveTranscriptSegments(
        recordingId: String,
        segments: List<TranscriptSegment>
    )
    fun getTranscriptSegments(recordingId: String): Flow<List<TranscriptSegment>>
}
```

Implementation trong `data/repository/...` map Entity ↔ Model.

---

### 2.4. Core audio & STT skeleton

```kotlin
// core/audio/AudioRecorder.kt
interface AudioRecorder {
    suspend fun startRecording(outputFile: File)
    suspend fun stopRecording(): File
    suspend fun pause()
    suspend fun resume()
}

// core/stt/SttEngine.kt
interface SttEngine {
    suspend fun transcribe(
        audioFile: File,
        onPartialResult: (String) -> Unit = {}
    ): List<TranscriptSegment>
}

// core/stt/WhisperSttEngine.kt
class WhisperSttEngine(
    private val modelPath: String
) : SttEngine {
    override suspend fun transcribe(
        audioFile: File,
        onPartialResult: (String) -> Unit
    ): List<TranscriptSegment> {
        // TODO: call Whisper native / JNI
        // - load model
        // - run inference
        // - split to segments with timestamps
        return emptyList()
    }
}
```

---

### 2.5. UseCases chính & gợi ý triển khai

#### 1) **StartRecordingUseCase**

```kotlin
class StartRecordingUseCase(
    private val audioRecorder: AudioRecorder,
    private val fileProvider: RecordingFileProvider
) {
    suspend operator fun invoke(): Recording {
        val file = fileProvider.createNewTempFile()
        audioRecorder.startRecording(file)
        return Recording(
            id = generateId(),
            title = "",
            filePath = file.absolutePath,
            createdAt = System.currentTimeMillis(),
            durationMs = 0L,
            mode = "DEFAULT"
        )
    }
}
```

* Gợi ý:

  * Chỉ tạo Recording tạm trong ViewModel.
  * Khi stop xong → biết duration → mới insert DB.

#### 2) **StopRecordingAndSaveUseCase**

```kotlin
class StopRecordingAndSaveUseCase(
    private val audioRecorder: AudioRecorder,
    private val recordingRepository: RecordingRepository
) {
    suspend operator fun invoke(recording: Recording, durationMs: Long): Recording {
        val file = audioRecorder.stopRecording()
        val final = recording.copy(
            filePath = file.absolutePath,
            durationMs = durationMs
        )
        recordingRepository.insertRecording(final)
        return final
    }
}
```

#### 3) **GenerateTranscriptUseCase (dùng Whisper)**

```kotlin
class GenerateTranscriptUseCase(
    private val sttEngine: SttEngine,
    private val transcriptRepository: TranscriptRepository
) {
    suspend operator fun invoke(recording: Recording) {
        val audioFile = File(recording.filePath)
        val segments = sttEngine.transcribe(audioFile)
        transcriptRepository.saveTranscriptSegments(recording.id, segments)
    }
}
```

* Gợi ý:

  * Chạy trong `viewModelScope.launch(Dispatchers.IO)`.
  * Emit state loading / progress để show UI.

#### 4) **ExtractKeywordsUseCase**

```kotlin
class ExtractKeywordsUseCase {
    operator fun invoke(fullText: String, topN: Int = 10): List<String> {
        // TODO:
        // - tokenize
        // - remove stopwords
        // - count frequency
        // - sort & take topN
        return emptyList()
    }
}
```

#### 5) **GenerateSummaryUseCase** (rule-based)

```kotlin
class GenerateSummaryUseCase {
    operator fun invoke(fullText: String): String {
        // TODO:
        // - split sentences
        // - pick sentences with cues: "tóm lại", "kết luận", "vì vậy",...
        // - ensure diversity (spread in text)
        return ""
    }
}
```

#### 6) **ExportTranscriptUseCase**

```kotlin
class ExportTranscriptUseCase(
    private val textFormatter: TextFormatter,
    private val srtFormatter: SrtFormatter
) {
    fun exportAsText(
        recording: Recording,
        segments: List<TranscriptSegment>
    ): String {
        return textFormatter.toPlainText(recording, segments)
    }

    fun exportAsSrt(
        recording: Recording,
        segments: List<TranscriptSegment>
    ): String {
        return srtFormatter.toSrt(segments)
    }
}
```

---

### 2.6. ViewModel – ví dụ cho Record & Transcript

#### RecordViewModel

```kotlin
data class RecordUiState(
    val isRecording: Boolean = false,
    val durationMs: Long = 0L,
    val liveText: String = "",
    val error: String? = null
)

class RecordViewModel(
    private val startRecording: StartRecordingUseCase,
    private val stopRecordingAndSave: StopRecordingAndSaveUseCase
) : ViewModel() {

    var uiState by mutableStateOf(RecordUiState())
        private set

    private var currentRecording: Recording? = null
    private var timerJob: Job? = null

    fun onStartClick() {
        viewModelScope.launch {
            currentRecording = startRecording()
            uiState = uiState.copy(isRecording = true, durationMs = 0)
            startTimer()
        }
    }

    fun onPauseClick() { /* TODO: pause recorder + stop timer */ }

    fun onStopClick() {
        viewModelScope.launch {
            timerJob?.cancel()
            val recording = currentRecording ?: return@launch
            val saved = stopRecordingAndSave(recording, uiState.durationMs)
            // TODO: navigate to Transcript screen with saved.id
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                uiState = uiState.copy(durationMs = uiState.durationMs + 1000)
            }
        }
    }
}
```

#### TranscriptViewModel

```kotlin
data class TranscriptUiState(
    val recording: Recording? = null,
    val segments: List<TranscriptSegment> = emptyList(),
    val summary: String = "",
    val keywords: List<String> = emptyList(),
    val questions: List<TranscriptSegment> = emptyList(),
    val isLoading: Boolean = false
)

class TranscriptViewModel(
    private val getRecordingDetail: GetRecordingDetailUseCase,
    private val getTranscript: GetTranscriptUseCase,
    private val generateSummary: GenerateSummaryUseCase,
    private val extractKeywords: ExtractKeywordsUseCase
) : ViewModel() {

    var uiState by mutableStateOf(TranscriptUiState())
        private set

    fun load(recordingId: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)

            val rec = getRecordingDetail(recordingId)
            val segments = getTranscript(recordingId)

            val fullText = segments.joinToString(" ") { it.text }
            val summary = generateSummary(fullText)
            val keywords = extractKeywords(fullText)
            val questions = segments.filter { it.text.trim().endsWith("?") }

            uiState = uiState.copy(
                recording = rec,
                segments = segments,
                summary = summary,
                keywords = keywords,
                questions = questions,
                isLoading = false
            )
        }
    }
}
```

---




Ok, chơi full combo luôn 🎧📄
Mình chia làm 3 khối lớn:

1. Flow chi tiết cho: Smart Note, Import Audio, Flashcard, Export Template
2. Compose UI skeleton cho `RecordScreen`, `TranscriptScreen`
3. Whisper integration plan (JNI, thread, chunking, progress, offline 100%)

---

## 1. Các FLOW chi tiết

### 1.1. Flow **Smart Note** (ở Transcript Detail)

**Mục tiêu:** Biến transcript dài → notes, summary, keyword, questions, flashcard.

#### Luồng tổng:

```text
[Transcript Detail Screen]
        │
        ├─ User đọc transcript
        │
        ├─ (Auto) App chạy background:
        │       - Generate keywords
        │       - Detect questions
        │       - Generate summary (rule-based)
        │       => Lưu vào DB (NoteEntity / metadata)
        │
        ├─ User long-press 1 câu transcript
        │       ↓
        │   Bottom Sheet:
        │       - Add Note
        │       - Mark as Important
        │       - Add to Todo
        │       - Create Flashcard (Q/A)
        │
        ├─ User chuyển sang tab [Notes]
        │       ↓
        │   Xem list:
        │       - Notes manual
        │       - Auto summary
        │       - Todo
        │
        └─ User chỉnh / xóa / pin Note
```

#### State/DB liên quan

* `NoteEntity`:

  * `id, recordingId, segmentId?, type (SUMMARY/TODO/NOTE/FLASHCARD_Q/FLASHCARD_A), content, createdAt`
* `TranscriptSegmentEntity`:

  * có `segmentId` để link notes vào đúng đoạn.

---

### 1.2. Flow **Import file audio**

**Mục tiêu:** User có file .mp3/.wav ngoài → import → transcript & notes giống như bản ghi âm trong app.

```text
[Home / History Screen]
      │
      ├─ User bấm nút "Import Audio"
      │
      ↓
 [System File Picker]
      │
      ├─ User chọn file audio
      │
      ↓
 [Import Processing Screen] (hoặc dialog/progress)
      │
      ├─ Preprocess:
      │   - Copy file về app storage (app-specific dir)
      │   - Chuẩn hóa: convert → 16kHz mono (nếu cần)
      │
      ├─ Tạo Recording mới trong DB:
      │   - mode = IMPORTED
      │   - title = tên file (có thể user chỉnh sau)
      │
      ├─ Gọi Whisper transcribe:
      │   - Show progress (0% → 100%)
      │
      └─ Sau khi xong:
            → Điều hướng sang [Transcript Detail]
```

---

### 1.3. Flow **Flashcard**

**Mục tiêu:** Tạo flashcard từ transcript + notes → luyện tập cho ôn thi.

```text
[Transcript Detail Screen]
       │
       ├─ User long-press câu hỏi
       │     ↓
       │   "Create Flashcard"
       │     - Set as Question
       │     - Chọn Answer (text khác hoặc same segment)
       │     → Lưu Flashcard
       │
       ├─ Tab [Summary & Questions]
       │     - Hiển thị list câu hỏi
       │     - Cho phép "Make Flashcard" nhanh
       │
       └─ [Flashcards Screen] (entry từ menu Home/Transcript)
             │
             ├─ Chọn Deck (theo recording/tag)
             │
             ├─ Practice Mode:
             │      - Show Question
             │      - User bấm "Show Answer"
             │      - User đánh giá: Easy / Medium / Hard
             │      → Update nextReviewDate (spaced repetition đơn giản)
             │
             └─ User có thể:
                    - Edit nội dung card
                    - Delete card
                    - Pin card quan trọng
```

DB gợi ý:

* `FlashcardEntity`

  * `id, recordingId, questionText, answerText, createdAt, nextReviewAt, box (Leitner box)`

---

### 1.4. Flow **Export Template**

**Mục tiêu:** Cho user chọn template phù hợp (Meeting, Lecture, Interview) và format text tương ứng.

```text
[Transcript Detail Screen]
       │
       └─ User bấm icon "Export"
               ↓
        [Export Bottom Sheet]
               │
               ├─ Chọn Format:
               │     - Plain Text (.txt)
               │     - Markdown (.md)
               │     - Subtitles (.srt)
               │
               ├─ Chọn Template (optional):
               │     - None (raw)
               │     - Meeting Minutes
               │     - Lecture Notes
               │     - Interview Q&A
               │
               └─ Action:
                     - Copy
                     - Save to File (Document API)
                     - Share (Intent)
```

* Logic:

  * `ExportTranscriptUseCase`:

    * `toPlainText(recording, segments, notes, templateType)`
    * `toMarkdown(...)`
    * `toSrt(segments)`

---

## 2. Compose UI skeleton

### 2.1. `RecordScreen`

Skeleton theo layout đã nói (waveform giữa, live transcript dưới, controls cuối).

```kotlin
@Composable
fun RecordScreen(
    uiState: RecordUiState,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onBackClick: () -> Unit,
    onModeClick: () -> Unit,
) {
    val isRecording = uiState.isRecording

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = {
                    Text(
                        text = formatDuration(uiState.durationMs),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                },
                actions = {
                    AssistChip(
                        onClick = onModeClick,
                        label = { Text(text = uiState.modeLabel) }
                    )
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Waveform
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                // TODO: WaveformView(uiState.waveformData)
                Text(
                    text = "Waveform Preview",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Live transcript box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Live transcript",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = uiState.liveText.ifBlank { "Đang chờ giọng nói..." },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Controls row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBookmarkClick) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = "Bookmark")
                }

                // Big central button
                ElevatedButton(
                    onClick = {
                        if (!isRecording) onStartClick() else onPauseClick()
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    val icon = if (!isRecording) Icons.Default.Mic else Icons.Default.Pause
                    Icon(icon, contentDescription = "Record/Pause")
                }

                IconButton(
                    onClick = onStopClick,
                    enabled = isRecording || uiState.durationMs > 0
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
```

---

### 2.2. `TranscriptScreen`

Skeleton với TopBar, player, tabs, transcript/notes/summary.

```kotlin
@Composable
fun TranscriptScreen(
    uiState: TranscriptUiState,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onExportClick: () -> Unit,
    onTranscriptLongPress: (TranscriptSegment) -> Unit,
    onTabChange: (TranscriptTab) -> Unit
) {
    val recording = uiState.recording
    val currentTab = uiState.currentTab

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = {
                    Column {
                        Text(
                            text = recording?.title ?: "Recording",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (recording != null) {
                            Text(
                                text = "${formatDuration(recording.durationMs)} • ${
                                    formatDate(recording.createdAt)
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onExportClick) {
                        Icon(Icons.Default.IosShare, contentDescription = "Export")
                    }
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Player bar
            PlayerBar(
                isPlaying = uiState.isPlaying,
                currentPosMs = uiState.currentPositionMs,
                durationMs = recording?.durationMs ?: 0L,
                onPlayPauseClick = onPlayPauseClick,
                onSeekTo = onSeekTo
            )

            // Tabs
            TabRow(
                selectedTabIndex = currentTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                TranscriptTab.values().forEachIndexed { index, tab ->
                    Tab(
                        selected = index == currentTab.ordinal,
                        onClick = { onTabChange(tab) },
                        text = { Text(tab.label) }
                    )
                }
            }

            when (currentTab) {
                TranscriptTab.TRANSCRIPT -> TranscriptTabContent(uiState, onTranscriptLongPress)
                TranscriptTab.NOTES -> NotesTabContent(uiState)
                TranscriptTab.SUMMARY -> SummaryTabContent(uiState)
            }
        }
    }
}

enum class TranscriptTab(val label: String) {
    TRANSCRIPT("Transcript"),
    NOTES("Notes"),
    SUMMARY("Summary & Questions")
}

@Composable
private fun PlayerBar(
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlayPauseClick) {
                val icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
                Icon(icon, contentDescription = "Play/Pause")
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Slider(
                    value = currentPosMs.toFloat(),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..durationMs.toFloat(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(currentPosMs), style = MaterialTheme.typography.labelSmall)
                    Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TranscriptTabContent(
    uiState: TranscriptUiState,
    onTranscriptLongPress: (TranscriptSegment) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(uiState.segments) { seg ->
            TranscriptLineItem(
                segment = seg,
                isCurrent = seg.id == uiState.currentSegmentId,
                onLongPress = { onTranscriptLongPress(seg) }
            )
        }
    }
}

@Composable
fun TranscriptLineItem(
    segment: TranscriptSegment,
    isCurrent: Boolean,
    onLongPress: () -> Unit
) {
    val bgColor =
        if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* jump to time handled outside if cần */ },
                onLongClick = onLongPress
            )
            .background(bgColor)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "[${formatDuration(segment.startTimeMs)}]",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = segment.text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
```

(`NotesTabContent`, `SummaryTabContent` bạn có thể build tương tự: list note, summary text, danh sách questions…)

---

## 3. Whisper Integration Plan (offline, JNI, chunking, progress)

### 3.1. Kiến trúc tổng

```text
[RecordScreen] / [Import Audio]
      │
      └─> Audio file (.wav, 16kHz mono)
               │
               ▼
      [WhisperSttEngine] (Kotlin)
               │   (JNI)
               ▼
      [Native Layer - C/C++ whisper.cpp]
               │
               ▼
      Segments (text + start/end timestamps)
               │
               ▼
      [TranscriptRepository.saveTranscriptSegments()]
               │
               ▼
      UI: TranscriptScreen
```

---

### 3.2. JNI / Native layer

1. Bạn build whisper.cpp thành `.so` (Android NDK).
2. Expose JNI:

```cpp
// C++ (pseudo)
extern "C"
JNIEXPORT jlong JNICALL
Java_com_yourcompany_smartrecorder_core_stt_WhisperNative_initModel(
    JNIEnv* env,
    jobject thiz,
    jstring modelPath
);

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_yourcompany_smartrecorder_core_stt_WhisperNative_transcribeFile(
    JNIEnv* env,
    jobject thiz,
    jlong ctxPtr,
    jstring audioPath,
    jobject callback
);
```

Kotlin wrapper:

```kotlin
object WhisperNative {
    external fun initModel(modelPath: String): Long
    external fun transcribeFile(
        ctxPtr: Long,
        audioPath: String,
        callback: WhisperCallback?
    ): Array<WhisperSegment>
}

interface WhisperCallback {
    fun onProgress(percent: Int)
}
```

---

### 3.3. Model & file

* Model: **whisper-tiny** hoặc **tiny.en** (~75MB).
* Stored as:

  * Asset → copy ra `filesDir/models` lần đầu.
  * Hoặc packaged trong `downloadable` optional nếu muốn giảm APK size (sau).

---

### 3.4. Chunking audio

Để không bị quá tải RAM & tăng cảm giác “tiến trình”:

* Nếu audio **≤ 5 phút**: gửi 1 file.
* Nếu **> 5 phút**:

  * Cắt thành chunk 2–5 phút, có **overlap** ~5–10s để tránh mất chữ.
  * Whisper transcribe từng chunk.
  * Merge segments:

    * Thời gian chunk2 = offset + localTime.
    * Giải quyết overlap bằng:

      * Bỏ đoạn trùng > 50% text giống.
      * Hoặc ưu tiên segment từ chunk trước.

Pseudo:

```kotlin
suspend fun transcribeWithChunking(audioFile: File): List<TranscriptSegment> {
    val chunks = audioChunker.splitWithOverlap(audioFile, maxMinutes = 5)
    val allSegments = mutableListOf<TranscriptSegment>()
    var offsetMs = 0L

    for ((index, chunk) in chunks.withIndex()) {
        val segments = nativeTranscribe(chunk.file, onProgress = { p ->
            // progress = (index + p%) / totalChunks
        })
        segments.forEach { seg ->
            allSegments += seg.copy(
                startTimeMs = seg.startTimeMs + offsetMs,
                endTimeMs = seg.endTimeMs + offsetMs
            )
        }
        offsetMs += chunk.effectiveDurationMs
    }

    return mergeOverlaps(allSegments)
}
```

---

### 3.5. Threading & progress

* Luôn chạy STT trong `Dispatchers.IO` (hoặc `newSingleThreadContext("whisper")`).
* ViewModel:

```kotlin
fun generateTranscript(recordingId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isGeneratingTranscript = true, progress = 0) }

        generateTranscriptUseCase(
            recordingId = recordingId,
            onProgress = { percent ->
                _uiState.update { it.copy(progress = percent) }
            }
        )

        _uiState.update { it.copy(isGeneratingTranscript = false, progress = 100) }
    }
}
```

* UI:

  * Hiển thị dialog / bottom sheet:

    * “Đang tạo transcript… 37%”
    * Có nút Cancel (gọi `job.cancel()`).

---

### 3.6. Cancellation

* JNI nên check “đã bị cancel chưa?” thông qua biến global / atomic flag.
* Trong Kotlin:

```kotlin
val job = viewModelScope.launch(Dispatchers.IO) {
    nativeTranscribeWithCancellation(recording, cancelFlag)
}

fun onCancelTranscription() {
    cancelFlag.set(true)
    job.cancel()
}
```

* Trong C++:

  * Mỗi frame/step check flag → nếu true thì dừng sớm.

---

### 3.7. Memory & performance tips

* Chỉ **init model** một lần:

  * `val ctxPtr = WhisperNative.initModel(modelPath)`
  * Giữ ctxPtr trong singleton / DI (`@Singleton` Hilt).
* Đừng load model nhiều lần mỗi recording.
* Có thể lazy load:

  * Khi user lần đầu chọn “Create transcript”.
* Log:

  * Thời gian load model
  * Thời gian xử lý mỗi phút audio
  * Memory usage sơ bộ (nếu bạn muốn debug).

---

### 3.8. Full pipeline tóm tắt

```text
[Record / Import]
   ↓
[Audio file ready]
   ↓
(ViewModel)
   - set isGeneratingTranscript = true
   - call GenerateTranscriptUseCase
   ↓
[GenerateTranscriptUseCase]
   - chunking (nếu cần)
   - WhisperNative.transcribeFile(...) per chunk
   - build List<TranscriptSegment>
   - transcriptRepository.saveTranscriptSegments()
   ↓
(ViewModel)
   - load segments from repository
   - run ExtractKeywords + GenerateSummary + DetectQuestions
   - update TranscriptUiState
   ↓
[TranscriptScreen UI]
   - hiển thị transcript
   - tabs Notes / Summary & Questions
```

---
