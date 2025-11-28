# 🎨 Notification & Lock Screen UI Design - Smart Recorder

## 📋 Tổng Quan

Tài liệu này mô tả chi tiết thiết kế UI cho **Notification** và **Lock Screen** player, đồng bộ với UI trong app (RecordingPlayerBar).

**Màu sắc chính:**
- Primary Orange: `#FF6B35` (vibrant orange)
- Background: System notification background (trắng/xám)
- Icon: Trắng trên nền cam, hoặc cam trên nền trắng

---

## 🎯 UI Hiện Tại vs UI Mới

### ✅ UI Đã Implement (2025-01-XX)

**Đã hoàn thành:**
- ✅ MediaSessionCompat setup trong PlaybackForegroundService
- ✅ MediaStyle notification với compact và expanded views
- ✅ Compact view: Title + Play/Pause + Stop buttons (dạng phẳng, đơn giản)
- ✅ Expanded view: Progress bar (tự động từ MediaSession) + time labels trong contentText
- ✅ Lock screen controls tự động enable qua MediaSession

**Chưa implement (có thể thêm sau):**
- ⏸️ Custom icons (hiện dùng system icons)
- ⏸️ Rewind/Forward actions (chỉ có Play/Pause + Stop)

### ❌ UI Hiện Tại (Trước khi implement)

```
┌─────────────────────────────────────────────────────────┐
│  📱 Notification Panel (Hiện tại)                      │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │  [▶] Audio Playback                              │  │
│  │  Playing - 00:26 / 03:31                        │  │
│  │  [⏸] Pause  [✕] Stop                            │  │
│  └──────────────────────────────────────────────────┘  │
│                                                          │
│  Vấn đề:                                                │
│  • Dùng system icon (android.R.drawable)               │
│  • Layout đơn giản, không có progress bar              │
│  • Không có MediaStyle → không hiện trên lock screen   │
│  • Màu sắc không thống nhất với app                    │
└─────────────────────────────────────────────────────────┘
```

### ✅ UI Mới (Đã Implement - Đơn giản)

```
═══════════════════════════════════════════════════════════════════════
📱 NOTIFICATION PANEL (Compact View - Khi thu gọn) ✅ IMPLEMENTED
═══════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────┐
│  Smart Recorder                                                    │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │  Recording Title...                                          │  │
│  │  [⏸] Pause  [⏹] Stop                                       │  │
│  └─────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘

Chi tiết (Đã implement):
• Title: Recording title (truncate nếu dài)
• Actions: Play/Pause (action 0) + Stop (action 1) - dạng phẳng, đơn giản
• MediaStyle: setShowActionsInCompactView(0, 1) - chỉ hiện 2 actions trong compact view


═══════════════════════════════════════════════════════════════════════
📱 NOTIFICATION PANEL (Expanded View) ✅ IMPLEMENTED
═══════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────┐
│  Smart Recorder                                                    │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │  Recording Title (có thể dài...)                            │  │
│  │  00:26 / 03:31                                              │  │
│  │                                                              │  │
│  │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │  │
│  │  ──────────●───────────────────────────────────────────  │  │
│  │            ↑ Progress bar (tự động từ MediaSession)      │  │
│  │                                                              │  │
│  │  [⏸] Pause  [⏹] Stop                                      │  │
│  │                                                              │  │
│  └─────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘

Chi tiết (Đã implement):
• Title: Recording title (truncate nếu dài)
• ContentText: Time format "00:26 / 03:31"
• Progress bar: Tự động từ MediaSession playback state (có thể seek)
• Actions: Play/Pause + Stop (dạng phẳng, đơn giản)
• Background: System notification background
• MediaStyle: setMediaSession(sessionToken) - enable lock screen controls


═══════════════════════════════════════════════════════════════════════
🔒 LOCK SCREEN (Media Controls) ✅ IMPLEMENTED
═══════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────┐
│  🔒 Lock Screen                                                    │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │  Smart Recorder                                              │  │
│  │  Recording Title (có thể dài...)                             │  │
│  │                                                              │  │
│  │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │  │
│  │  ──────────●───────────────────────────────────────────  │  │
│  │            ↑ Progress bar (tự động từ MediaSession)      │  │
│  │                                                              │  │
│  │  [⏸] Pause  [⏹] Stop                                      │  │
│  │                                                              │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘

Chi tiết (Đã implement):
• Title: Recording title (từ MediaSession metadata)
• Progress bar: Tự động từ MediaSession playback state (có thể seek)
• Actions: Play/Pause + Stop (từ MediaSession actions)
• Background: System lock screen background (tối/trong suốt)
• MediaStyle: setMediaSession(sessionToken) - tự động enable lock screen controls
• MediaSession: Update metadata và playback state real-time


═══════════════════════════════════════════════════════════════════════
📊 SO SÁNH: UI Trong App vs Notification vs Lock Screen
═══════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────┐
│  Trong App (RecordingPlayerBar)                                    │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │  [Pill-shaped card - gradient cam nhạt]                    │  │
│  │                                                             │  │
│  │  ┌──────┐                                                   │  │
│  │  │  ⏸  │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │  │
│  │  │      │  ──────────●─────────────────────────────────  │  │
│  │  └──────┘                                                   │  │
│  │   20dp (compact) hoặc 40dp (normal)                        │  │
│  │                                                             │  │
│  │  00:26 / 03:31                                            │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                      │
├─────────────────────────────────────────────────────────────────────┤
│  Notification (Expanded)                                           │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │  [System notification background]                          │  │
│  │                                                             │  │
│  │  ┌──────┐                                                   │  │
│  │  │  ⏸  │  Recording Title...  00:26 / 03:31              │  │
│  │  │      │                                                   │  │
│  │  └──────┘                                                   │  │
│  │   20dp                                                      │  │
│  │                                                             │  │
│  │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │  │
│  │  ──────────●───────────────────────────────────────────  │  │
│  │                                                             │  │
│  │  [⏪] [⏸] [⏩] [⏹]                                        │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                      │
├─────────────────────────────────────────────────────────────────────┤
│  Lock Screen                                                       │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │  [System lock screen background - tối/trong suốt]          │  │
│  │                                                             │  │
│  │  Smart Recorder                                             │  │
│  │  Recording Title...                                         │  │
│  │                                                             │  │
│  │  ┌──────┐  [Large Icon 64dp]                               │  │
│  │  │  ⏸  │                                                   │  │
│  │  └──────┘                                                   │  │
│  │                                                             │  │
│  │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │  │
│  │  ──────────●───────────────────────────────────────────  │  │
│  │                                                             │  │
│  │  00:26 / 03:31                                            │  │
│  │                                                             │  │
│  │  [⏪] [⏸] [⏩] [⏹]                                        │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘

Điểm chung:
✅ Màu cam #FF6B35 cho progress bar và button
✅ Icon trắng trên nền cam
✅ Time format "00:26 / 03:31"
✅ Progress bar có thể seek
✅ Actions: Rewind, Play/Pause, Forward, Stop

Khác nhau:
• Background: App có gradient, Notification/Lock Screen dùng system background
• Layout: App có pill shape, Notification/Lock Screen dùng system layout
• Button size: App 20dp/40dp, Notification/Lock Screen system default


═══════════════════════════════════════════════════════════════════════
🔧 IMPLEMENTATION PLAN
═══════════════════════════════════════════════════════════════════════

1. **MediaSession Setup**
   - Tạo MediaSessionCompat trong PlaybackForegroundService
   - Set metadata (title, artist, duration)
   - Set playback state (playing/paused, position)
   - Set actions (play, pause, stop, rewind, forward)

2. **MediaStyle Notification**
   - Dùng NotificationCompat.MediaStyle()
   - Set mediaSession token
   - Set showActionsInCompactView(0, 1, 2) - Play/Pause, Forward, Stop
   - Set large icon (custom hoặc placeholder)

3. **Custom Icons**
   - Tạo custom icons cho Rewind/Forward (nếu cần)
   - Hoặc dùng Material Icons: Replay10, Forward10
   - Icon màu cam #FF6B35 hoặc trắng

4. **Progress Bar**
   - Dùng MediaStyle's built-in progress bar
   - Hoặc custom RemoteViews (phức tạp hơn)
   - Màu cam #FF6B35

5. **Permissions**
   - ✅ FOREGROUND_SERVICE_MEDIA_PLAYBACK (đã có)
   - ✅ POST_NOTIFICATIONS (đã có)
   - ✅ Notification channel lockscreenVisibility = VISIBILITY_PUBLIC (đã có)
   - ❓ Có thể cần thêm quyền cho lock screen? → KHÔNG, MediaStyle tự động handle


═══════════════════════════════════════════════════════════════════════
📋 CHECKLIST: Quyền và Cấu hình
═══════════════════════════════════════════════════════════════════════

✅ Đã có trong Manifest:
- FOREGROUND_SERVICE_MEDIA_PLAYBACK
- POST_NOTIFICATIONS

✅ Đã có trong Notification Channel:
- lockscreenVisibility = VISIBILITY_PUBLIC

❌ Cần thêm:
- MediaSessionCompat setup
- MediaStyle notification
- Custom icons (nếu muốn)
- Rewind/Forward actions

💡 Lưu ý:
- KHÔNG cần thêm quyền đặc biệt cho lock screen
- MediaStyle + MediaSession tự động enable lock screen controls
- Chỉ cần đảm bảo notification channel có VISIBILITY_PUBLIC


═══════════════════════════════════════════════════════════════════════
🎨 COLOR & STYLING SPECIFICATIONS
═══════════════════════════════════════════════════════════════════════

1. **Progress Bar**
   - Active color: #FF6B35 (primary orange)
   - Inactive color: #FF6B35 với alpha 0.25
   - Height: 4dp
   - Thumb: Circle, 12dp, màu cam

2. **Button Icons**
   - Play/Pause: Trắng trên nền cam (trong notification)
   - Rewind/Forward: Cam #FF6B35 hoặc system default
   - Stop: System default hoặc cam

3. **Text**
   - Title: System default (đen trên nền trắng)
   - Time: System default hoặc xám đậm
   - Font: System default

4. **Background**
   - Notification: System notification background (trắng/xám)
   - Lock Screen: System lock screen background (tối/trong suốt)


═══════════════════════════════════════════════════════════════════════
📝 CODE STRUCTURE (Dự kiến)
═══════════════════════════════════════════════════════════════════════

```kotlin
// 1. MediaSession Setup
private var mediaSession: MediaSessionCompat? = null

override fun onCreate() {
    mediaSession = MediaSessionCompat(this, "PlaybackService").apply {
        isActive = true
        setCallback(mediaSessionCallback)
    }
}

// 2. Update Metadata
private fun updateMediaSessionMetadata(title: String, duration: Long) {
    mediaSession?.setMetadata(
        MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Smart Recorder")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .build()
    )
}

// 3. Update Playback State
private fun updatePlaybackState(isPlaying: Boolean, position: Long) {
    val state = if (isPlaying) {
        PlaybackStateCompat.STATE_PLAYING
    } else {
        PlaybackStateCompat.STATE_PAUSED
    }
    
    mediaSession?.setPlaybackState(
        PlaybackStateCompat.Builder()
            .setState(state, position, 1.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_REWIND or
                PlaybackStateCompat.ACTION_FAST_FORWARD
            )
            .build()
    )
}

// 4. MediaStyle Notification
private fun createNotification(...): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(title)
        .setContentText("$positionText / $durationText")
        .setSmallIcon(R.drawable.ic_play) // Custom icon
        .setLargeIcon(getLargeIcon()) // 64dp × 64dp
        .setStyle(
            NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2) // Play/Pause, Forward, Stop
                .setMediaSession(mediaSession!!.sessionToken)
        )
        .addAction(rewindAction)
        .addAction(playPauseAction)
        .addAction(forwardAction)
        .addAction(stopAction)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()
}
```

---

## 🎯 Kết Luận

**UI đã implement (2025-01-XX):**
- ✅ MediaSessionCompat - Setup trong PlaybackForegroundService
- ✅ MediaStyle notification - Compact và expanded views
- ✅ Compact view: Title + Play/Pause + Stop buttons (dạng phẳng, đơn giản)
- ✅ Expanded view: Progress bar (tự động từ MediaSession) + time labels trong contentText
- ✅ Lock screen controls - Tự động enable qua MediaSession
- ✅ Progress bar có thể seek - Từ MediaSession playback state
- ⏸️ Custom icons - Dùng system icons (có thể thêm custom sau)
- ⏸️ Rewind/Forward actions - Chưa thêm (chỉ có Play/Pause + Stop - đơn giản như yêu cầu)

**Không cần thêm quyền:**
- ✅ FOREGROUND_SERVICE_MEDIA_PLAYBACK (đã có)
- ✅ POST_NOTIFICATIONS (đã có)
- ✅ VISIBILITY_PUBLIC (đã có trong channel)

**Implementation Status:**
- ✅ **COMPLETED** - MediaSession và MediaStyle notification đã được implement
- ✅ **COMPLETED** - Compact view với Play/Pause + Stop
- ✅ **COMPLETED** - Expanded view với progress bar và time labels
- ✅ **COMPLETED** - Lock screen controls tự động enable

