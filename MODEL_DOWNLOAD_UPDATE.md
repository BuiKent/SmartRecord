# Cách Làm Padding Cho Màn Hình Có Card (Không Có Menu Bottom)

## Tổng Quan

Tài liệu này mô tả cách làm padding cho các màn hình có chứa các card, đặc biệt là màn hình **"Các con số"** (NumerologyInfoScreen) và các màn hình tương tự **không có menu bottom**.

## Pattern Chính: Column + verticalScroll + Spacer

### 1. Cấu Trúc Cơ Bản

```kotlin
Box(modifier = modifier.fillMaxSize()) {
    // Padding ngang: responsive dựa trên device type
    val horizontalPadding = if (isTabletMode || isLandscapeMode || isFoldableMode) {
        Spacing.lg // 24dp cho tablet/fold/landscape
    } else {
        Spacing.sm // 8dp cho phone thường
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding)
    ) {
        // Content: Cards, Text, etc.
        // ...
        
        // ✅ Spacer cuối với navigation bar inset + spacing
        val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomPadding = if (isTabletMode || isLandscapeMode || isFoldableMode) {
            navBarsBottom + 80.dp // Tablet/fold/landscape: 80dp
        } else {
            navBarsBottom + 70.dp // Phone: 70dp (50dp banner + 20dp spacing)
        }
        Spacer(Modifier.height(bottomPadding))
    }
    
    // Floating button (nếu có) - ở bottom end
    FloatingActionButton(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(bottom = volumeButtonBottomPadding, end = Spacing.md)
    ) {
        // Button content
    }
}
```

## Chi Tiết Từng Phần

### 1. Horizontal Padding (Padding Ngang)

**Responsive dựa trên device type:**

```kotlin
val isTabletMode = isTablet()
val isLandscapeMode = isLandscape()
val isFoldableMode = isFoldable()

val horizontalPadding = if (isTabletMode || isLandscapeMode || isFoldableMode) {
    Spacing.lg // 24dp cho tablet/fold/landscape
} else {
    Spacing.sm // 8dp cho phone thường
}
```

**Lý do:**
- Phone: 8dp - tiết kiệm không gian, content rộng hơn
- Tablet/Fold/Landscape: 24dp - tăng spacing để dễ đọc, content không quá rộng

**Áp dụng:**
```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = horizontalPadding) // ← Áp dụng ở đây
) {
    // Content
}
```

### 2. Bottom Padding (Padding Dưới)

**Tính toán dynamic dựa trên:**
- System navigation bar inset
- Banner ads height (nếu có)
- Device type

```kotlin
val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

val bottomPadding = if (isTabletMode || isLandscapeMode || isFoldableMode) {
    navBarsBottom + 80.dp // Tablet/fold/landscape: 80dp
} else {
    navBarsBottom + 70.dp // Phone: 70dp (50dp banner + 20dp spacing)
}

Spacer(Modifier.height(bottomPadding)) // ← Ở cuối Column
```

**Lý do:**
- `navBarsBottom`: Tránh content bị che bởi system navigation bar
- `70-80dp`: Tránh content bị che bởi banner ads (nếu có) + spacing
- Responsive: Tablet cần spacing lớn hơn

### 3. Card Spacing (Khoảng Cách Giữa Các Card)

**Responsive spacing giữa các card:**

```kotlin
val cardSpacing = if (isTabletMode || isLandscapeMode || isFoldableMode) {
    Spacing.md // 16dp cho tablet/fold/landscape
} else {
    Spacing.sm // 8dp cho phone thường
}
```

**Áp dụng:**
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(cardSpacing) // ← Áp dụng ở đây
) {
    // Cards
}
```

**Spacing giữa các hàng card:**
```kotlin
sortedCards.chunked(2).forEach { rowCards ->
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(cardSpacing)
    ) {
        // Cards trong hàng
    }
    Spacer(modifier = Modifier.height(Spacing.sm)) // ← 8dp giữa các hàng
}
```

### 4. Floating Button Padding

**Nếu có floating button (ví dụ: volume button):**

```kotlin
FloatingActionButton(
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .navigationBarsPadding() // ← Tránh bị che bởi system navigation bar
        .padding(
            bottom = volumeButtonBottomPadding, // Dynamic: 16dp (Premium) hoặc 58dp (có banner)
            end = Spacing.md // 16dp từ mép phải
        )
        .size(56.dp)
) {
    // Button content
}
```

**`volumeButtonBottomPadding` được tính:**
```kotlin
// Nếu có banner (không Premium) = 58.dp (50dp banner + 8dp spacing)
// Nếu Premium = 16.dp
val volumeButtonBottomPadding = if (!isPremium) 58.dp else 16.dp
```

## Ví Dụ Cụ Thể: NumerologyInfoScreen

### Cấu Trúc Đầy Đủ

```kotlin
@Composable
fun NumerologyInfoScreen(
    info: NumerologyInfo,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onVolumeClick: (() -> Unit)? = null,
    isSpeaking: Boolean = false,
    onStopSpeaking: (() -> Unit)? = null,
    volumeButtonBottomPadding: Dp = 16.dp
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1. Tính toán padding responsive
        val isTabletMode = isTablet()
        val isLandscapeMode = isLandscape()
        val isFoldableMode = isFoldable()
        
        val horizontalPadding = if (isTabletMode || isLandscapeMode || isFoldableMode) {
            Spacing.lg // 24dp
        } else {
            Spacing.sm // 8dp
        }
        
        val cardSpacing = if (isTabletMode || isLandscapeMode || isFoldableMode) {
            Spacing.md // 16dp
        } else {
            Spacing.sm // 8dp
        }
        
        // 2. Column scrollable với padding ngang
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding)
        ) {
            // Header: Họ tên và ngày sinh
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = info.fullName, ...)
                Spacer(Modifier.height(Spacing.xs))
                Text(text = info.dobDisplay, ...)
            }
            
            Spacer(Modifier.height(Spacing.lg))
            
            // Life Path card (highlighted)
            // ...
            
            Spacer(Modifier.height(Spacing.md))
            
            // Grid cards (2 cột)
            sortedCards.chunked(2).forEach { rowCards ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cardSpacing)
                ) {
                    rowCards.forEach { card ->
                        Column(modifier = Modifier.weight(1f)) {
                            // Card content
                        }
                    }
                    if (rowCards.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
            
            // 3. Spacer cuối với navigation bar inset
            val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomPadding = if (isTabletMode || isLandscapeMode || isFoldableMode) {
                navBarsBottom + 80.dp
            } else {
                navBarsBottom + 70.dp
            }
            Spacer(Modifier.height(bottomPadding))
        }
        
        // 4. Floating button (nếu có)
        FloatingActionButton(
            onClick = { /* ... */ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = volumeButtonBottomPadding, end = Spacing.md)
                .size(56.dp)
        ) {
            Icon(/* ... */)
        }
    }
}
```

## So Sánh Với Màn Hình Có Menu Bottom

### Màn Hình Có Menu Bottom (LazyColumn + contentPadding)

```kotlin
LazyColumn(
    contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 16.dp,
        bottom = maxOf(16.dp, navBarsBottom + floatingNavHeight)
    )
) {
    items(items) { item ->
        // Item content
    }
}
```

**Đặc điểm:**
- Dùng `LazyColumn` với `contentPadding`
- Padding **luôn hiển thị**, ngay cả khi content ngắn
- Tính đến floating navigation bar

### Màn Hình Không Có Menu Bottom (Column + Spacer)

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = horizontalPadding)
) {
    // Content
    // ...
    
    // Spacer cuối
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = navBarsBottom + 70.dp
    Spacer(Modifier.height(bottomPadding))
}
```

**Đặc điểm:**
- Dùng `Column` với `verticalScroll`
- Spacer chỉ hiển thị khi scroll đến cuối
- Tính đến system navigation bar và banner ads

## Best Practices

### 1. Luôn Tính Navigation Bar Inset

```kotlin
// ✅ ĐÚNG
val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
val bottomPadding = navBarsBottom + 70.dp

// ❌ SAI - Không tính navigation bar
val bottomPadding = 70.dp
```

### 2. Responsive Padding

```kotlin
// ✅ ĐÚNG - Responsive dựa trên device type
val horizontalPadding = if (isTabletMode || isLandscapeMode || isFoldableMode) {
    Spacing.lg // 24dp
} else {
    Spacing.sm // 8dp
}

// ❌ SAI - Padding cố định
val horizontalPadding = 16.dp
```

### 3. Spacer Ở Cuối Column

```kotlin
// ✅ ĐÚNG - Spacer ở cuối Column
Column(
    modifier = Modifier.verticalScroll(rememberScrollState())
) {
    // Content
    // ...
    Spacer(Modifier.height(bottomPadding)) // ← Ở cuối
}

// ❌ SAI - Không có Spacer
Column(
    modifier = Modifier.verticalScroll(rememberScrollState())
) {
    // Content
    // Không có Spacer → content có thể bị che
}
```

### 4. Floating Button Padding

```kotlin
// ✅ ĐÚNG - Tính đến banner ads
val volumeButtonBottomPadding = if (!isPremium) 58.dp else 16.dp

FloatingActionButton(
    modifier = Modifier
        .navigationBarsPadding()
        .padding(bottom = volumeButtonBottomPadding, end = Spacing.md)
) {
    // Button content
}

// ❌ SAI - Padding cố định
FloatingActionButton(
    modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
) {
    // Button có thể bị che bởi banner
}
```

## Checklist

Khi implement padding cho màn hình có card (không có menu bottom):

- [ ] Dùng `Column` + `verticalScroll` (không phải `LazyColumn`)
- [ ] Horizontal padding responsive (8dp phone, 24dp tablet)
- [ ] Tính `navBarsBottom` từ `WindowInsets.navigationBars`
- [ ] Bottom padding = `navBarsBottom + 70-80dp` (tùy device)
- [ ] Spacer ở cuối Column với bottom padding đã tính
- [ ] Card spacing responsive (8dp phone, 16dp tablet)
- [ ] Floating button có `navigationBarsPadding()`
- [ ] Floating button padding tính đến banner ads (nếu có)

## Kết Luận

**Pattern cho màn hình có card (không có menu bottom):**
1. `Column` + `verticalScroll` với horizontal padding responsive
2. Spacer cuối với `navBarsBottom + 70-80dp`
3. Card spacing responsive
4. Floating button (nếu có) với padding tính đến banner ads

**Lưu ý:** Pattern này khác với màn hình có menu bottom (dùng `LazyColumn` + `contentPadding`).

