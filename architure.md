# Technical Architecture - Version & Dependency Management

> Tài liệu này mô tả kiến trúc kỹ thuật của app, tập trung vào **version management** và **sự kết hợp các dependencies**.  
> Mục đích: Tham khảo cho các dự án Android khác muốn áp dụng cùng tech stack và version strategy.

---

## 📋 Mục lục

1. [Version Management Strategy](#version-management-strategy)
2. [Core Build Tools & Versions](#core-build-tools--versions)
3. [Dependency Compatibility Matrix](#dependency-compatibility-matrix)
4. [Plugin Configuration](#plugin-configuration)
5. [Dependency Groups & Versions](#dependency-groups--versions)
6. [Build Configuration](#build-configuration)
7. [Best Practices](#best-practices)
8. [Migration Notes](#migration-notes)

---

## Version Management Strategy

### ✅ Sử dụng Version Catalog (TOML)

App sử dụng **Version Catalog** (`gradle/libs.versions.toml`) thay vì hardcode versions trong `build.gradle.kts`.

**Lợi ích:**
- ✅ Centralized version management
- ✅ Type-safe dependency references
- ✅ Dễ maintain và update
- ✅ Tránh version conflicts

**File structure:**
```
gradle/
└── libs.versions.toml  # Single source of truth cho tất cả versions
```

---

## Core Build Tools & Versions

### Build System

| Tool | Version | Purpose |
|------|---------|---------|
| **Gradle** | `8.5` | Build system |
| **Android Gradle Plugin (AGP)** | `8.13.1` | Android build plugin |
| **Kotlin** | `2.2.21` | Programming language |
| **KSP** | `2.2.21-2.0.4` | Annotation processing (thay thế kapt) |
| **Java** | `21` | JVM target |

### Version Format Notes

**KSP Version Format:**
```
KotlinVersion-KSPVersion
2.2.21-2.0.4
```

KSP version phải tương thích với Kotlin version. Format này đảm bảo compatibility.

---

## Dependency Compatibility Matrix

### ✅ Tested & Compatible Combinations

| Component | Version | Compatible With |
|-----------|---------|-----------------|
| **Kotlin** | `2.2.21` | AGP 8.13.1, KSP 2.2.21-2.0.4 |
| **AGP** | `8.13.1` | Kotlin 2.2.21, Gradle 8.5+ |
| **Compose BOM** | `2025.10.01` | Kotlin 2.2.21, AGP 8.13.1 |
| **Hilt** | `2.57` | KSP 2.2.21-2.0.4, Kotlin 2.2.21 |
| **Room** | `2.7.0` | KSP 2.2.21-2.0.4, Kotlin 2.2.21 |
| **Navigation** | `2.9.4` | Compose BOM 2025.10.01 |

### ⚠️ Important Compatibility Rules

1. **Kotlin 2.2.21+** yêu cầu **KSP2** (không dùng kapt)
2. **Compose BOM** quản lý tất cả Compose library versions
3. **Hilt 2.57** tương thích với Kotlin 2.2.21 và KSP2
4. **Room 2.7.0+** hỗ trợ Room plugin và KSP incremental processing

---

## Plugin Configuration

### Root `build.gradle.kts`

```kotlin
plugins {
    // Core Android
    id("com.android.application") version "8.13.1" apply false
    
    // Kotlin
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    
    // Dependency Injection
    id("com.google.dagger.hilt.android") version "2.57" apply false
    
    // Annotation Processing - KSP (thay thế kapt)
    id("com.google.devtools.ksp") version "2.2.21-2.0.4" apply false
    
    // Database
    id("androidx.room") version "2.7.0" apply false
    
    // Navigation
    id("androidx.navigation.safeargs.kotlin") version "2.9.4" apply false
    
    // Firebase
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}
```

### App `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp") // KSP thay thế kapt
    id("androidx.room")
}
```

---

## Dependency Groups & Versions

### 1. Compose Ecosystem

**Version Management:** Sử dụng Compose BOM để quản lý versions

```toml
compose-bom = "2025.10.01"
```

**Dependencies:**
- `androidx.compose.ui:ui` (managed by BOM)
- `androidx.compose.material3:material3` (managed by BOM)
- `androidx.compose.ui:ui-tooling-preview` (managed by BOM)
- `androidx.compose.material:material-icons-extended` (managed by BOM)
- `androidx.compose.ui:ui-text-google-fonts` (managed by BOM)

**Usage:**
```kotlin
implementation(platform(libs.compose.bom))
implementation(libs.compose.ui)
implementation(libs.compose.material3)
```

### 2. Dependency Injection (Hilt)

**Version:** `2.57`

**Dependencies:**
```kotlin
implementation(libs.hilt.android)              // 2.57
implementation(libs.hilt.navigation.compose)   // 1.2.0
ksp(libs.hilt.compiler)                        // 2.57 (KSP, không phải kapt)
```

**Key Points:**
- ✅ Dùng **KSP** thay vì kapt cho Hilt compiler
- ✅ Hilt Navigation Compose version độc lập (`1.2.0`)
- ✅ Test dependencies: `hilt-android-testing` và `hilt-compiler-test` (cũng dùng KSP)

### 3. Database (Room)

**Version:** `2.7.0`

**Dependencies:**
```kotlin
implementation(libs.room.runtime)    // 2.7.0
implementation(libs.room.ktx)        // 2.7.0
ksp(libs.room.compiler)              // 2.7.0 (KSP, không phải kapt)
```

**Room Plugin Configuration:**
```kotlin
room {
    schemaDirectory("$projectDir/schemas")
}
```

**KSP Configuration:**
```kotlin
ksp {
    arg("room.incremental", "true")  // Incremental processing
}
```

### 4. Navigation

**Version:** `2.9.4`

**Dependencies:**
```kotlin
implementation(libs.compose.navigation)  // 2.9.4
```

**Safe Args Plugin:**
```kotlin
id("androidx.navigation.safeargs.kotlin") version "2.9.4"
```

### 5. Networking

**Versions:**
- Retrofit: `2.11.0`
- Gson: `2.11.0`
- Gson Converter: `2.11.0`

**Dependencies:**
```kotlin
implementation(libs.retrofit)                    // 2.11.0
implementation(libs.retrofit.gson.converter)     // 2.11.0
implementation(libs.gson)                        // 2.11.0
```

### 6. Image Loading

**Version:** `2.7.0`

**Dependencies:**
```kotlin
implementation(libs.coil.compose)  // 2.7.0
```

### 7. DataStore

**Version:** `1.1.2`

**Dependencies:**
```kotlin
implementation(libs.datastore.preferences)  // 1.1.2
```

### 8. Coroutines

**Version:** `1.9.0`

**Dependencies:**
```kotlin
implementation(libs.coroutines.core)      // 1.9.0
implementation(libs.coroutines.android)  // 1.9.0
```

### 9. Lifecycle

**Version:** `2.9.4`

**Dependencies:**
```kotlin
implementation(libs.lifecycle.viewmodel.savedstate)  // 2.9.4
```

### 10. AndroidX Core

**Versions:**
- Core KTX: `1.16.0`
- AppCompat: `1.7.1`
- Material: `1.13.0`
- Splash Screen: `1.0.1`

**Dependencies:**
```kotlin
implementation(libs.androidx.core.ktx)      // 1.16.0
implementation(libs.androidx.appcompat)    // 1.7.1
implementation(libs.androidx.material)     // 1.13.0
implementation(libs.core.splashscreen)     // 1.0.1
```

### 11. Firebase & Google Services

**Versions:**
- Firebase BOM: `33.9.0`
- Google Services Plugin: `4.4.4`
- Firebase Crashlytics Plugin: `3.0.2`

**Dependencies:**
```kotlin
implementation(platform(libs.firebase.bom))  // 33.9.0
implementation(libs.firebase.config)         // Managed by BOM
```

**Plugins:**
```kotlin
id("com.google.gms.google-services") version "4.4.4"
id("com.google.firebase.crashlytics") version "3.0.2"
```

### 12. AdMob & Billing

**Versions:**
- Play Services Ads: `23.0.0`
- UMP SDK: `2.2.0`
- Billing KTX: `7.0.0`

**Dependencies:**
```kotlin
implementation(libs.play.services.ads)  // 23.0.0
implementation(libs.ump.sdk)            // 2.2.0
implementation(libs.billing.ktx)        // 7.0.0
```

### 13. Testing

**Versions:**
- JUnit: `4.13.2`
- JUnit Android: `1.2.1`
- Espresso: `3.6.1`
- MockK: `1.13.12`
- Turbine: `1.1.0`
- Truth: `1.4.2`
- Arch Test Core: `2.2.0`

**Unit Test Dependencies:**
```kotlin
testImplementation(libs.junit)
testImplementation(libs.mockk)
testImplementation(libs.turbine)
testImplementation(libs.truth)
testImplementation(libs.coroutines.test)
testImplementation(libs.arch.test.core)
```

**Android Test Dependencies:**
```kotlin
androidTestImplementation(platform(libs.compose.bom))
androidTestImplementation(libs.junit.android)
androidTestImplementation(libs.espresso.core)
androidTestImplementation(libs.compose.ui.test)
androidTestImplementation(libs.compose.ui.test.junit4)
androidTestImplementation(libs.hilt.android.testing)
androidTestImplementation(libs.room.testing)
kspAndroidTest(libs.hilt.compiler.test)  // KSP cho test
```

---

## Build Configuration

### Android Configuration

```kotlin
android {
    namespace = "com.app.numerology"
    compileSdk = 36
    minSdk = 24
    targetSdk = 36
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
    
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
```

### KSP Configuration

```kotlin
ksp {
    // Room incremental processing
    arg("room.incremental", "true")
    // KSP2 is automatically used with Kotlin 2.2.21+
}
```

### Gradle Properties Optimizations

```properties
# KSP Optimizations
ksp.incremental=true
ksp.useKSP2=true

# Kotlin Optimizations
kotlin.incremental=true
kotlin.daemon.useFallbackStrategy=false

# Gradle Performance
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
```

---

## Best Practices

### 1. Version Management

✅ **DO:**
- Sử dụng Version Catalog (TOML) cho tất cả versions
- Group related versions trong `[versions]` section
- Reference versions qua `version.ref` trong `[libraries]`
- Update versions theo compatibility matrix

❌ **DON'T:**
- Hardcode versions trong `build.gradle.kts`
- Mix kapt và KSP trong cùng project
- Update versions mà không check compatibility

### 2. KSP vs Kapt

✅ **KSP (Recommended):**
- Nhanh hơn kapt
- Tương thích tốt với Kotlin 2.0+
- Incremental processing
- Type-safe

❌ **Kapt (Deprecated):**
- Chậm hơn
- Không tương thích tốt với Kotlin 2.0+
- Đang được deprecated

**Migration Path:**
```
kapt → ksp
kapt(libs.hilt.compiler) → ksp(libs.hilt.compiler)
```

### 3. Compose BOM

✅ **DO:**
- Luôn dùng Compose BOM để quản lý Compose versions
- Không specify version cho Compose libraries khi dùng BOM
- Update BOM version thay vì từng library

```kotlin
// ✅ Correct
implementation(platform(libs.compose.bom))
implementation(libs.compose.ui)  // Version managed by BOM

// ❌ Wrong
implementation("androidx.compose.ui:ui:1.7.2")  // Don't specify version
```

### 4. Plugin Versions

✅ **DO:**
- Specify plugin versions trong root `build.gradle.kts`
- Apply plugins trong app `build.gradle.kts` không cần version
- Keep plugin versions compatible với nhau

### 5. Dependency Groups

✅ **DO:**
- Group dependencies theo chức năng (Compose, DI, Database, etc.)
- Comment rõ ràng cho mỗi group
- Sử dụng consistent naming trong TOML

---

## Migration Notes

### Từ Kapt sang KSP

**Steps:**
1. Remove kapt plugin
2. Add KSP plugin với version tương thích Kotlin
3. Replace `kapt()` với `ksp()`
4. Update KSP configuration
5. Clean và rebuild

**Example:**
```kotlin
// Before (kapt)
plugins {
    id("kotlin-kapt")
}
dependencies {
    kapt(libs.hilt.compiler)
}

// After (KSP)
plugins {
    id("com.google.devtools.ksp") version "2.2.21-2.0.4"
}
dependencies {
    ksp(libs.hilt.compiler)
}
```

### Từ Hardcoded Versions sang Version Catalog

**Steps:**
1. Create `gradle/libs.versions.toml`
2. Define versions trong `[versions]`
3. Define libraries trong `[libraries]`
4. Replace hardcoded dependencies với catalog references
5. Remove version numbers từ `build.gradle.kts`

---

## Version Summary Table

| Category | Component | Version | Notes |
|----------|-----------|---------|-------|
| **Build** | Gradle | 8.5 | |
| **Build** | AGP | 8.13.1 | |
| **Language** | Kotlin | 2.2.21 | |
| **Processing** | KSP | 2.2.21-2.0.4 | Format: KotlinVersion-KSPVersion |
| **Runtime** | Java | 21 | JVM target |
| **UI** | Compose BOM | 2025.10.01 | Manages all Compose libs |
| **DI** | Hilt | 2.57 | Compatible with KSP2 |
| **Database** | Room | 2.7.0 | With Room plugin |
| **Navigation** | Navigation | 2.9.4 | |
| **Networking** | Retrofit | 2.11.0 | |
| **Image** | Coil | 2.7.0 | |
| **Storage** | DataStore | 1.1.2 | |
| **Async** | Coroutines | 1.9.0 | |
| **Lifecycle** | Lifecycle | 2.9.4 | |
| **Firebase** | Firebase BOM | 33.9.0 | |
| **Ads** | Play Services Ads | 23.0.0 | |
| **Billing** | Billing KTX | 7.0.0 | |

---

## Quick Reference

### Version Catalog Structure

```toml
[versions]
# Define all versions here
kotlin = "2.2.21"
hilt = "2.57"

[libraries]
# Reference versions via version.ref
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }

[plugins]
# Plugin versions
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### Usage in build.gradle.kts

```kotlin
// Plugins
plugins {
    alias(libs.plugins.hilt)
}

// Dependencies
dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
```

---

## Resources

- [Version Catalog Documentation](https://docs.gradle.org/current/userguide/platforms.html)
- [KSP Documentation](https://kotlinlang.org/docs/ksp-overview.html)
- [Compose BOM](https://developer.android.com/jetpack/compose/setup#compose-bom)
- [Hilt Documentation](https://dagger.dev/hilt/)
- [Room Documentation](https://developer.android.com/training/data-storage/room)

---

**Last Updated:** 2025-01-20  
**Maintained By:** Development Team

