# ANDROID PROJECT TEMPLATE GUIDE

## 📱 TEMPLATE CHUẨN - ANDROID APP VỚI KOTLIN & COMPOSE

**Mục đích:** File này mô tả cấu trúc, versions, cấu hình và best practices để tạo một app Android hiện đại, ổn định và sẵn sàng cho production.

**Đặc điểm:**
- ✅ Full Kotlin (100% Kotlin, không có Java)
- ✅ Jetpack Compose (Kotlin-first UI)
- ✅ Versions mới nhất và đã được verify
- ✅ Sẵn sàng mở rộng với nhiều chức năng

---

## 📊 VERSIONS ĐANG DÙNG (ĐÃ VERIFY)

### 1. Build Tools & Plugins

```toml
AGP (Android Gradle Plugin):    8.13.0    # ✅ Stable, verified
Kotlin:                         2.2.21    # ✅ Latest stable
Gradle:                         8.13      # ✅ Compatible
Java:                           21        # ✅ LTS mới nhất
```

### 2. Android SDK

```kotlin
compileSdk:    36    # ✅ Android 16, mới nhất
targetSdk:     36    # ✅ Android 16, mới nhất
minSdk:        24    # ✅ Android 7.0 Nougat (99.5% device coverage)
```

### 3. AndroidX Libraries

```toml
core-ktx:                   1.16.0    # ✅ Latest stable
lifecycle-runtime-ktx:      2.9.4     # ✅ Latest stable
activity-compose:           1.10.1    # ✅ Stable (hoặc 1.11.0 cho latest)
```

### 4. Jetpack Compose

```toml
Compose BOM:                2025.06.00    # ✅ Stable (hoặc 2025.11.00 cho latest)
```

**Compose Libraries (managed by BOM):**
- compose-ui
- compose-material3
- compose-ui-tooling-preview
- compose-ui-tooling (debug)
- compose-foundation
- compose-runtime

### 5. Testing Libraries

```toml
JUnit:                      4.13.2    # ✅ Latest JUnit 4
androidx.test.ext:junit:   1.2.1      # ✅ Stable (hoặc 1.3.0 cho latest)
espresso-core:             3.6.1      # ✅ Stable (hoặc 3.7.0 cho latest)
```

---

## 🏗️ CẤU TRÚC PROJECT

### Hiện tại (Basic Structure)

```
ReceiptApp/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/receiptapp/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── ui/
│   │   │   │       └── theme/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── test/
│   │   └── androidTest/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   ├── libs.versions.toml    # ✅ Version catalog
│   └── wrapper/
├── build.gradle.kts           # Root build config
├── settings.gradle.kts
├── gradle.properties
└── TEMPLATE_GUIDE.md          # File này
```

### Cấu trúc đề xuất khi mở rộng (Feature-based)

```
app/src/main/java/com/example/receiptapp/
├── activity/
│   └── MainActivity.kt
├── features/                  # Feature-based modules
│   ├── [feature1]/
│   │   ├── data/
│   │   ├── domain/
│   │   ├── presentation/
│   │   └── ui/
│   └── shared/
├── data/                      # Data layer (nếu cần)
│   ├── database/
│   ├── network/
│   └── repository/
├── di/                        # Dependency Injection (nếu thêm Hilt)
├── ui/                        # UI components
│   ├── theme/
│   └── composables/
├── utils/                     # Utilities
└── MainApplication.kt         # Application class (nếu cần)
```

---

## ⚙️ CẤU HÌNH BUILD

### 1. Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

### 2. App `build.gradle.kts` - Key Configurations

```kotlin
android {
    namespace = "com.example.receiptapp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.receiptapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Kotlin 2.2+ compilerOptions DSL
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
                "-Xjvm-default=all",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false  // Không cần cho full Kotlin app
    }
}
```

### 3. `gradle/libs.versions.toml` - Version Catalog

```toml
[versions]
agp = "8.13.0"
kotlin = "2.2.21"
coreKtx = "1.16.0"
lifecycleRuntimeKtx = "2.9.4"
activityCompose = "1.10.1"
composeBom = "2025.06.00"
junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.6.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
# ... (xem file thực tế)

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### 4. `gradle.properties` - Performance

```properties
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

---

## ✅ FULL KOTLIN SETUP

### Xác nhận App Full Kotlin

- ✅ **Không có file Java** (`.java`) trong project
- ✅ **Tất cả code đều là Kotlin** (`.kt` files)
- ✅ **Build output**: `compileDebugJavaWithJavac NO-SOURCE`

### Lưu ý quan trọng

1. **Kotlin files trong thư mục `java/`**
   - Đây là convention của Android
   - Không có nghĩa là đang dùng Java
   - Gradle tự động nhận diện `.kt` files

2. **JVM Target không phải là Java**
   - Kotlin compile ra JVM bytecode
   - Cần JVM target để tương thích với Android Runtime (ART)
   - Java 21 là LTS mới nhất, hiệu suất tốt hơn

3. **Kotlin Compiler Options**
   - Sử dụng `compilerOptions` DSL (Kotlin 2.2+)
   - Không dùng `kotlinOptions` (deprecated)

---

## 🚀 HƯỚNG DẪN NÂNG CẤP KHI CẦN

### Phase 1: Thêm Dependencies Cơ Bản

#### Navigation Compose
```toml
[versions]
navigationCompose = "2.9.4"

[libraries]
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
```

#### Dependency Injection (Hilt)
```toml
[versions]
hilt = "2.57"
hiltNavigationCompose = "1.2.0"

[plugins]
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

#### Database (Room)
```toml
[versions]
room = "2.7.0"
ksp = "2.2.21-2.0.4"

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
```

#### Networking (Retrofit)
```toml
[versions]
retrofit = "2.11.0"
gson = "2.11.0"

[libraries]
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "gson" }
```

#### Image Loading (Coil)
```toml
[versions]
coil = "2.7.0"

[libraries]
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
```

### Phase 2: Release-Ready

1. **Bật R8/ProGuard**
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(...)
    }
}
```

2. **Build Variants**
```kotlin
buildTypes {
    debug { ... }
    release { ... }
    staging { ... }  // Nếu cần
}
```

3. **Signing Config**
```kotlin
signingConfigs {
    create("release") {
        // Setup keystore
    }
}
```

4. **Đổi Application ID**
```kotlin
applicationId = "com.yourcompany.receiptapp"  // Thay vì com.example
```

### Phase 3: Architecture (Nếu cần)

1. **Clean Architecture**
   - Data Layer: Repository, DataSource
   - Domain Layer: Use Cases
   - Presentation Layer: ViewModel, UI

2. **MVVM Pattern**
   - ViewModel cho state management
   - StateFlow cho reactive state
   - Repository pattern

3. **Feature-based Structure**
   - Mỗi feature có: data, domain, presentation, ui
   - Shared code trong features/shared/

---

## 📝 BEST PRACTICES

### 1. Code Organization

- ✅ Feature-based structure (khi app lớn)
- ✅ Separation of concerns
- ✅ Single Responsibility Principle
- ✅ DRY (Don't Repeat Yourself)

### 2. Kotlin Best Practices

- ✅ Data classes cho models
- ✅ Sealed classes cho state
- ✅ Extension functions cho utilities
- ✅ Coroutines cho async operations
- ✅ Flow cho reactive streams
- ✅ Delegates: `by lazy`, `by viewModels()`

### 3. Compose Best Practices

- ✅ Sử dụng `remember` và `derivedStateOf`
- ✅ Tránh unnecessary recompositions
- ✅ LazyColumn cho lists
- ✅ State hoisting
- ✅ `LaunchedEffect` cho side effects
- ✅ `DisposableEffect` cho cleanup

### 4. Performance

- ✅ Lazy loading
- ✅ Image optimization
- ✅ Memory management
- ✅ Startup time optimization
- ✅ APK size optimization

### 5. Testing

- ✅ Unit tests cho business logic
- ✅ UI tests cho critical flows
- ✅ Integration tests
- ✅ Target: 70%+ code coverage

---

## ⚠️ LƯU Ý QUAN TRỌNG

### 1. Versions

- ✅ **Tất cả versions đã được verify** từ official Android docs
- ✅ **Không có versions giả** hoặc không tồn tại
- ✅ **Stable và production-ready**

### 2. Compatibility

- ✅ Kotlin 2.2.21 + AGP 8.13.0 (được Google test)
- ✅ Java 21 (LTS, hiệu suất tốt)
- ✅ SDK 36 (Android 16, mới nhất)
- ✅ Tất cả libraries tương thích với nhau

### 3. Deprecated APIs

- ✅ **Không dùng `kotlinOptions`** (deprecated)
- ✅ **Dùng `compilerOptions` DSL** (Kotlin 2.2+)
- ✅ **Không có warnings** trong build

### 4. Migration Notes

Khi nâng cấp từ project cũ:
- Update từng dependency một
- Test kỹ sau mỗi bước
- Đọc release notes
- Kiểm tra breaking changes

---

## 🔧 QUICK START CHECKLIST

Khi tạo app mới từ template này:

- [ ] Update `namespace` và `applicationId` trong `build.gradle.kts`
- [ ] Update package name trong code
- [ ] Tạo Application class (nếu cần)
- [ ] Setup theme (Color, Typography)
- [ ] Tạo Navigation (nếu cần)
- [ ] Thêm dependencies khi cần (Hilt, Room, Retrofit, etc.)
- [ ] Setup ProGuard rules (khi enable minify)
- [ ] Test trên nhiều devices và Android versions

---

## 📚 TÀI LIỆU THAM KHẢO

- [Android Developer Guide](https://developer.android.com/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Android Gradle Plugin Release Notes](https://developer.android.com/build/releases/gradle-plugin)
- [Material Design 3](https://m3.material.io/)

---

## ✅ TÓM TẮT

**Template này cung cấp:**
- ✅ Full Kotlin setup (100% Kotlin)
- ✅ Jetpack Compose (Kotlin-first UI)
- ✅ Versions mới nhất và đã verify
- ✅ Cấu hình tối ưu cho production
- ✅ Sẵn sàng mở rộng với nhiều chức năng
- ✅ Best practices và hướng dẫn chi tiết

**Sử dụng template này để:**
- ✅ Tạo app mới nhanh chóng
- ✅ Đảm bảo versions đúng và mới nhất
- ✅ Có cấu trúc sẵn sàng mở rộng
- ✅ Tuân thủ best practices
- ✅ Sẵn sàng cho production

---

*Template được tạo và verify: 2025*
*Versions đã được kiểm tra từ official Android Developer docs*

