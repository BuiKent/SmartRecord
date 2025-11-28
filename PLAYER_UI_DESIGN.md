# 🎨 Player UI Design - Smart Recorder

## 📋 Tổng Quan

Tài liệu này mô tả chi tiết thiết kế UI cho audio player trong app Smart Recorder, với **3 options** rõ ràng để team có thể chọn và implement.

**Màu sắc chính:**
- Primary Orange: `#FF6B35` (vibrant orange)
- Surface Variant: `#FFE5D9` (light orange background)
- On Primary: `#FFFFFF` (white text/icons)

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

#### 3. Play/Pause Button
- **Kích thước:**
  - Size: `40.dp × 40.dp` (tròn)
  - Icon size: `24.dp`

- **Styling:**
  - Background: `MaterialTheme.colorScheme.primary` (#FF6B35 - cam đậm)
  - Shape: `CircleShape`
  - Icon color: `Color.White`
  - Icon: `Icons.Default.PlayArrow` / `Icons.Default.Pause`

- **Vị trí:** Bên trái, cách lề trái `16.dp`

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
  - Size: `24dp × 24dp`
  - Color: `#FF6B35` (primary orange)

- **App Name:** "Smart Recorder"
- **Color Accent:** `ContextCompat.getColor(context, R.color.primary_orange)` (#FF6B35)

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
  - Current: `00:26` (left)
  - Total: `03:31` (right)
  - Progress: `setProgress(max, current, false)`

- **Styling:**
  - Color: System accent (sẽ dùng màu cam từ `setColor()`)
  - Height: System default (~4dp)

#### 5. Actions
- **Rewind 10s:**
  - Icon: `R.drawable.ic_rewind_10`
  - Label: "Rewind"
  - Action: `ACTION_REWIND_10`

- **Play/Pause:**
  - Icon: `R.drawable.ic_play` / `R.drawable.ic_pause`
  - Label: "Play" / "Pause"
  - Action: `ACTION_TOGGLE_PLAY`

- **Forward 10s:**
  - Icon: `R.drawable.ic_forward_10`
  - Label: "Forward"
  - Action: `ACTION_FORWARD_10`

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

### 🎯 Kết Luận

- **Option 1:** Dùng cho UI trong app → **Nên implement ngay**
- **Option 3:** Dùng cho notification/lock screen → **Bắt buộc nếu muốn background playback**

**→ Nên implement CẢ HAI** để có trải nghiệm tốt nhất!

---

## 🔧 Implementation Checklist

### Option 1: Card Player

- [ ] Tạo file `TranscriptPlayerBar.kt`
- [ ] Implement Card với gradient background
- [ ] Implement Play/Pause button (tròn, cam)
- [ ] Implement Slider với styling cam
- [ ] Implement time labels (MM:SS format)
- [ ] Integrate vào `TranscriptScreen.kt`
- [ ] Connect với ViewModel state
- [ ] Test seek functionality
- [ ] Test play/pause toggle
- [ ] Verify responsive trên các screen sizes

### Option 3: Notification & Lock Screen

- [ ] Update `PlaybackForegroundService.kt`
- [ ] Tạo `MediaSessionCompat`
- [ ] Implement `buildPlaybackNotification()` với MediaStyle
- [ ] Add actions: Rewind, Play/Pause, Forward
- [ ] Add progress bar với time labels
- [ ] Add preview text (transcript snippet)
- [ ] Set notification color accent (#FF6B35)
- [ ] Create large icon cho lock screen
- [ ] Update `MediaSession` state khi playback thay đổi
- [ ] Test notification khi app ở background
- [ ] Test lock screen controls
- [ ] Verify permission `POST_NOTIFICATIONS`

---

## 🎨 Design Tokens

### Colors
```kotlin
// Primary
val PrimaryOrange = Color(0xFFFF6B35)      // #FF6B35
val OnPrimary = Color(0xFFFFFFFF)          // White

// Surface Variant (Card background)
val SurfaceVariant = Color(0xFFFFE5D9)     // Light orange
val OnSurfaceVariant = Color(0xFF475569)    // Dark gray

// Gradients
val GradientStart = PrimaryOrange.copy(alpha = 0.10f)
val GradientEnd = PrimaryOrange.copy(alpha = 0.05f)
```

### Dimensions
```kotlin
// Card
val CardPadding = 16.dp
val CardCornerRadius = 24.dp
val InnerCornerRadius = 20.dp
val CardElevation = 2.dp

// Button
val PlayButtonSize = 40.dp
val PlayButtonIconSize = 24.dp

// Slider
val SliderTrackHeight = 4.dp
val SliderThumbSize = 10.dp

// Spacing
val HorizontalPadding = 16.dp
val VerticalPadding = 8.dp
val ButtonSpacing = 16.dp
```

### Typography
```kotlin
// Title
MaterialTheme.typography.labelMedium

// Time labels
MaterialTheme.typography.labelSmall
```

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
└── core/
    └── service/
        └── PlaybackForegroundService.kt (sửa: Option 3)
```

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

