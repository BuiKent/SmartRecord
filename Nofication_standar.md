# Notification Permission Flow - Standard Implementation

Tài liệu này mô tả chi tiết cách ứng dụng xử lý notification permission từ onboarding đến settings screen, đồng bộ với hệ thống Android.

**Last Updated**: 2025-11-27  
**Status**: ✅ Đã implement đầy đủ theo standard

---

## 📋 Tổng Quan

Ứng dụng sử dụng **hệ thống Android làm single source of truth** cho notification permission:
- **NotificationManagerCompat.areNotificationsEnabled()** - Kiểm tra trạng thái notification (Google recommended)
- **ActivityResultContracts.RequestPermission()** - Request permission dialog (Android 13+)
- **Settings.ACTION_APP_NOTIFICATION_SETTINGS** - Mở system settings để tắt notification

---

## 🏗️ Kiến Trúc

### 1. Components Chính

```
┌─────────────────────────────────────────────────────────────┐
│ NotificationPermissionManager                                │
│ - areNotificationsEnabled(context) → Boolean                │
│ - openSystemSettings(context) → Unit                        │
└─────────────────────────────────────────────────────────────┘
                        ▲                    ▲
                        │                    │
        ┌───────────────┴────────┐           │
        │                         │           │
┌───────┴────────┐    ┌──────────┴───────────┴──────┐
│ Notification    │    │      SettingScreen          │
│ PermissionScreen│    │                             │
│ (Post-sign-in)  │    │ - Toggle ON/OFF             │
│ - Request       │    │ - Sync với system           │
│   permission    │    │ - Rationale BottomSheet     │
└────────────────┘    └─────────────────────────────┘
        │                         │
        ▼                         ▼
┌─────────────────────────────────────────────────────────────┐
│ SettingViewModel                                             │
│ - onNotificationToggleChanged()                              │
│ - refreshState()                                             │
│ - scheduleNotifications()                                    │
└─────────────────────────────────────────────────────────────┘
        │                         │
        ▼                         ▼
┌──────────────────┐    ┌─────────────────────────────────────┐
│ SettingsStore    │    │ NotificationScheduler                │
│ (DataStore)      │    │ - scheduleDailyNotifications()      │
│                  │    │ - cancelAllNotifications()           │
└──────────────────┘    └─────────────────────────────────────┘
```

---

## 🔄 Luồng Chi Tiết

### A. NOTIFICATION PERMISSION SCREEN - Request Permission (Post-sign-in)

#### 1. NotificationPermissionScreen.kt

**Location:** `app/src/main/java/com/marketsnap/app/ui/screens/NotificationPermissionScreen.kt`

**Flow:**

```kotlin
// Notification permission state (sử dụng Accompanist Permissions)
val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.POST_NOTIFICATIONS)
    )
} else {
    null // Android < 13: No runtime permission needed
}

val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    notificationPermission?.allPermissionsGranted == true
} else {
    true // Android < 13: Permission granted by default
}

// ✅ Auto-navigate khi permission granted
LaunchedEffect(notificationPermission?.allPermissionsGranted) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (notificationPermission?.allPermissionsGranted == true) {
            delay(300) // Allow permission state to update
            onContinue() // Auto-navigate
        }
    }
}

// Request permission
Button(onClick = {
    notificationPermission?.launchMultiplePermissionRequest()
}) {
    Text("Cho phép thông báo")
}
```

**Chi tiết:**
1. **Kiểm tra permission:**
   - Android 13+ (TIRAMISU): Sử dụng `rememberMultiplePermissionsState` từ Accompanist
   - Android < 13: Mặc định `true` (không cần permission)

2. **Request permission:**
   - User click "Cho phép thông báo"
   - Gọi `notificationPermission?.launchMultiplePermissionRequest()`
   - Permission dialog hiển thị

3. **Auto-navigate khi granted:**
   - ✅ **Tốt hơn standard**: Tự động navigate khi permission granted (LaunchedEffect)
   - Delay 300ms để đảm bảo permission state đã được update
   - Không cần lưu vào DataStore ở đây (sẽ được lưu khi schedule notifications)

**Lưu ý:**
- Screen này là post-sign-in onboarding, không phải trong pager
- User có thể skip nếu không muốn cấp permission
- Auto-navigate giúp UX mượt mà hơn

---

### B. SETTINGS SCREEN - Toggle Notification

#### 1. SettingsScreen.kt

**Location:** `app/src/main/java/com/marketsnap/app/ui/screens/SettingsScreen.kt`

**Flow chính:**

##### a. Initialize State khi mở Settings

```kotlin
// ✅ Initialize state synchronously to avoid UI flash
DisposableEffect(Unit) {
    viewModel.initializeState(context)
    onDispose { }
}

// ✅ Refresh state when Settings screen opens (async)
LaunchedEffect(Unit) {
    viewModel.refreshState(context)
}

// ✅ Refresh state when app resumes (user returns from system settings)
LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        delay(120) // Stabilize delay
        viewModel.refreshState(context)
    }
}
```

**Chi tiết:**
1. **DisposableEffect(Unit):**
   - Gọi `vm.initializeState(context)` **đồng bộ** ngay khi screen compose
   - Đảm bảo toggle hiển thị đúng state ngay lập tức (không bị flash)

2. **LaunchedEffect(Unit):**
   - Gọi `vm.refreshState(context)` **bất đồng bộ** để refresh state
   - Chạy khi Settings screen mở lần đầu

3. **repeatOnLifecycle(RESUMED):**
   - Refresh state khi app resume (user quay lại từ system settings)
   - Delay 120ms để hệ thống cập nhật state (đặc biệt Samsung/Xiaomi)

##### b. Permission Launcher

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { isGranted ->
    coroutineScope.launch {
        delay(150) // Allow system to settle
        viewModel.refreshState(context)
        
        // ✅ Tốt hơn standard: Handle "Don't ask again"
        if (!isGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                context as Activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (!shouldShowRationale) {
                // User checked "Don't ask again" → Open system settings
                notificationPermissionManager.openSystemSettings(context)
            }
        }
    }
}
```

**Chi tiết:**
- Sau khi permission dialog đóng, delay 150ms rồi refresh state
- Xử lý delay của Samsung/Xiaomi khi cập nhật permission state

##### c. Event Handler

```kotlin
// ✅ Event handler
LaunchedEffect(Unit) {
    viewModel.eventFlow.collectLatest { event ->
        when (event) {
            is SettingsViewModel.SettingsEvent.RequestPermission -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Pre-13 treated as granted
                    viewModel.refreshState(context)
                    viewModel.scheduleNotifications()
                }
            }
            is SettingsViewModel.SettingsEvent.OpenSystemSettings -> {
                // ✅ Hiện BottomSheet trước khi mở system settings
                showDisableWarning = true
            }
        }
    }
}

// ✅ Gọi scheduleNotifications() sau khi permission granted
LaunchedEffect(systemNotificationAllowed) {
    if (systemNotificationAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        delay(200) // Allow permission state to update
        viewModel.scheduleNotifications()
    }
}
```

**Chi tiết:**
- **RequestNotificationPermission:** 
  - Android 13+: Mở permission dialog
  - Android < 13: Tự động schedule notifications (không cần permission)

- **OpenSystemSettings:**
  - Mở system settings để user tắt notification (vì permission dialog không thể tắt)

##### d. DisableNotificationWarningBottomSheet

```kotlin
// ✅ State cho BottomSheet
var showDisableWarning by remember { mutableStateOf(false) }

// ✅ Hiện BottomSheet khi toggle OFF
is SettingsEvent.OpenSystemSettings -> {
    showDisableWarning = true
}

// ✅ DisableNotificationWarningBottomSheet
DisableNotificationWarningBottomSheet(
    visible = showDisableWarning,
    onDismissRequest = { showDisableWarning = false },
    onConfirm = {
        showDisableWarning = false
        notificationPermissionManager.openSystemSettings(context)
    },
)
```

**Chi tiết:**
- ✅ **Rationale BottomSheet**: Hiện cảnh báo trước khi mở system settings
- User có thể hủy để giữ notification bật
- Chỉ mở system settings khi user confirm

##### e. Toggle Switch UI

```kotlin
// Toggle hiển thị state từ systemNotificationAllowed
Switch(
    checked = systemNotificationAllowed,
    onCheckedChange = { viewModel.onToggleClicked(it) }
)
```

**Chi tiết:**
- Toggle hiển thị state từ `systemNotificationAllowed` (system state)
- Khi user toggle → gọi `viewModel.onToggleClicked(it)`

---

#### 2. SettingsViewModel.kt

**Location:** `app/src/main/java/com/marketsnap/app/viewmodel/SettingsViewModel.kt`

##### a. State Management

```kotlin
// ✅ Notification state: Use system notification state as single source of truth
private val _systemNotificationAllowed = MutableStateFlow(false)
val systemNotificationAllowed: StateFlow<Boolean> = _systemNotificationAllowed.asStateFlow()

// Events for permission requests and system settings
sealed class SettingsEvent {
    object RequestPermission : SettingsEvent() // Toggle ON → Request permission
    object OpenSystemSettings : SettingsEvent() // Toggle OFF → Open system settings
}

private val _eventFlow = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
val eventFlow: SharedFlow<SettingsEvent> = _eventFlow.asSharedFlow()
```

**Chi tiết:**
- **`_systemNotificationAllowed`**: State từ hệ thống (NotificationManagerCompat)
- **Single source of truth**: Hệ thống Android (không phải DataStore)
- **Event flow**: Sử dụng SharedFlow với extraBufferCapacity để tránh mất event

##### b. Initialize State (Đồng bộ)

```kotlin
fun initializeState(context: Context) {
    _systemNotificationAllowed.value = notificationPermissionManager.areNotificationsEnabled(context)
    Timber.d("SettingsViewModel: initializeState() - ${_systemNotificationAllowed.value}")
}
```

**Chi tiết:**
- Gọi **đồng bộ** ngay khi Settings screen mở
- Đảm bảo toggle hiển thị đúng state ngay lập tức (không bị flash)

##### c. Refresh State (Bất đồng bộ)

```kotlin
fun refreshState(context: Context) {
    viewModelScope.launch {
        val first = notificationPermissionManager.areNotificationsEnabled(context)
        _systemNotificationAllowed.value = first
        Timber.d("SettingsViewModel: refreshState() - First check: $first")

        // ✅ Retry logic for OEM ROMs that lag behind permission change
        repeat(3) { attempt ->
            delay(180) // Tuned for device variety
            val now = notificationPermissionManager.areNotificationsEnabled(context)
            if (now != _systemNotificationAllowed.value) {
                Timber.d("SettingsViewModel: refreshState() - Retry $attempt detected change: $now")
                _systemNotificationAllowed.value = now
                return@launch
            }
        }
    }
}
```

**Chi tiết:**
1. Kiểm tra state từ hệ thống
2. Retry 3 lần nếu state chưa cập nhật (xử lý delay Samsung/Xiaomi)
3. Mỗi lần retry delay 180ms
4. Chỉ retry nếu state thay đổi

##### d. Handle Toggle Change

```kotlin
fun onToggleClicked(wantsEnable: Boolean) {
    viewModelScope.launch {
        val current = _systemNotificationAllowed.value
        Timber.d("SettingsViewModel: onToggleClicked() - wantsEnable=$wantsEnable, current=$current")
        
        if (wantsEnable && !current) {
            // ✅ Toggle ON → Request permission dialog
            _eventFlow.emit(SettingsEvent.RequestPermission)
        } else if (!wantsEnable && current) {
            // ✅ Toggle OFF → Cancel scheduled notifications + Update DataStore + Open system settings
            notificationScheduler.cancelAllScheduledNotifications()
            userPreferencesRepository.updateNotificationsEnabled(false)
            _eventFlow.emit(SettingsEvent.OpenSystemSettings)
        }
    }
}
```

**Chi tiết:**
- **Toggle ON:**
  - Nếu chưa có permission → emit `RequestPermission`
  - SettingsScreen sẽ mở permission dialog

- **Toggle OFF:**
  - ✅ Cancel tất cả scheduled notifications
  - ✅ Update DataStore: `notificationsEnabled = false`
  - Emit `OpenSystemSettings` → SettingsScreen sẽ hiện BottomSheet

##### e. Schedule Notifications (với Guard)

```kotlin
fun scheduleNotifications() {
    viewModelScope.launch {
        // ✅ Guard: Check state trước
        val currentState = _systemNotificationAllowed.value
        val storeState = userPreferencesRepository.notificationsEnabled.first()
        
        if (currentState && storeState) {
            // Already scheduled → Skip
            Timber.d("SettingsViewModel: Already scheduled, skipping")
            return@launch
        }
        
        // Update DataStore preference
        userPreferencesRepository.updateNotificationsEnabled(true)
        
        // Schedule notifications
        notificationScheduler.schedulePromotionNotification()
        notificationScheduler.scheduleTipsNotification()
        
        Timber.d("SettingsViewModel: Scheduled notifications")
    }
}
```

**Chi tiết:**
- ✅ **Guard**: Check state trước để tránh duplicate scheduling
- Lưu `notificationsEnabled = true` vào DataStore
- Schedule promotion và tips notifications
- Tránh tốn resources khi đã scheduled

**Lưu ý:**
- `openSystemSettings()` được gọi trực tiếp từ SettingsScreen thông qua `NotificationPermissionManager`
- Không cần method riêng trong ViewModel

---

#### 3. NotificationPermissionManager.kt

**Location:** `app/src/main/java/com/marketsnap/app/utils/NotificationPermissionManager.kt`

```kotlin
fun areNotificationsEnabled(context: Context): Boolean {
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * Navigate to system Settings app for notification settings
 */
fun openSystemSettings(context: Context) {
    Timber.d("NotificationPermissionManager: Opening system Settings app")
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
```

**Chi tiết:**
- **`areNotificationsEnabled()`**: 
  - Sử dụng `NotificationManagerCompat.areNotificationsEnabled()`
  - Google recommended way (đáng tin cậy hơn check permission)

- **`openSystemSettings()`**:
  - Android 8.0+: `ACTION_APP_NOTIFICATION_SETTINGS` (mở trực tiếp notification settings)
  - Android < 8.0: `ACTION_APPLICATION_DETAILS_SETTINGS` (mở app details)

---

#### 4. UserPreferencesRepository.kt

**Location:** `app/src/main/java/com/marketsnap/app/data/repository/UserPreferencesRepository.kt`

```kotlin
val notificationsEnabled: Flow<Boolean> =
    dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_ENABLED_KEY] ?: false // Default: false
    }

suspend fun updateNotificationsEnabled(enabled: Boolean) {
    Timber.d("UserPreferencesRepository: updateNotificationsEnabled($enabled)")
    dataStore.edit { preferences ->
        preferences[NOTIFICATIONS_ENABLED_KEY] = enabled
    }
}
```

**Chi tiết:**
- Lưu user preference vào DataStore
- **Lưu ý:** Đây chỉ là user preference, không phải source of truth
- Source of truth là `NotificationManagerCompat.areNotificationsEnabled()`
- Default: `false` (khác với standard là `true`)

---

## 🔄 Luồng Hoàn Chỉnh

### Scenario 1: Notification Permission Screen - Request Permission (Post-sign-in)

```
1. User sign in thành công
   ↓
2. Navigate đến NotificationPermissionScreen
   ↓
3. Screen hiển thị:
   - Icon + Title: "Nhận thông báo quan trọng"
   - Description: Lợi ích của notifications
   - Button: "Cho phép thông báo"
   - Button: "Bỏ qua" (optional)
   ↓
4. User click "Cho phép thông báo"
   ↓
5. Android 13+:
   → notificationPermission?.launchMultiplePermissionRequest()
   → Permission dialog hiển thị
   ↓
6. User chọn "Allow"
   ↓
7. LaunchedEffect phát hiện permission granted
   ↓
8. Delay 300ms → Auto-navigate (onContinue)
   ↓
   Android < 13:
   → Tự động navigate (không cần permission)
```

### Scenario 2: Settings - Toggle ON

```
1. User vào Settings screen
   ↓
2. DisposableEffect(Unit) → initializeState()
   → NotificationPermissionManager.areNotificationsEnabled()
   → _systemNotificationAllowed.value = true/false
   ↓
3. LaunchedEffect(Unit) → refreshState()
   → Kiểm tra lại state từ hệ thống (async)
   ↓
4. Toggle hiển thị state từ settings.notificationsEnabled
   (Combine từ systemNotificationAllowed)
   ↓
5. User toggle ON (chưa có permission)
   ↓
6. SettingViewModel.onNotificationToggleChanged(true)
   ↓
7. Emit SettingsEvent.RequestNotificationPermission
   ↓
8. SettingScreen nhận event
   ↓
9. Check permission đã granted chưa
   ↓
10. Case A: Đã granted
    → scheduleNotifications() ngay
   ↓
    Case B: Chưa granted
    → permissionLauncher.launch(POST_NOTIFICATIONS) trực tiếp
    → Permission dialog hiển thị
    → User chọn "Allow" hoặc "Don't allow"
    ↓
11. Callback: Delay 150ms → refreshState()
   ↓
12. LaunchedEffect(Unit) phát hiện permission granted
   → scheduleNotifications() (với guard)
   → SettingsStore.setNotificationsEnabled(true)
   → NotificationScheduler.scheduleDailyNotifications()
```

### Scenario 3: Settings - Toggle OFF (Hiện Rationale BottomSheet)

```
1. User vào Settings screen
   ↓
2. Toggle đang ở ON
   ↓
3. User toggle OFF
   ↓
4. SettingsViewModel.onToggleClicked(false)
   ↓
5. Cancel notifications:
   → notificationScheduler.cancelAllScheduledNotifications()
   → userPreferencesRepository.updateNotificationsEnabled(false)
   ↓
6. Emit SettingsEvent.OpenSystemSettings
   ↓
7. SettingsScreen nhận event
   ↓
8. ✅ Hiện DisableNotificationWarningBottomSheet (KHÔNG mở system settings ngay)
   ↓
9. BottomSheet hiển thị:
   - Title: "Tắt thông báo?"
   - Text: "Bạn sẽ không nhận được: • Nhắc nhở quét hóa đơn • Cập nhật giá sản phẩm • Thông báo khuyến mãi"
   - Button: "Mở Cài đặt thiết bị" và "Hủy"
   ↓
10. Case A: User click "Mở Cài đặt thiết bị"
    → notificationPermissionManager.openSystemSettings(context)
    → System Settings mở ra
    → User tắt notification trong system settings
    ↓
    Case B: User click "Hủy"
    → Đóng BottomSheet
    → Toggle vẫn ở ON (không thay đổi - system state chưa đổi)
    ↓
11. User quay lại app (nếu đã mở system settings)
   ↓
12. repeatOnLifecycle(RESUMED) trigger
   ↓
13. refreshState() sau delay 120ms
   ↓
14. NotificationPermissionManager.areNotificationsEnabled()
    → Trả về false
   ↓
15. _systemNotificationAllowed.value = false
   ↓
16. Toggle hiển thị OFF
```

### Scenario 4: Settings - Sync khi Resume

```
1. User đang ở Settings screen
   ↓
2. User mở system settings (từ bên ngoài app)
   ↓
3. User thay đổi notification permission
   ↓
4. User quay lại app
   ↓
5. Lifecycle: RESUMED
   ↓
6. repeatOnLifecycle(RESUMED) trigger
   ↓
7. Delay 120ms
   ↓
8. refreshState()
   ↓
9. NotificationPermissionManager.areNotificationsEnabled()
   ↓
10. Cập nhật _systemNotificationAllowed
   ↓
11. settings.notificationsEnabled tự động sync
   ↓
12. Toggle hiển thị đúng state
```

---

## 🔑 Điểm Quan Trọng

### 1. Single Source of Truth

- **Hệ thống Android** là single source of truth (NotificationManagerCompat)
- **DataStore** chỉ lưu user preference, không phải state thực tế

### 2. Đồng Bộ Hóa

- **initializeState()**: Đồng bộ, gọi ngay khi screen compose (tránh flash)
- **refreshState()**: Bất đồng bộ, gọi khi screen mở và khi resume

### 3. Xử Lý Delay

- Samsung/Xiaomi có delay khi cập nhật permission state
- Retry 3 lần, mỗi lần delay 180ms
- Delay 120ms khi app resume
- Delay 150ms sau permission dialog đóng

### 4. Permission Dialog vs System Settings

- **Permission dialog** (RequestPermission): Chỉ có thể GRANT permission
- **System settings**: Có thể GRANT hoặc DENY permission
- Vì vậy:
  - Toggle ON → Mở permission dialog
  - Toggle OFF → Mở system settings

### 5. Android Version Handling

- **Android 13+ (TIRAMISU)**: Cần request POST_NOTIFICATIONS permission
- **Android < 13**: Permission tự động granted, không cần request

### 6. ✅ Best Practice: Rationale BottomSheet khi Toggle OFF

**Logic:**
- **Toggle ON**: Launch permission dialog trực tiếp (đơn giản, không cần rationale)
- **Toggle OFF**: Hiện rationale BottomSheet cảnh báo trước khi mở system settings

**Rationale BottomSheet khi Toggle OFF:**
- Giải thích hậu quả khi tắt notification
- Cảnh báo user sẽ không nhận được thông báo
- Hướng dẫn user mở system settings để tắt
- User có thể hủy để giữ notification bật

**Code:**
```kotlin
// ✅ State cho BottomSheet
var showDisableWarning by remember { mutableStateOf(false) }

is SettingsEvent.OpenSystemSettings -> {
    // ✅ Toggle OFF → Hiện BottomSheet trước
    showDisableWarning = true
}

// ✅ DisableNotificationWarningBottomSheet
DisableNotificationWarningBottomSheet(
    visible = showDisableWarning,
    onDismissRequest = { showDisableWarning = false },
    onConfirm = {
        showDisableWarning = false
        notificationPermissionManager.openSystemSettings(context)
    },
)
```

**Lưu ý:**
- Sử dụng BottomSheet thay vì AlertDialog (Material Design 3)
- BottomSheet có thể scroll nếu content dài
- UX tốt hơn trên mobile devices

### 7. ✅ Best Practice: Guard trong `scheduleNotifications()`

**Vấn đề:**
- `scheduleNotifications()` có thể được gọi nhiều lần
- Gây duplicate scheduling, tốn resources

**Giải pháp:**
```kotlin
fun scheduleNotifications() {
    viewModelScope.launch {
        // ✅ Guard: Check state trước
        val currentState = _systemNotificationAllowed.value
        val storeState = userPreferencesRepository.notificationsEnabled.first()
        
        if (currentState && storeState) {
            // Already scheduled → Skip
            Timber.d("SettingsViewModel: Already scheduled, skipping")
            return@launch
        }
        
        // Update DataStore preference
        userPreferencesRepository.updateNotificationsEnabled(true)
        
        // Schedule notifications
        notificationScheduler.schedulePromotionNotification()
        notificationScheduler.scheduleTipsNotification()
    }
}
```

**Lợi ích:**
- Tránh duplicate scheduling
- Tiết kiệm resources (CPU, memory)
- Thread-safe hơn
- Tránh unnecessary database updates
- Check cả system state và DataStore state

---

## 📝 Code Reference

### NotificationPermissionScreen.kt
- Permission state sử dụng Accompanist Permissions
- Auto-navigate khi permission granted (LaunchedEffect)
- Skip button cho user không muốn cấp permission

### SettingsScreen.kt
- DisposableEffect(Unit): Initialize state (sync)
- LaunchedEffect(Unit): Refresh state (async)
- LaunchedEffect(lifecycleOwner): Refresh khi resume
- Permission launcher với "Don't ask again" handling
- Event handler: RequestPermission và OpenSystemSettings
- LaunchedEffect(systemNotificationAllowed): Schedule notifications sau khi granted
- DisableNotificationWarningBottomSheet: Rationale khi toggle OFF

### SettingsViewModel.kt
- State management: systemNotificationAllowed (StateFlow)
- initializeState(): Đồng bộ, gọi trong DisposableEffect
- refreshState(): Bất đồng bộ, retry logic cho OEM ROMs
- onToggleClicked(): Handle toggle change, cancel notifications khi OFF
- scheduleNotifications(): Với guard để tránh duplicate

### NotificationPermissionManager.kt
- areNotificationsEnabled(): Check system state
- openSystemSettings(): Mở system settings

### UserPreferencesRepository.kt
- notificationsEnabled: Flow<Boolean> (user preference)
- updateNotificationsEnabled(): Update preference

---

## 🎯 Tóm Tắt

1. **Notification Permission Screen**: Post-sign-in onboarding, auto-navigate khi granted
2. **Settings**: Toggle sync với hệ thống, ON → permission dialog, OFF → BottomSheet → system settings
3. **Sync**: Tự động refresh khi screen mở và khi app resume
4. **Single source of truth**: NotificationManagerCompat (hệ thống Android)
5. **Delay handling**: Retry và delay để xử lý Samsung/Xiaomi
6. ✅ **Best Practice**: Rationale BottomSheet khi toggle OFF (cảnh báo trước khi tắt)
7. ✅ **Best Practice**: Guard trong `scheduleNotifications()` để tránh duplicate scheduling
8. ✅ **Best Practice**: "Don't ask again" handling - tự động mở system settings
9. **Toggle ON**: Đơn giản, launch permission dialog trực tiếp (không cần rationale)
10. **Cancel notifications**: Tự động cancel khi toggle OFF

---

## 🔧 Cải Tiến Đã Triển Khai

### ✅ Cải Tiến: Rationale BottomSheet khi Toggle OFF

**Logic:**
- **Toggle ON**: Launch permission dialog trực tiếp (đơn giản, không cần rationale)
- **Toggle OFF**: Hiện rationale BottomSheet cảnh báo trước khi mở system settings

**Lý do:**
- User cần được cảnh báo trước khi tắt notification
- Rationale BottomSheet giúp user hiểu hậu quả của việc tắt notification
- User có thể hủy để giữ notification bật
- BottomSheet phù hợp với Material Design 3

**Implementation:**
- Component: `DisableNotificationWarningBottomSheet`
- Hiển thị khi `SettingsEvent.OpenSystemSettings` được emit
- User có thể hủy hoặc confirm để mở system settings

### ✅ Cải Tiến: Guard trong `scheduleNotifications()`

**Vấn đề:**
- `scheduleNotifications()` có thể được gọi nhiều lần → duplicate scheduling

**Giải pháp:**
- Thêm guard check state trước khi schedule
- Check cả system state và DataStore state
- Nếu đã scheduled → Skip

**Implementation:**
- Check `_systemNotificationAllowed.value` và `userPreferencesRepository.notificationsEnabled.first()`
- Chỉ schedule nếu cả hai đều false hoặc chưa scheduled
- Tránh duplicate scheduling và tốn resources

### ✅ Cải Tiến: "Don't ask again" Handling

**Vấn đề:**
- User có thể check "Don't ask again" trong permission dialog
- App không thể request lại permission nếu đã check

**Giải pháp:**
- Detect "Don't ask again" bằng `shouldShowRequestPermissionRationale()`
- Tự động mở system settings để user có thể bật lại

**Implementation:**
- Check trong permission launcher callback
- Nếu `!isGranted && !shouldShowRationale` → Mở system settings
- UX tốt hơn, user không bị stuck

### ✅ Cải Tiến: Auto-navigate trong Notification Permission Screen

**Lý do:**
- User không cần click "Tiếp theo" sau khi granted
- UX mượt mà hơn

**Implementation:**
- Sử dụng `LaunchedEffect(notificationPermission?.allPermissionsGranted)`
- Delay 300ms để đảm bảo permission state đã được update
- Tự động navigate khi permission granted

