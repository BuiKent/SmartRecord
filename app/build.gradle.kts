plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp) // KSP for Room and Hilt (replaces deprecated kapt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.yourname.smartrecorder"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.yourname.smartrecorder"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // NDK configuration for Whisper
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
        
        // External native build configuration for CMake
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }
    
    // NDK version
    ndkVersion = "25.2.9519653"
    
    // External native build
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    lint {
        // Allow NewApi warnings for API level checks (we handle them with version checks)
        warningsAsErrors = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    // Kotlin 2.2+ compilerOptions DSL (replaces deprecated kotlinOptions)
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
                "-Xjvm-default=all", // Enable JVM default methods
                "-Xannotation-default-target=param-property", // Fix @ApplicationContext annotation warnings
            )
        }
    }
    buildFeatures {
        compose = true
        // Disable BuildConfig (not needed for full Kotlin app)
        buildConfig = false
    }
    // Note: Kotlin files can be in java/ directory (Android convention)
    // This is normal and doesn't mean Java is being used
}

// Room configuration
room {
    schemaDirectory("$projectDir/schemas")
}

// KSP configuration for Room and Hilt
ksp {
    // Room incremental processing
    arg("room.incremental", "true")
    // KSP incremental processing (already enabled in gradle.properties)
    // KSP2 is automatically used with Kotlin 2.2.21+
}

dependencies {
    // Kotlin Standard Library
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    
    // AndroidX Core KTX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation Compose
    implementation(libs.navigation.compose)
    
    // ViewModel & Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    
    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    // Hilt Dependency Injection - dùng KSP (thay thế deprecated kapt)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler) // KSP for Hilt (Hilt 2.57+ supports KSP2)
    implementation(libs.hilt.navigation.compose)
    
    // Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    
    // OkHttp for model download
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}