# 🎨 Player UI Design - Smart Recorder

## 📋 Tổng Quan

Tài liệu này mô tả chi tiết thiết kế UI cho audio player trong app Smart Recorder, với **3 options** rõ ràng để team có thể chọn và implement.

**Màu sắc chính:**
- Primary Orange: `#FF6B35` (vibrant orange)
- Surface Variant: `#FFE5D9` (light orange background)
- On Primary: `#FFFFFF` (white text/icons)

---

## 🎯 Design Consistency - Thống Nhất UI

### Nguyên Tắc Chung

Mặc dù UI trong app (Option 1) và UI ngoài app (Option 3 - Notification/Lock Screen) có **layout khác nhau** do constraints khác nhau, nhưng chúng phải **thống nhất** về:

#### ✅ Những Gì PHẢI Giống Nhau (Unified)

1. **Màu Sắc Brand:**
   - Primary Orange: `#FF6B35` (dùng cho tất cả accent colors)
   - Background cam nhạt: `#FFE5D9` (nếu có background)
   - Icon màu: Trắng trên nền cam, hoặc cam trên nền trắng

2. **Icon Style:**
   - Play: `Icons.Default.PlayArrow` (Material Icons)
   - Pause: `Icons.Default.Pause` (Material Icons)
   - Rewind: `Icons.Default.Replay10` hoặc custom `ic_rewind_10`
   - Forward: `Icons.Default.Forward10` hoặc custom `ic_forward_10`
   - **Tất cả icons phải cùng style, cùng weight**

3. **Time Format:**
   - Format: `MM:SS` (ví dụ: `00:26`, `03:31`)
   - Không dùng `H:MM:SS` trừ khi > 1 giờ
   - Font: System default hoặc Material typography
   - Color: Xám đậm (#475569) hoặc onSurfaceVariant

4. **Progress Bar Style:**
   - Active color: `#FF6B35` (primary orange)
   - Inactive color: `#FF6B35` với alpha 0.2
   - Height: `4dp` (mảnh, hiện đại)
   - Thumb: Circle, màu cam, size `10dp`

5. **Button Actions:**
   - **Bắt buộc:** Play/Pause (luôn có)
   - **Tùy chọn:** Rewind 10s, Forward 10s (có thể thêm vào Option 1)
   - Layout: Horizontal, spacing đều

6. **States:**
   - Playing: Icon Pause, progress đang chạy
   - Paused: Icon Play, progress dừng
   - Loading: CircularProgressIndicator hoặc disabled state

#### ⚠️ Những Gì CÓ THỂ Khác Nhau (Platform Constraints)

1. **Layout Structure:**
   - **Option 1 (In-app):** Card với gradient, có thể có title, spacing rộng
   - **Option 3 (Notification):** Compact, system constraints, không có gradient

2. **Button Size:**
   - **Option 1:** `40dp × 40dp` (tròn, lớn, dễ tap)
   - **Option 3:** System default (~24dp icon trong notification)

3. **Spacing:**
   - **Option 1:** `16dp` padding, `8dp` margins
   - **Option 3:** System default (tighter spacing)

4. **Background:**
   - **Option 1:** Card với gradient cam nhạt
   - **Option 3:** System notification background (trắng/xám)

5. **Additional Controls:**
   - **Option 1:** Có thể thêm loop button, speed control (nếu cần)
   - **Option 3:** Chỉ có Rewind, Play/Pause, Forward (standard)

### 📐 Unified Layout Pattern

Dù layout khác nhau, nhưng **pattern** phải giống:

```
[Icon/Button]  [Progress Bar]  [Time Labels]
     ↓              ↓              ↓
   Play/Pause    ────●─────    00:26 / 03:31
```

**Trong app (Option 1):**
```
┌────────────────────────────────────┐
│  [▶]  ────────────●─────────────  │
│        00:26              03:31    │
└────────────────────────────────────┘
```

**Ngoài app (Option 3):**
```
00:26 ────────────●────────── 03:31
[⏮]  [▶]  [⏭]
```

→ **Cùng pattern:** Button bên trái, Progress ở giữa, Time labels dưới progress

---

## 🅰️ OPTION 1 – Card Player dưới AppBar (RECOMMENDED)

### 📐 Wireframe & Layout

```
┌─────────────────────────────────────────────────────────┐
│ ← Thoại 019                    🔍 🔖 ↗ 🗑              │ ← TopAppBar (Material3)
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │                                                   │  │
│  │  ┌────────────────────────────────────────────┐  │  │
│  │  │  ╔═══════════════════════════════════════╗ │  │  │
│  │  │  ║  [▶]  ────────────●───────────────   ║ │  │  │ ← Card Player
│  │  │  ║        00:26                  03:31  ║ │  │  │   (nền cam nhạt)
│  │  │  ╚═══════════════════════════════════════╝ │  │  │
│  │  └────────────────────────────────────────────┘  │  │
│  │                                                   │  │
│  └──────────────────────────────────────────────────┘  │
│                                                          │
│  ┌──────────┬──────────┬──────────┐                    │
│  │TRANSCRIPT│  NOTES   │ SUMMARY  │                    │ ← Tabs
│  └──────────┴──────────┴──────────┘                    │
│  ────────────────────────────────────────────────────  │
│                                                          │
│  Speaker 1: Jockerfie is my favourite subject...       │
│  [Highlighted segment khi đang phát]                    │
│                                                          │
│  Speaker 2: Yes, I agree with that...                   │
│                                                          │
│  ...                                                     │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 🎨 Chi Tiết Thiết Kế

#### 1. Card Container
- **Kích thước:**
  - Width: `fillMaxWidth()` với padding horizontal `16.dp`
  - Height: ~`64-72dp` (tự động theo content)
  - Margin top: `8.dp` (cách AppBar)
  - Margin bottom: `8.dp` (cách Tabs)

- **Styling:**
  - Background: `MaterialTheme.colorScheme.surfaceVariant` (#FFE5D9 - cam nhạt)
  - Corner radius: `24.dp` (bo tròn lớn, hiện đại)
  - Elevation: `2.dp` (shadow nhẹ)
  - Padding: `10.dp` (padding ngoài)

#### 2. Inner Container (Gradient Layer)
- **Styling:**
  - Background: Horizontal gradient từ `primary.copy(alpha = 0.10f)` → `primary.copy(alpha = 0.05f)`
  - Corner radius: `20.dp` (nhỏ hơn outer một chút)
  - Padding: `16.dp` horizontal, `12.dp` vertical

#### 3. Control Buttons

##### 3.1. Play/Pause Button (Bắt buộc)
- **Kích thước:**
  - Size: `40.dp × 40.dp` (tròn)
  - Icon size: `24.dp`

- **Styling:**
  - Background: `MaterialTheme.colorScheme.primary` (#FF6B35 - cam đậm)
  - Shape: `CircleShape`
  - Icon color: `Color.White`
  - Icon: `Icons.Default.PlayArrow` / `Icons.Default.Pause`

- **Vị trí:** Bên trái, cách lề trái `16.dp`

##### 3.2. Rewind/Forward Buttons (Tùy chọn - để thống nhất với Option 3)
- **Kích thước:**
  - Size: `32.dp × 32.dp` (tròn, nhỏ hơn Play/Pause)
  - Icon size: `20.dp`

- **Styling:**
  - Background: `Transparent` hoặc `SurfaceVariant.copy(alpha = 0.5f)`
  - Shape: `CircleShape`
  - Icon color: `MaterialTheme.colorScheme.primary` (#FF6B35)
  - Icon: `Icons.Default.Replay10` / `Icons.Default.Forward10`

- **Vị trí:** 
  - Rewind: Bên trái Play/Pause, spacing `8.dp`
  - Forward: Bên phải Play/Pause, spacing `8.dp`
  - **Hoặc:** Có thể bỏ qua nếu muốn UI gọn hơn (chỉ giữ Play/Pause)

- **Layout Option A (Chỉ Play/Pause - Recommended):**
```
┌────────────────────────────────────┐
│  [▶]  ────────────●─────────────  │
│        00:26              03:31    │
└────────────────────────────────────┘
```

- **Layout Option B (Có Rewind/Forward - Thống nhất với Notification):**
```
┌────────────────────────────────────┐
│  [⏮] [▶] [⏭]  ────●─────────────  │
│        00:26              03:31    │
└────────────────────────────────────┘
```

#### 4. Progress Slider
- **Kích thước:**
  - Height: `4.dp` (thanh mảnh)
  - Width: `fillMaxWidth()` (chiếm hết không gian còn lại)

- **Styling:**
  - Active track: `MaterialTheme.colorScheme.primary` (#FF6B35)
  - Inactive track: `primary.copy(alpha = 0.2f)` (cam nhạt)
  - Thumb: Circle `10.dp`, màu `primary`

- **Vị trí:** Ở giữa, dưới title (nếu có)

#### 5. Time Labels
- **Typography:**
  - Style: `MaterialTheme.typography.labelSmall`
  - Color: `MaterialTheme.colorScheme.onSurfaceVariant` (#475569 - xám đậm)

- **Layout:**
  - Format: `MM:SS` (ví dụ: `00:26`, `03:31`)
  - Left: Current position
  - Right: Total duration
  - Arrangement: `SpaceBetween`

#### 6. Title (Optional)
- **Typography:**
  - Style: `MaterialTheme.typography.labelMedium`
  - Color: `MaterialTheme.colorScheme.onSurfaceVariant`
  - Max lines: `1`
  - Ellipsize: `end`

- **Vị trí:** Trên slider (nếu cần hiện tên file)

### 📱 Responsive & States

#### Playing State
```
┌────────────────────────────────────┐
│  [⏸]  ────────────●─────────────  │
│        00:26              03:31    │
└────────────────────────────────────┘
```
- Icon: Pause
- Progress bar: Đang chạy (animated)

#### Paused State
```
┌────────────────────────────────────┐
│  [▶]  ────────────●─────────────  │
│        00:26              03:31    │
└────────────────────────────────────┘
```
- Icon: Play
- Progress bar: Dừng tại vị trí hiện tại

#### Loading State
```
┌────────────────────────────────────┐
│  [⟳]  ─────────────────────────  │
│        Loading...                  │
└────────────────────────────────────┘
```
- Icon: CircularProgressIndicator
- Disable slider

### ✅ Ưu Điểm
- ✅ Gọn gàng, hiện đại, dễ nhìn
- ✅ Tách biệt rõ với transcript content
- ✅ Hợp với Material 3 design system
- ✅ Dễ implement với Compose
- ✅ Không chiếm quá nhiều không gian

### ⚠️ Lưu Ý Implementation
- Sử dụng `Card` composable với `RoundedCornerShape`
- Gradient dùng `Brush.horizontalGradient`
- Slider cần debounce khi seek để tránh lag
- Nhớ sync với ViewModel state

---

## 🅾️ OPTION 3 – Media Notification & Lock Screen

### 📐 Wireframe Notification (Kéo Status Bar xuống)

```
┌─────────────────────────────────────────────────────────┐
│ 🎙 Smart Recorder                                      │ ← App name + icon
├─────────────────────────────────────────────────────────┤
│ Thoại 019                                               │ ← Title
│                                                          │
│ Jockerfie is my favourite subject because...           │ ← Preview text
│                                                          │
│ 00:26 ────────────●──────────────── 03:31              │ ← Progress bar
│                                                          │
│ ┌──────┐  ┌──────┐  ┌──────┐                           │
│ │ ⏮ -10│  │ ⏯    │  │ ⏭ +10│                           │ ← Actions
│ └──────┘  └──────┘  └──────┘                           │
└─────────────────────────────────────────────────────────┘
```

### 📐 Wireframe Lock Screen

```
┌─────────────────────────────────────────────────────────┐
│                                                          │
│                    [🔶 Large Icon]                      │ ← 64dp icon
│                                                          │
│                    Thoại 019                             │ ← Title
│                                                          │
│        Jockerfie is my favourite subject...             │ ← Preview
│                                                          │
│        00:26 ────────────●────────────── 03:31          │ ← Progress
│                                                          │
│              ┌──────┐  ┌──────┐  ┌──────┐              │
│              │ ⏮    │  │ ⏯    │  │ ⏭    │              │ ← Actions
│              └──────┘  └──────┘  └──────┘              │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 🎨 Chi Tiết Thiết Kế

#### 1. Notification Channel
- **ID:** `"recorder_playback_channel"`
- **Name:** "Smart Recorder playback"
- **Importance:** `IMPORTANCE_LOW` (không làm phiền user)
- **Description:** "Playback controls for Smart Recorder"
- **Show Badge:** `false`

#### 2. Notification Header
- **Small Icon:**
  - Resource: `R.drawable.ic_notification_mic` (icon mic màu cam)
  - Size: `24dp × 24dp` (system standard)
  - Color: `#FF6B35` (primary orange) - **THỐNG NHẤT với Option 1**

- **App Name:** "Smart Recorder"
- **Color Accent:** `ContextCompat.getColor(context, R.color.primary_orange)` (#FF6B35)
  - **⚠️ QUAN TRỌNG:** Phải set `.setColor()` để progress bar và accent dùng màu cam

#### 3. Content
- **Title:**
  - Text: Recording title (ví dụ: "Thoại 019")
  - Style: System notification title style
  - Max lines: 1

- **Preview Text:**
  - Text: 1-2 câu đầu của transcript hiện tại
  - Style: System notification text style
  - Max lines: 2
  - Ellipsize: `end`

#### 4. Progress Bar
- **Format:**
  - Current: `00:26` (left) - **THỐNG NHẤT format với Option 1**
  - Total: `03:31` (right) - **THỐNG NHẤT format với Option 1**
  - Progress: `setProgress(max, current, false)`

- **Styling:**
  - Active color: `#FF6B35` (primary orange) - **THỐNG NHẤT với Option 1**
  - Inactive color: System default (sẽ tự động dùng alpha từ accent color)
  - Height: System default (~4dp) - **THỐNG NHẤT với Option 1**
  - **⚠️ QUAN TRỌNG:** Phải set `.setColor(ContextCompat.getColor(context, R.color.primary_orange))` để đảm bảo màu cam

#### 5. Actions (Bắt buộc có đầy đủ 3 buttons)

- **Rewind 10s:**
  - Icon: `R.drawable.ic_rewind_10` hoặc `Icons.Default.Replay10`
  - Label: "Rewind" hoặc "Rewind 10s"
  - Action: `ACTION_REWIND_10`
  - **⚠️ THỐNG NHẤT:** Cùng icon style với Option 1 (nếu có)

- **Play/Pause:**
  - Icon: `R.drawable.ic_play` / `R.drawable.ic_pause` hoặc Material Icons
  - Label: "Play" / "Pause"
  - Action: `ACTION_TOGGLE_PLAY`
  - **⚠️ THỐNG NHẤT:** Cùng icon (`Icons.Default.PlayArrow` / `Icons.Default.Pause`) với Option 1

- **Forward 10s:**
  - Icon: `R.drawable.ic_forward_10` hoặc `Icons.Default.Forward10`
  - Label: "Forward" hoặc "Forward 10s"
  - Action: `ACTION_FORWARD_10`
  - **⚠️ THỐNG NHẤT:** Cùng icon style với Option 1 (nếu có)

**Layout:** `[Rewind] [Play/Pause] [Forward]` - Horizontal, spacing đều

#### 6. MediaStyle Configuration
```kotlin
.setStyle(
    androidx.media.app.NotificationCompat.MediaStyle()
        .setMediaSession(mediaSession.sessionToken)
        .setShowActionsInCompactView(1) // Chỉ hiện Play/Pause ở compact view
)
```

#### 7. MediaSession
- **Session Token:** Từ `MediaSessionCompat`
- **Playback State:**
  - Actions: `ACTION_PLAY | ACTION_PAUSE | ACTION_PLAY_PAUSE | ACTION_SEEK_TO`
  - State: `STATE_PLAYING` / `STATE_PAUSED`
  - Position: Current position in ms
  - Speed: `1.0f`

#### 8. Lock Screen
- **Large Icon:**
  - Resource: `R.drawable.ic_lockscreen_player` (64dp × 64dp)
  - Style: Circle với waveform/mic icon màu cam
  - Background: Cam nhạt (#FFE5D9)

- **Auto-generated từ MediaSession:**
  - Android tự render từ `MediaStyle` + `MediaSession`
  - Chỉ cần đảm bảo `MediaSession` được update đúng

### 📱 States

#### Playing State
```
🎙 Smart Recorder
Thoại 019
Jockerfie is my favourite...
00:26 ────────────●────────── 03:31
[⏮]  [⏸]  [⏭]
```

#### Paused State
```
🎙 Smart Recorder
Thoại 019
Jockerfie is my favourite...
00:26 ────────────●────────── 03:31
[⏮]  [▶]  [⏭]
```

### ✅ Ưu Điểm
- ✅ Điều khiển được khi app ở background
- ✅ Hiện trên lock screen (tiện lợi)
- ✅ Tuân thủ Android MediaStyle standard
- ✅ Tự động sync với system media controls

### ⚠️ Lưu Ý Implementation
- Cần `MediaSessionCompat` từ `androidx.media:media:1.6.0`
- Service phải là `foregroundServiceType="mediaPlayback"`
- Cần permission `POST_NOTIFICATIONS` (Android 13+)
- Update `MediaSession` mỗi khi position/state thay đổi
- Large icon nên là vector drawable để scale tốt

---

## 📊 So Sánh Options

| Tiêu chí | Option 1 (Card) | Option 3 (Notification) |
|----------|----------------|-------------------------|
| **Vị trí** | Trong app (TranscriptScreen) | Ngoài app (Notification/Lock) |
| **Mục đích** | UI trong màn hình | Điều khiển background |
| **Thiết kế** | Card Material 3 | System MediaStyle |
| **Tương tác** | Tap, drag slider | Tap actions, system controls |
| **Implementation** | Compose UI | Service + Notification |
| **Phức tạp** | ⭐⭐ (dễ) | ⭐⭐⭐ (trung bình) |
| **Bắt buộc** | ✅ Recommended | ✅ Bắt buộc cho background |
| **Màu sắc** | ✅ #FF6B35 (cam) | ✅ #FF6B35 (cam) - **THỐNG NHẤT** |
| **Icons** | ✅ Material Icons | ✅ Material Icons - **THỐNG NHẤT** |
| **Time format** | ✅ MM:SS | ✅ MM:SS - **THỐNG NHẤT** |
| **Progress style** | ✅ 4dp, cam | ✅ 4dp, cam - **THỐNG NHẤT** |
| **Actions** | ⚠️ Play/Pause (có thể thêm Rewind/Forward) | ✅ Rewind, Play/Pause, Forward |

### 🎯 Kết Luận

- **Option 1:** Dùng cho UI trong app → **Nên implement ngay**
- **Option 3:** Dùng cho notification/lock screen → **Bắt buộc nếu muốn background playback**

**→ Nên implement CẢ HAI** để có trải nghiệm tốt nhất!

### ✅ Đảm Bảo Thống Nhất

Khi implement, đảm bảo:
1. ✅ Cùng màu cam `#FF6B35` cho tất cả accent colors
2. ✅ Cùng Material Icons cho Play/Pause/Rewind/Forward
3. ✅ Cùng format time `MM:SS`
4. ✅ Cùng progress bar style (4dp, cam)
5. ✅ Cùng layout pattern (Button trái, Progress giữa, Time dưới)

---

## 🔧 Implementation Checklist

### ✅ Consistency Checklist (BẮT BUỘC)

Trước khi implement, đảm bảo:

- [ ] **Colors:** Đã define `#FF6B35` trong `colors.xml` và `Color.kt`
- [ ] **Icons:** Đã import Material Icons hoặc tạo custom icons cùng style
- [ ] **Time Format:** Đã tạo helper function `formatTime(ms: Long): String` → `MM:SS`
- [ ] **Design Tokens:** Đã tạo constants file hoặc object chứa tất cả values

### Option 1: Card Player

- [ ] Tạo file `TranscriptPlayerBar.kt`
- [ ] **Colors:** Dùng `MaterialTheme.colorScheme.primary` (#FF6B35)
- [ ] Implement Card với gradient background (cam nhạt #FFE5D9)
- [ ] **Icons:** Dùng `Icons.Default.PlayArrow` / `Icons.Default.Pause`
- [ ] Implement Play/Pause button (tròn 40dp, nền cam, icon trắng)
- [ ] **Progress:** Slider 4dp, active cam, inactive cam alpha 0.2
- [ ] **Time:** Dùng helper `formatTime()` → `MM:SS` format
- [ ] Implement time labels (left: current, right: duration)
- [ ] (Optional) Thêm Rewind/Forward buttons để thống nhất với Option 3
- [ ] Integrate vào `TranscriptScreen.kt`
- [ ] Connect với ViewModel state
- [ ] Test seek functionality
- [ ] Test play/pause toggle
- [ ] **Verify:** Màu cam, icons, format time đều đúng

### Option 3: Notification & Lock Screen

- [ ] Update `PlaybackForegroundService.kt`
- [ ] Tạo `MediaSessionCompat`
- [ ] **Colors:** Set `.setColor(ContextCompat.getColor(context, R.color.primary_orange))` → #FF6B35
- [ ] **Icons:** Dùng cùng Material Icons hoặc custom icons cùng style với Option 1
- [ ] Implement `buildPlaybackNotification()` với MediaStyle
- [ ] Add actions: Rewind, Play/Pause, Forward (đầy đủ 3 buttons)
- [ ] **Time:** Dùng cùng helper `formatTime()` → `MM:SS` format
- [ ] Add progress bar với time labels (left: current, right: duration)
- [ ] Add preview text (transcript snippet)
- [ ] Create large icon cho lock screen (64dp, cam background)
- [ ] Update `MediaSession` state khi playback thay đổi
- [ ] Test notification khi app ở background
- [ ] Test lock screen controls
- [ ] Verify permission `POST_NOTIFICATIONS`
- [ ] **Verify:** Màu cam, icons, format time đều đúng và giống Option 1

### 🔍 Final Verification

Sau khi implement cả 2 options:

- [ ] **Visual Test:** So sánh Option 1 và Option 3 → Màu cam có giống nhau không?
- [ ] **Icon Test:** Icons có cùng style, cùng weight không?
- [ ] **Time Test:** Format time có giống nhau không? (MM:SS)
- [ ] **Progress Test:** Progress bar có cùng màu cam không?
- [ ] **Layout Test:** Pattern có giống nhau không? (Button trái, Progress giữa, Time dưới)

---

## 🎨 Unified Design Tokens

### 🎨 Colors (BẮT BUỘC dùng chung)

```kotlin
// PRIMARY - Dùng cho TẤT CẢ media controls
val PrimaryOrange = Color(0xFFFF6B35)      // #FF6B35 - Vibrant orange
val OnPrimary = Color(0xFFFFFFFF)          // White - Text/icons trên nền cam

// SURFACE - Chỉ dùng trong app (Option 1)
val SurfaceVariant = Color(0xFFFFE5D9)     // Light orange - Card background
val OnSurfaceVariant = Color(0xFF475569)    // Dark gray - Text trên nền nhạt

// PROGRESS BAR - Dùng chung cho cả 2 options
val ProgressActive = PrimaryOrange         // #FF6B35 - Phần đã phát
val ProgressInactive = PrimaryOrange.copy(alpha = 0.2f)  // Cam nhạt - Phần chưa phát

// GRADIENTS - Chỉ dùng trong app (Option 1)
val GradientStart = PrimaryOrange.copy(alpha = 0.10f)
val GradientEnd = PrimaryOrange.copy(alpha = 0.05f)
```

**⚠️ Lưu ý:** 
- Option 1 (In-app): Dùng đầy đủ colors trên
- Option 3 (Notification): Chỉ dùng `PrimaryOrange` cho accent, system sẽ tự render background

### 📏 Dimensions

#### Option 1 (In-App) - Card Player
```kotlin
// Card Container
val CardPadding = 16.dp
val CardCornerRadius = 24.dp
val InnerCornerRadius = 20.dp
val CardElevation = 2.dp

// Play/Pause Button
val PlayButtonSize = 40.dp              // Tròn, lớn, dễ tap
val PlayButtonIconSize = 24.dp

// Progress Slider
val SliderTrackHeight = 4.dp             // Mảnh, hiện đại
val SliderThumbSize = 10.dp              // Circle thumb

// Spacing
val HorizontalPadding = 16.dp
val VerticalPadding = 8.dp
val ButtonSpacing = 16.dp
```

#### Option 3 (Notification) - System Defaults
```kotlin
// Notification uses system defaults, but ensure:
// - Icon size: 24dp (system standard)
// - Progress bar: System default (~4dp)
// - Button spacing: System default (compact)
```

### 🔤 Typography

```kotlin
// Time Labels - Dùng chung cho cả 2 options
val TimeLabelStyle = MaterialTheme.typography.labelSmall
val TimeLabelColor = OnSurfaceVariant  // #475569 - Dark gray

// Title (chỉ Option 1)
val TitleStyle = MaterialTheme.typography.labelMedium
val TitleColor = OnSurfaceVariant

// Format: MM:SS (ví dụ: 00:26, 03:31)
// Không dùng H:MM:SS trừ khi duration > 1 giờ
```

### 🎯 Icon Specifications

```kotlin
// TẤT CẢ icons phải cùng style Material Icons
val PlayIcon = Icons.Default.PlayArrow
val PauseIcon = Icons.Default.Pause
val RewindIcon = Icons.Default.Replay10      // Hoặc custom ic_rewind_10
val ForwardIcon = Icons.Default.Forward10    // Hoặc custom ic_forward_10

// Icon colors:
// - Trên nền cam: White (#FFFFFF)
// - Trên nền trắng: Primary Orange (#FF6B35)
```

### 📐 Layout Pattern (Unified)

```
┌─────────────────────────────────────────┐
│  [Button]  [Progress Bar]  [Time]      │
│     ↓           ↓            ↓           │
│   Play/Pause  ────●─────   00:26/03:31  │
└─────────────────────────────────────────┘
```

**Pattern Rules:**
1. Button (Play/Pause) luôn ở **bên trái**
2. Progress bar ở **giữa**, chiếm hết không gian còn lại
3. Time labels ở **dưới progress bar**, left/right alignment
4. Optional: Rewind/Forward buttons có thể thêm vào Option 1

---

## 📝 Code Structure

### File Organization
```
app/src/main/java/com/yourname/smartrecorder/
├── ui/
│   ├── screens/
│   │   └── TranscriptScreen.kt          (sửa: thay PlayerBar cũ)
│   └── player/
│       └── TranscriptPlayerBar.kt       (mới: Option 1)
├── core/
│   ├── service/
│   │   └── PlaybackForegroundService.kt (sửa: Option 3)
│   └── utils/
│       └── TimeFormatter.kt              (mới: Shared helper)
└── ui/
    └── theme/
        └── PlayerColors.kt               (mới: Shared color constants)
```

### 🔄 Shared Code/Helpers (Tái Sử Dụng)

Để đảm bảo thống nhất, tạo shared helpers:

#### 1. TimeFormatter.kt (Shared)
```kotlin
package com.yourname.smartrecorder.core.utils

object TimeFormatter {
    /**
     * Format milliseconds to MM:SS or H:MM:SS
     * THỐNG NHẤT cho cả Option 1 và Option 3
     */
    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
```

**Usage:**
- Option 1: `Text(TimeFormatter.formatTime(positionMs))`
- Option 3: `TimeFormatter.formatTime(position)` trong notification

#### 2. PlayerColors.kt (Shared Constants)
```kotlin
package com.yourname.smartrecorder.ui.theme

import androidx.compose.ui.graphics.Color

object PlayerColors {
    // PRIMARY - Dùng cho TẤT CẢ media controls
    val PrimaryOrange = Color(0xFFFF6B35)      // #FF6B35
    
    // PROGRESS BAR - Dùng chung
    val ProgressActive = PrimaryOrange
    val ProgressInactive = PrimaryOrange.copy(alpha = 0.2f)
    
    // SURFACE - Chỉ Option 1
    val CardBackground = Color(0xFFFFE5D9)     // Light orange
    val CardGradientStart = PrimaryOrange.copy(alpha = 0.10f)
    val CardGradientEnd = PrimaryOrange.copy(alpha = 0.05f)
}
```

**Usage:**
- Option 1: `PlayerColors.PrimaryOrange` trong Compose
- Option 3: `ContextCompat.getColor(context, R.color.primary_orange)` trong Service

#### 3. PlayerIcons.kt (Shared Icon References)
```kotlin
package com.yourname.smartrecorder.ui.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

object PlayerIcons {
    // THỐNG NHẤT cho cả Option 1 và Option 3
    val Play = Icons.Default.PlayArrow
    val Pause = Icons.Default.Pause
    val Rewind = Icons.Default.Replay10      // Hoặc custom
    val Forward = Icons.Default.Forward10     // Hoặc custom
}
```

**Usage:**
- Option 1: `Icon(PlayerIcons.Play, ...)`
- Option 3: Dùng cùng resource IDs hoặc Material Icons

### Dependencies (cần thêm)
```gradle
// build.gradle.kts (app level)
dependencies {
    // MediaSession for notification
    implementation("androidx.media:media:1.6.0")
    
    // Already have these:
    // implementation("androidx.core:core-ktx:1.12.0")
    // implementation("androidx.compose.material3:material3:...")
}
```

---

## 🚀 Next Steps

1. **Review design này với team** → Chọn Option 1 hoặc cả 2
2. **Tạo tasks trong project management tool** (Jira, Trello, etc.)
3. **Assign developer** cho từng task
4. **Implement Option 1 trước** (dễ hơn, impact lớn)
5. **Sau đó implement Option 3** (cần test kỹ notification)
6. **Test end-to-end** trên device thật
7. **Update documentation** sau khi hoàn thành

---

## 📸 Mockups (Text-based)

### Option 1 - Full Screen View
```
┌─────────────────────────────────────────┐
│ ← Thoại 019        🔍 🔖 ↗ 🗑          │
├─────────────────────────────────────────┤
│                                         │
│  ╔═══════════════════════════════════╗ │
│  ║  ┌──┐  ────────────●────────────  ║ │
│  ║  │▶ │  00:26              03:31  ║ │
│  ║  └──┘                             ║ │
│  ╚═══════════════════════════════════╝ │
│                                         │
│  [TRANSCRIPT] [NOTES] [SUMMARY]         │
│  ─────────────────────────────────────  │
│                                         │
│  Speaker 1: Jockerfie is my favourite   │
│  subject because it combines...         │
│                                         │
│  [Highlighted]                          │
│                                         │
│  Speaker 2: Yes, I completely agree...  │
│                                         │
└─────────────────────────────────────────┘
```

### Option 3 - Notification Expanded
```
┌─────────────────────────────────────────┐
│ 🎙 Smart Recorder                       │
├─────────────────────────────────────────┤
│ Thoại 019                               │
│                                         │
│ Jockerfie is my favourite subject       │
│ because it combines mathematics and...  │
│                                         │
│ 00:26 ────────────●────────── 03:31     │
│                                         │
│ ┌────────┐ ┌────────┐ ┌────────┐      │
│ │ ⏮ -10s │ │ ⏯ Play │ │ ⏭ +10s │      │
│ └────────┘ └────────┘ └────────┘      │
└─────────────────────────────────────────┘
```

### Option 3 - Lock Screen
```
┌─────────────────────────────────────────┐
│                                         │
│              ┌─────────┐                │
│              │    🎙    │                │
│              │  (Cam)   │                │
│              └─────────┘                │
│                                         │
│            Thoại 019                    │
│                                         │
│    Jockerfie is my favourite...         │
│                                         │
│    00:26 ────────●──────── 03:31        │
│                                         │
│         ┌──┐    ┌──┐    ┌──┐           │
│         │⏮│    │⏯│    │⏭│           │
│         └──┘    └──┘    └──┘           │
│                                         │
└─────────────────────────────────────────┘
```

---

## ✅ Final Notes

- **Option 1** là UI chính trong app → Ưu tiên implement trước
- **Option 3** là bắt buộc cho background playback → Implement sau
- Cả 2 options đều dùng màu cam (#FF6B35) để đồng bộ brand
- Test kỹ trên Android 8+ (notification channel) và Android 13+ (permission)

**Chúc team implement thành công! 🚀**

