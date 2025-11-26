# 📘 HƯỚNG DẪN THIẾT KẾ ONBOARDING & SETTINGS SCREEN

**Ngày tạo:** 2025-01-21  
**Dựa trên:** Phân tích code, flow, logic, UI từ NumerologyApp  
**Mục đích:** Tài liệu chuẩn để thiết kế Onboarding và Settings Screen cho các app khác

---

## 📋 MỤC LỤC

1. [Onboarding Screen](#1-onboarding-screen)
   - [1.1. Flow & Logic](#11-flow--logic)
   - [1.2. UI Patterns](#12-ui-patterns)
   - [1.3. Code Structure](#13-code-structure)
   - [1.4. Best Practices](#14-best-practices)
   
2. [Settings Screen](#2-settings-screen)
   - [2.1. Flow & Logic](#21-flow--logic)
   - [2.2. UI Patterns](#22-ui-patterns)
   - [2.3. Code Structure](#23-code-structure)
   - [2.4. Best Practices](#24-best-practices)

3. [Common Patterns](#3-common-patterns)
   - [3.1. State Management](#31-state-management)
   - [3.2. Navigation](#32-navigation)
   - [3.3. Permissions](#33-permissions)
   - [3.4. Data Persistence](#34-data-persistence)

---

## 1. ONBOARDING SCREEN

### 1.1. Flow & Logic

#### 1.1.1. Entry Point & Check

**Flow:**
```
AppContent (Main Entry)
    ↓
LaunchedEffect(Unit) → Check SettingsStore.onboardingCompleted
    ↓
showOnboarding = !completed
    ↓
if (showOnboarding == true) → OnboardingScreen
else → MainScreen
```

**Code Pattern:**
```kotlin
@Composable
fun AppContent() {
    var showOnboarding by remember { mutableStateOf<Boolean?>(null) }
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    
    LaunchedEffect(Unit) {
        val completed = onboardingViewModel.settingsStore
            .onboardingCompleted.first()
        showOnboarding = !completed
    }
    
    when {
        showOnboarding == null -> {
            // Loading state - có thể hiển thị splash
        }
        showOnboarding == true -> {
            OnboardingScreen(
                onComplete = { showOnboarding = false }
            )
        }
        else -> {
            MainScreen()
        }
    }
}
```

**Key Points:**
- ✅ Check onboarding status **ngay khi app khởi động** (LaunchedEffect)
- ✅ Dùng `Boolean?` để phân biệt 3 states: `null` (loading), `true` (show), `false` (hide)
- ✅ ViewModel inject qua Hilt để access SettingsStore
- ✅ Flow-based: `onboardingCompleted.first()` để lấy giá trị một lần

#### 1.1.2. Onboarding Pages Structure

**Pattern: Horizontal Pager với 4 pages**

```
Page 0: Giới thiệu app
Page 1: Giới thiệu tính năng chính
Page 2: Request permission (Notifications)
Page 3: Call-to-action (Donation, Rate, Start)
```

**Navigation Logic:**
- **Page 0-2:** Có nút "Quay lại" (nếu page > 0) và "Tiếp theo"
- **Page 2:** Khi click "Tiếp theo" → Request notification permission (Android 13+)
- **Page 3:** Hiển thị 3 nút:
  - "Ủng hộ phát triển" → Navigate to Donation (không complete onboarding)
  - "Đánh giá ứng dụng" → Complete onboarding + Open Play Store
  - "Bắt đầu" → Complete onboarding + Navigate to Main

**Code Pattern:**
```kotlin
val pagerState = rememberPagerState(pageCount = { 4 })
val currentPage = pagerState.currentPage

// Navigation buttons
if (currentPage == 3) {
    // Trang cuối
    Column {
        Button(onClick = { onNavigateToDonation() }) {
            Text("Ủng hộ phát triển")
        }
        OutlinedButton(onClick = {
            viewModel.completeOnboarding()
            onComplete()
            onNavigateToRate()
        }) {
            Text("Đánh giá ứng dụng")
        }
        Button(onClick = {
            viewModel.completeOnboarding()
            onComplete()
        }) {
            Text("Bắt đầu")
        }
    }
} else {
    // Các trang khác
    Row {
        if (currentPage > 0) {
            OutlinedButton(onClick = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(currentPage - 1)
                }
            }) {
                Text("Quay lại")
            }
        }
        Button(onClick = {
            if (currentPage == 2) {
                // Request permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU 
                    && !hasNotificationPermission) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(3)
                    }
                }
            } else {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(currentPage + 1)
                }
            }
        }) {
            Text("Tiếp theo")
        }
    }
}
```

#### 1.1.3. Permission Handling

**Pattern: Request permission ở page 2, auto-navigate sau khi grant**

```kotlin
val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { isGranted ->
    hasNotificationPermission = isGranted
    if (isGranted) {
        viewModel.enableNotifications()
    }
    // Auto-navigate to next page
    coroutineScope.launch {
        pagerState.animateScrollToPage(3)
    }
}
```

**Key Points:**
- ✅ Check permission state **trước khi request** (tránh request nhiều lần)
- ✅ Update ViewModel state khi permission granted
- ✅ Auto-navigate sau khi xử lý permission
- ✅ Handle Android version: < 13 không cần request

#### 1.1.4. Completion Logic

**Pattern: Save to DataStore, trigger navigation**

```kotlin
fun completeOnboarding() {
    viewModelScope.launch {
        settingsStore.setOnboardingCompleted(true)
    }
}
```

**Key Points:**
- ✅ Save state vào DataStore (persistent)
- ✅ Use `viewModelScope.launch` cho suspend function
- ✅ Parent Composable handle navigation (onComplete callback)

---

### 1.2. UI Patterns

#### 1.2.1. Layout Structure

**Hierarchy:**
```
Box (fillMaxSize, gradient background)
    ↓
Column (fillMaxSize, center alignment)
    ├─ Spacer (top padding)
    ├─ App Icon/Logo (96dp, rounded 24dp)
    ├─ Title Text (headlineMedium, bold, center)
    ├─ HorizontalPager (weight(1f))
    │   └─ Card (fillMaxWidth, fillMaxHeight)
    │       └─ Column (padding, verticalScroll)
    │           └─ OnboardingContent(page)
    ├─ Page Indicators (Row, 4 dots)
    └─ Navigation Buttons (Row/Column, navigationBarsPadding)
```

**Code Pattern:**
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.background
                )
            )
        )
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Image(
            bitmap = iconBitmap,
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
        )
        
        Spacer(Modifier.height(Spacing.md))
        
        // Title
        Text(
            text = "Chào mừng đến với\nThần Số Học",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(Spacing.md))
        
        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md)
                        .verticalScroll(rememberScrollState())
                ) {
                    OnboardingContent(page = page)
                }
            }
        }
        
        // Indicators & Buttons...
    }
}
```

#### 1.2.2. Page Indicators

**Pattern: Animated dots với size & alpha changes**

```kotlin
Row(
    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    modifier = Modifier.padding(vertical = Spacing.sm)
) {
    repeat(4) { index ->
        val alpha by animateFloatAsState(
            targetValue = if (index == currentPage) 1f else 0.3f,
            animationSpec = tween(300),
            label = "indicator"
        )
        Box(
            modifier = Modifier
                .size(if (index == currentPage) 10.dp else 6.dp)
                .alpha(alpha)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50)
                )
        )
    }
}
```

**Key Points:**
- ✅ Active dot: 10dp, alpha = 1f
- ✅ Inactive dots: 6dp, alpha = 0.3f
- ✅ Smooth animation (300ms tween)
- ✅ Rounded shape (50% = circle)

#### 1.2.3. Content Cards

**Pattern: Scrollable card với title + content**

```kotlin
@Composable
private fun OnboardingPage(
    title: String,
    content: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

**Key Points:**
- ✅ Title: `titleLarge`, bold, center
- ✅ Content: `bodyMedium`, center, lineHeight 20sp
- ✅ Color: `onSurfaceVariant` cho content (softer)
- ✅ Spacing: `spacedBy(Spacing.sm)` giữa title và content

#### 1.2.4. Button Layout

**Pattern: Full-width buttons với proper insets**

```kotlin
// Trang cuối
Column(
    modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()  // ✅ Tránh system nav bar
        .padding(bottom = Spacing.md),
    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
) {
    Button(
        onClick = { /* ... */ },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Favorite, null)
        Spacer(Modifier.width(Spacing.sm))
        Text("Ủng hộ phát triển")
    }
    
    OutlinedButton(
        onClick = { /* ... */ },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Đánh giá ứng dụng")
    }
    
    Button(
        onClick = { /* ... */ },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Bắt đầu")
    }
}
```

**Key Points:**
- ✅ `navigationBarsPadding()` để tránh system navigation bar
- ✅ Full-width buttons (`fillMaxWidth()`)
- ✅ Spacing giữa buttons (`spacedBy(Spacing.sm)`)
- ✅ Primary button cho action chính, OutlinedButton cho secondary

---

### 1.3. Code Structure

#### 1.3.1. ViewModel

```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val settingsStore: SettingsStore
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsStore.setOnboardingCompleted(true)
        }
    }
    
    fun enableNotifications() {
        viewModelScope.launch {
            settingsStore.setNotificationsEnabled(true)
        }
    }
}
```

**Key Points:**
- ✅ Inject `SettingsStore` qua constructor
- ✅ Expose `settingsStore` public để Composable access
- ✅ StateFlow cho UI state (nếu cần loading/error states)
- ✅ Suspend functions trong `viewModelScope.launch`

#### 1.3.2. DataStore Keys

```kotlin
object PrefKeys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
}

// SettingsStore
val onboardingCompleted: Flow<Boolean> = ds.data.map {
    it[PrefKeys.ONBOARDING_COMPLETED] ?: false
}

suspend fun setOnboardingCompleted(v: Boolean) {
    ds.edit { it[PrefKeys.ONBOARDING_COMPLETED] = v }
}
```

**Key Points:**
- ✅ Default value: `false` (chưa complete)
- ✅ Flow-based để observe changes
- ✅ Suspend function để write

---

### 1.4. Best Practices

#### ✅ DO

1. **Check onboarding status ngay khi app khởi động**
   - Dùng `LaunchedEffect(Unit)` trong AppContent
   - Show loading state nếu cần

2. **Horizontal Pager cho multi-page onboarding**
   - Smooth swipe gestures
   - Animated page indicators
   - Proper navigation buttons

3. **Request permissions ở page riêng**
   - Không request ngay khi app mở
   - Giải thích lý do trước khi request
   - Auto-navigate sau khi grant/deny

4. **Multiple completion paths**
   - "Bắt đầu" → Complete + Navigate
   - "Đánh giá" → Complete + Rate + Navigate
   - "Ủng hộ" → Navigate (không complete)

5. **Proper insets handling**
   - `statusBarsPadding()` cho top
   - `navigationBarsPadding()` cho bottom buttons
   - Gradient background cho visual appeal

6. **Save state to DataStore**
   - Persistent across app restarts
   - Flow-based để observe changes

#### ❌ DON'T

1. **Không force complete onboarding**
   - Cho phép user skip (nếu cần)
   - Không block navigation nếu user deny permission

2. **Không request nhiều permissions cùng lúc**
   - Request từng cái một
   - Giải thích rõ ràng từng permission

3. **Không dùng SharedPreferences cũ**
   - Dùng DataStore (type-safe, Flow-based)

4. **Không hardcode page count**
   - Dùng `pageCount = { 4 }` để dễ thay đổi

5. **Không bỏ qua insets**
   - Luôn handle system bars properly

---

## 2. SETTINGS SCREEN

### 2.1. Flow & Logic

#### 2.1.1. Entry Point & State Initialization

**Flow:**
```
User clicks Settings icon
    ↓
SettingScreen composed
    ↓
DisposableEffect(Unit) → initializeState() (sync)
    ↓
LaunchedEffect(Unit) → refreshState() (async)
    ↓
LaunchedEffect(lifecycleOwner) → repeatOnLifecycle(RESUMED) → refreshState()
    ↓
Display settings with correct state
```

**Code Pattern:**
```kotlin
@Composable
fun SettingScreen(
    vm: SettingViewModel = hiltViewModel()
) {
    val settings by vm.settings.collectAsState()
    
    // ✅ Initialize state immediately (synchronous)
    DisposableEffect(Unit) {
        vm.initializeState(context)
        onDispose { }
    }
    
    // ✅ Refresh state when screen opens (async)
    LaunchedEffect(Unit) {
        vm.refreshState(context)
    }
    
    // ✅ Refresh state when app resumes (user returns from system settings)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(120) // Small stabilization delay
            vm.refreshState(context)
        }
    }
}
```

**Key Points:**
- ✅ **Sync initialization** để UI hiển thị đúng state ngay lập tức
- ✅ **Async refresh** để update nếu có thay đổi
- ✅ **Lifecycle-aware refresh** khi user quay lại từ system settings
- ✅ Delay nhỏ (120ms) để system kịp update permission state

#### 2.1.2. Settings Categories

**Structure:**
```
1. Toggles (Switches)
   - Tự động nói (TTS)
   - Thông báo (Notifications)
   
2. Navigation Cards
   - Nâng cấp Premium (primaryContainer color)
   - Về chúng tôi (surfaceVariant color)
   - Chính sách riêng tư (surfaceVariant color)
   - Điều khoản sử dụng (surfaceVariant color)
   
3. Footer
   - Copyright
   - Version info
```

**Code Pattern:**
```kotlin
LazyColumn(
    contentPadding = PaddingValues(
        start = contentPadding,
        end = contentPadding,
        top = contentPadding,
        bottom = maxOf(contentPadding, navBarsBottom + floatingNavHeight)
    )
) {
    item {
        // Toggle: Tự động nói
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.settings_auto_speak),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = settings.autoSpeak,
                onCheckedChange = { vm.setTtsAuto(it) }
            )
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
    }
    
    item {
        // Toggle: Thông báo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Thông báo",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = settings.notificationsEnabled,
                onCheckedChange = { vm.onNotificationToggleChanged(it) }
            )
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
    }
    
    item {
        // Card: Premium
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToPremium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = ...)
                    Spacer(Modifier.width(Spacing.md))
                    Column {
                        Text("Nâng Cấp Premium", fontWeight = FontWeight.Bold)
                        Text("Bỏ quảng cáo, hỗ trợ phát triển", style = ...)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.rotate(180f))
            }
        }
    }
    
    // More cards...
    
    item {
        // Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("© 2025 App Name", style = ...)
            Text("Tất cả quyền được bảo lưu.", style = ...)
            Text("Phiên bản ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = ...)
        }
    }
}
```

#### 2.1.3. Notification Permission Handling

**Pattern: System state as single source of truth**

```kotlin
// ViewModel
private val _systemNotificationAllowed = MutableStateFlow(false)
val systemNotificationAllowed = _systemNotificationAllowed.asStateFlow()

val settings = combine(
    store.language,
    store.ttsAuto,
    store.reduceGrouped,
    systemNotificationAllowed  // ✅ Use system state
) { lang, auto, grouped, notifications ->
    SettingsUi(lang, auto, grouped, notifications)
}.stateIn(...)

fun initializeState(context: Context) {
    val enabled = notificationPermissionManager.areNotificationsEnabled(context)
    _systemNotificationAllowed.value = enabled
}

fun refreshState(context: Context) {
    viewModelScope.launch {
        val firstCheck = notificationPermissionManager.areNotificationsEnabled(context)
        _systemNotificationAllowed.value = firstCheck
        
        // ✅ Retry if system hasn't updated yet (handle Samsung/Xiaomi delay)
        if (!firstCheck) {
            repeat(3) { attempt ->
                delay(180)
                val retryState = notificationPermissionManager.areNotificationsEnabled(context)
                if (retryState != firstCheck) {
                    _systemNotificationAllowed.value = retryState
                    return@launch
                }
            }
        }
    }
}

fun onNotificationToggleChanged(wantsToEnable: Boolean) {
    viewModelScope.launch {
        val currentValue = uiState.value.notificationsEnabled
        
        if (wantsToEnable && !currentValue) {
            // ✅ Toggle ON → Request permission dialog
            _eventFlow.emit(SettingsEvent.RequestNotificationPermission)
        } else if (!wantsToEnable && currentValue) {
            // ✅ Toggle OFF → Open system settings (permission dialog cannot disable)
            _eventFlow.emit(SettingsEvent.OpenSystemSettings)
        }
    }
}
```

**Screen Event Handling:**
```kotlin
// Permission launcher
val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { _ ->
    coroutineScope.launch {
        delay(150) // Allow system to update
        vm.refreshState(context)
    }
}

// Event handler
LaunchedEffect(key1 = vm) {
    vm.eventFlow.collect { event ->
        when (event) {
            is SettingsEvent.RequestNotificationPermission -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            is SettingsEvent.OpenSystemSettings -> {
                vm.openSystemSettings(context)
            }
        }
    }
}
```

**Key Points:**
- ✅ **System state as source of truth**: Dùng `NotificationManagerCompat.areNotificationsEnabled()`
- ✅ **Toggle ON**: Request permission dialog
- ✅ **Toggle OFF**: Open system settings (permission dialog không thể disable)
- ✅ **Retry logic**: Handle delay trên Samsung/Xiaomi
- ✅ **Event-based**: Dùng SharedFlow để communicate giữa ViewModel và Composable

---

### 2.2. UI Patterns

#### 2.2.1. Layout Structure

**Hierarchy (Thực tế trong app):**
```
MainScreen
    └─ AppScaffold (wrap toàn bộ NavHost)
        ├─ TopAppBar (inject SettingTopBar khi route = "settings")
        └─ NavHost
            └─ composable("settings")
                └─ SettingScreen
                    └─ LazyColumn (chỉ có content, không có AppScaffold)
                        ├─ contentPadding (all sides + bottom for nav bar)
                        ├─ Items:
                        │   ├─ Toggle rows (with HorizontalDivider)
                        │   ├─ Navigation cards
                        │   └─ Footer
                        └─ Bottom padding (navBarsBottom + floatingNavHeight)
```

**Code Pattern (MainScreen.kt):**
```kotlin
// MainScreen wraps toàn bộ với AppScaffold
AppScaffold(
    topBar = {
        when (currentRoute) {
            "settings" -> {
                SettingTopBar(onBack = { navController.popBackStack() })
            }
            // ... other routes
        }
    }
) { _ ->
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("settings") {
            SettingScreen(
                onBack = { /* ... */ },
                onNavigateToPrivacyPolicy = { /* ... */ },
                // ... other callbacks
            )
        }
    }
}
```

**Code Pattern (SettingScreen.kt - chỉ có LazyColumn):**
```kotlin
@Composable
fun SettingScreen(
    vm: SettingViewModel = hiltViewModel(),
    // ... callbacks
) {
    val settings by vm.settings.collectAsState()
    val contentPadding = getContentPadding()
    val maxContentWidth = getMaxContentWidth()
    
    // ✅ Navigation bar inset + spacing
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingNavHeight = 72.dp + 8.dp
    
    // ✅ Chỉ có LazyColumn, không có AppScaffold (được wrap ở MainScreen)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (maxContentWidth != Dp.Unspecified) {
                    Modifier.widthIn(max = maxContentWidth)
                } else {
                    Modifier
                }
            ),
        contentPadding = PaddingValues(
            start = contentPadding,
            end = contentPadding,
            top = contentPadding,
            bottom = maxOf(contentPadding, navBarsBottom + floatingNavHeight)
        )
    ) {
        // Items...
    }
}
```

**Code Pattern (SettingTopBar.kt - tách riêng):**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingTopBar(
    onBack: () -> Unit = {}
) {
    // Pattern C: TopAppBar tự xử lý insets (windowInsets mặc định = WindowInsets.statusBars)
    TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) }
        // Bỏ navigationIcon vì có FloatingBackButton ở góc dưới phải (nếu cần)
    )
}
```

**Key Points:**
- ✅ **AppScaffold ở MainScreen level** - wrap toàn bộ NavHost
- ✅ **TopAppBar được inject** dựa trên `currentRoute` trong MainScreen
- ✅ **SettingScreen chỉ có LazyColumn** - không có AppScaffold bên trong
- ✅ **SettingTopBar tách riêng** - để inject vào AppScaffold ở MainScreen
- ✅ `LazyColumn` với `contentPadding` để handle bottom nav bar
- ✅ `maxOf()` để đảm bảo đủ space cho nav bar

#### 2.2.2. Toggle Rows

**Pattern: Row với Text (weight) + Switch**

```kotlin
Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth()
) {
    Text(
        text = "Tự động nói",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f)  // ✅ Take available space
    )
    Switch(
        checked = settings.autoSpeak,
        onCheckedChange = { vm.setTtsAuto(it) }
    )
}
HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
```

**Key Points:**
- ✅ `weight(1f)` cho Text để push Switch sang bên phải
- ✅ `SpaceBetween` arrangement
- ✅ `HorizontalDivider` giữa các items
- ✅ `bodyLarge` typography cho label

#### 2.2.3. Navigation Cards

**Pattern: Clickable Card với icon + text + arrow**

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    onClick = onNavigateToPremium,
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer  // ✅ Highlight Premium
    )
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(
                    text = "Nâng Cấp Premium",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Bỏ quảng cáo, hỗ trợ phát triển",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.rotate(180f),  // ✅ Rotate để thành arrow right
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
```

**Key Points:**
- ✅ `primaryContainer` color cho Premium (highlight)
- ✅ `surfaceVariant` color cho các cards khác
- ✅ Icon + Text + Arrow layout
- ✅ Rotate arrow icon 180° để thành arrow right
- ✅ Subtitle với alpha 0.8f cho softer text

#### 2.2.4. Footer

**Pattern: Centered text với version info**

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = Spacing.xl),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Text(
        text = "© 2025 App Name",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Text(
        text = "Tất cả quyền được bảo lưu.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(Spacing.xs))
    Text(
        text = "Phiên bản ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}
```

**Key Points:**
- ✅ Centered alignment
- ✅ `bodySmall` typography
- ✅ `onSurfaceVariant` color (softer)
- ✅ Version từ `BuildConfig`

---

### 2.3. Code Structure

#### 2.3.1. ViewModel

```kotlin
@HiltViewModel
class SettingViewModel @Inject constructor(
    val store: SettingsStore,
    private val notificationPermissionManager: NotificationPermissionManager
) : BaseViewModel<SettingsUi>() {
    
    override val _uiState = MutableStateFlow(SettingsUi())
    
    // ✅ System notification state as single source of truth
    private val _systemNotificationAllowed = MutableStateFlow(false)
    val systemNotificationAllowed = _systemNotificationAllowed.asStateFlow()
    
    // Events
    private val _eventFlow = MutableSharedFlow<SettingsEvent>()
    val eventFlow: SharedFlow<SettingsEvent> = _eventFlow.asSharedFlow()
    
    // ✅ Combine với system notification state
    val settings = combine(
        store.language,
        store.ttsAuto,
        store.reduceGrouped,
        systemNotificationAllowed
    ) { lang, auto, grouped, notifications ->
        SettingsUi(lang, auto, grouped, notifications)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUi()
    )
    
    init {
        viewModelScope.launch {
            settings.collect { settings ->
                updateState { settings }
            }
        }
    }
    
    fun initializeState(context: Context) {
        val enabled = notificationPermissionManager.areNotificationsEnabled(context)
        _systemNotificationAllowed.value = enabled
    }
    
    fun refreshState(context: Context) {
        viewModelScope.launch {
            val firstCheck = notificationPermissionManager.areNotificationsEnabled(context)
            _systemNotificationAllowed.value = firstCheck
            
            // Retry logic for Samsung/Xiaomi
            if (!firstCheck) {
                repeat(3) { attempt ->
                    delay(180)
                    val retryState = notificationPermissionManager.areNotificationsEnabled(context)
                    if (retryState != firstCheck) {
                        _systemNotificationAllowed.value = retryState
                        return@launch
                    }
                }
            }
        }
    }
    
    fun onNotificationToggleChanged(wantsToEnable: Boolean) {
        viewModelScope.launch {
            val currentValue = uiState.value.notificationsEnabled
            
            if (wantsToEnable && !currentValue) {
                _eventFlow.emit(SettingsEvent.RequestNotificationPermission)
            } else if (!wantsToEnable && currentValue) {
                _eventFlow.emit(SettingsEvent.OpenSystemSettings)
            }
        }
    }
}
```

**Key Points:**
- ✅ Inject dependencies qua constructor
- ✅ System state as source of truth (không dùng DataStore cho notification state)
- ✅ Event-based communication (SharedFlow)
- ✅ Combine flows để tạo settings state
- ✅ Retry logic cho Samsung/Xiaomi delay

#### 2.3.2. Permission Manager

```kotlin
class NotificationPermissionManager {
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    
    fun openSystemSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }
}
```

**Key Points:**
- ✅ Dùng `NotificationManagerCompat.areNotificationsEnabled()` (Google recommended)
- ✅ `openSystemSettings()` handle cả Android 8.0+ và < 8.0
- ✅ Singleton scope với Hilt injection
- ✅ System state as single source of truth (không lưu vào DataStore)

---

### 2.4. Best Practices

#### ✅ DO

1. **Initialize state synchronously**
   - Dùng `DisposableEffect` để set initial state ngay
   - Tránh UI flash với wrong state

2. **Refresh state on resume**
   - Dùng `repeatOnLifecycle(RESUMED)` để refresh khi user quay lại
   - Delay nhỏ (120ms) để system kịp update

3. **System state as source of truth**
   - Dùng `NotificationManagerCompat.areNotificationsEnabled()`
   - Không lưu notification state vào DataStore

4. **Event-based communication**
   - Dùng SharedFlow để communicate giữa ViewModel và Composable
   - Tách biệt logic và UI

5. **Retry logic for permission state**
   - Handle delay trên Samsung/Xiaomi
   - Retry 3 lần với delay 180ms

6. **Proper insets handling**
   - `contentPadding` cho LazyColumn
   - `maxOf()` để đảm bảo đủ space cho nav bar

7. **Card colors for hierarchy**
   - `primaryContainer` cho Premium (highlight)
   - `surfaceVariant` cho các cards khác

#### ❌ DON'T

1. **Không dùng DataStore cho notification state**
   - System state là source of truth
   - DataStore chỉ lưu user preferences

2. **Không hardcode permission state**
   - Luôn check từ system
   - Handle Android version differences

3. **Không bỏ qua lifecycle**
   - Refresh state khi app resumes
   - Handle permission changes properly

4. **Không dùng callback trực tiếp**
   - Dùng event flow để tách biệt logic

5. **Không bỏ qua insets**
   - Luôn handle bottom nav bar properly

---

## 3. COMMON PATTERNS

### 3.1. State Management

#### 3.1.1. DataStore Pattern

```kotlin
// Keys
object PrefKeys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val TTS_AUTO = booleanPreferencesKey("tts_auto")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
}

// Store
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val ds = ctx.dataStore
    
    val onboardingCompleted: Flow<Boolean> = ds.data.map {
        it[PrefKeys.ONBOARDING_COMPLETED] ?: false
    }
    
    suspend fun setOnboardingCompleted(v: Boolean) {
        ds.edit { it[PrefKeys.ONBOARDING_COMPLETED] = v }
    }
}
```

**Key Points:**
- ✅ Type-safe keys
- ✅ Flow-based để observe changes
- ✅ Default values
- ✅ Suspend functions cho write operations

#### 3.1.2. ViewModel State Pattern

```kotlin
data class SettingsUi(
    val language: String = "vi-VN",
    val autoSpeak: Boolean = true,
    val notificationsEnabled: Boolean = true
)

@HiltViewModel
class SettingViewModel @Inject constructor(
    val store: SettingsStore
) : BaseViewModel<SettingsUi>() {
    
    override val _uiState = MutableStateFlow(SettingsUi())
    
    val settings = combine(
        store.language,
        store.ttsAuto,
        systemNotificationAllowed
    ) { lang, auto, notifications ->
        SettingsUi(lang, auto, notifications)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUi()
    )
}
```

**Key Points:**
- ✅ Data class cho UI state
- ✅ Combine flows để tạo state
- ✅ `stateIn` với `WhileSubscribed(5000)`
- ✅ Sync với `_uiState` trong init

---

### 3.2. Navigation

#### 3.2.1. Callback Pattern

```kotlin
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onNavigateToDonation: () -> Unit = {},
    onNavigateToRate: () -> Unit = {}
) {
    // ...
    Button(onClick = {
        viewModel.completeOnboarding()
        onComplete()  // ✅ Parent handles navigation
    }) {
        Text("Bắt đầu")
    }
}
```

**Key Points:**
- ✅ Callbacks cho navigation (separation of concerns)
- ✅ ViewModel chỉ handle business logic
- ✅ Parent Composable handle navigation

#### 3.2.2. Navigation from Onboarding

```kotlin
@Composable
fun AppContent() {
    var showOnboarding by remember { mutableStateOf<Boolean?>(null) }
    var showDonationFromOnboarding by remember { mutableStateOf(false) }
    var pendingNavigationRoute by remember { mutableStateOf<String?>(null) }
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    
    LaunchedEffect(Unit) {
        val completed = onboardingViewModel.settingsStore.onboardingCompleted.first()
        showOnboarding = !completed
    }
    
    when {
        showOnboarding == null -> {
            // Đang check onboarding status - có thể hiển thị loading
        }
        showOnboarding == true && !showDonationFromOnboarding -> {
            OnboardingScreen(
                onComplete = { showOnboarding = false },
                onNavigateToDonation = {
                    // Không complete onboarding, chỉ hiển thị donation screen
                    showDonationFromOnboarding = true
                },
                onNavigateToRate = {
                    pendingNavigationRoute = "rate"
                    showOnboarding = false
                }
            )
        }
        showDonationFromOnboarding -> {
            // Hiển thị donation screen từ onboarding
            DonationScreen(
                onNext = {
                    // Complete onboarding và vào HomeScreen
                    onboardingViewModel.completeOnboarding()
                    showOnboarding = false
                    showDonationFromOnboarding = false
                },
                isFromOnboarding = true
            )
        }
        else -> {
            MainScreen(
                pendingNavigationRoute = pendingNavigationRoute,
                onNavigationHandled = {
                    pendingNavigationRoute = null
                }
            )
        }
    }
}
```

**Key Points:**
- ✅ State-based navigation
- ✅ Multiple paths (complete, donation, rate)
- ✅ Clear state management

---

### 3.3. Permissions

#### 3.3.1. Permission Launcher Pattern

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { isGranted ->
    hasPermission = isGranted
    if (isGranted) {
        viewModel.enableFeature()
    }
    // Auto-navigate or update UI
}

Button(onClick = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}) {
    Text("Tiếp theo")
}
```

**Key Points:**
- ✅ `rememberLauncherForActivityResult` cho permission
- ✅ Check version và current state trước khi request
- ✅ Update ViewModel state khi granted
- ✅ Handle navigation/UI update sau khi request

#### 3.3.2. System Settings Pattern

```kotlin
// NotificationPermissionManager.kt
@Singleton
class NotificationPermissionManager @Inject constructor() {
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    
    fun openSystemSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        context.startActivity(intent)
    }
}

// In ViewModel
fun onNotificationToggleChanged(wantsToEnable: Boolean) {
    if (!wantsToEnable && currentValue) {
        _eventFlow.emit(SettingsEvent.OpenSystemSettings)
    }
}

// In Composable
LaunchedEffect(key1 = vm) {
    vm.eventFlow.collect { event ->
        when (event) {
            is SettingsEvent.OpenSystemSettings -> {
                vm.openSystemSettings(context)
            }
        }
    }
}
```

**Key Points:**
- ✅ Open system settings khi toggle OFF
- ✅ Permission dialog không thể disable notification
- ✅ Event-based communication

---

### 3.4. Data Persistence

#### 3.4.1. DataStore Setup

```kotlin
// Build.gradle.kts
dependencies {
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}

// Context extension
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// Store class
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val ds = ctx.dataStore
    
    // Read
    val onboardingCompleted: Flow<Boolean> = ds.data.map {
        it[PrefKeys.ONBOARDING_COMPLETED] ?: false
    }
    
    // Write
    suspend fun setOnboardingCompleted(v: Boolean) {
        ds.edit { it[PrefKeys.ONBOARDING_COMPLETED] = v }
    }
}
```

**Key Points:**
- ✅ Type-safe keys
- ✅ Flow-based reads
- ✅ Suspend functions cho writes
- ✅ Singleton scope

#### 3.4.2. Usage in ViewModel

```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val settingsStore: SettingsStore
) : ViewModel() {
    
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsStore.setOnboardingCompleted(true)
        }
    }
}
```

**Key Points:**
- ✅ Inject store qua constructor
- ✅ Use `viewModelScope.launch` cho suspend functions
- ✅ Expose store public nếu Composable cần access

---

## 📝 TÓM TẮT

### Onboarding Screen

1. **Flow:** Check status → Show pages → Request permissions → Complete
2. **UI:** Horizontal Pager + Page indicators + Navigation buttons
3. **State:** DataStore (`onboardingCompleted`)
4. **Permissions:** Request ở page riêng, auto-navigate sau khi grant

### Settings Screen

1. **Flow:** Initialize state → Refresh on resume → Handle toggle events
2. **UI:** LazyColumn với toggles + navigation cards + footer
3. **State:** DataStore (user preferences) + System state (notifications)
4. **Permissions:** Toggle ON → Request dialog, Toggle OFF → System settings

### Common Patterns

1. **State Management:** DataStore + ViewModel + StateFlow
2. **Navigation:** Callback pattern, state-based navigation
3. **Permissions:** Launcher pattern, system settings fallback
4. **Data Persistence:** DataStore với type-safe keys

---

**Kết thúc tài liệu**

