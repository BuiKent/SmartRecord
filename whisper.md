# 🎤 Whisper Models Integration Guide - Complete Implementation

> **Last Updated**: 2025-01-21  
> **Purpose**: Complete guide for integrating Whisper.cpp models into Android apps for audio transcription  
> **Target Audience**: Android developers implementing offline speech-to-text

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Setup & Dependencies](#setup--dependencies)
4. [Architecture](#architecture)
5. [Implementation Guide](#implementation-guide)
6. [Model Management](#model-management)
7. [Logging & Monitoring](#logging--monitoring)
8. [Testing](#testing)
9. [Troubleshooting](#troubleshooting)
10. [Best Practices](#best-practices)

---

## 📊 Overview

### What is Whisper?

Whisper is OpenAI's automatic speech recognition (ASR) system that provides:
- **High accuracy**: ~85-95% accuracy
- **Auto punctuation**: Output includes punctuation automatically
- **Multi-language**: Supports 100+ languages (we use English)
- **Offline**: 100% on-device processing (no internet required)
- **Privacy**: Audio never leaves device

### Why Use Whisper?

| Feature | Whisper | Server STT |
|---------|---------|------------|
| Accuracy | ⭐⭐⭐⭐⭐ (~85%) | ⭐⭐⭐⭐⭐ (~95%) |
| Offline | ✅ Yes | ❌ No |
| Privacy | ✅ Yes | ❌ No |
| Cost | ✅ Free | 💰 Paid |
| Punctuation | ✅ Auto | ✅ Auto |
| Model Size | ~75MB (tiny.en) | N/A |

### Model Selection

**Recommended: `tiny.en`**
- Size: ~75MB
- Accuracy: ~85%
- Speed: ~2-3x audio duration
- Memory: ~200-300MB RAM
- Best for: Mobile devices, English-only apps

**Other models:**
- `base.en` (~150MB): Better accuracy, slower
- `small.en` (~500MB): Too large for mobile
- `medium.en` (~1.5GB): Too large for mobile

---

## 🔧 Prerequisites

### Required Tools

1. **Android Studio** (latest version)
2. **Android NDK** (version 25.2.9519653 or newer)
3. **CMake** (3.10 or newer)
4. **Git** (to clone whisper.cpp)

### Required Knowledge

- Kotlin/Java Android development
- JNI basics (for native library integration)
- Coroutines (for async operations)
- Dependency Injection (Hilt/Dagger)

---

## 📦 Setup & Dependencies

### 1. Clone Whisper.cpp

```bash
# Clone whisper.cpp repository (in parent directory of your project)
cd /path/to/your/project
cd ..
git clone https://github.com/ggerganov/whisper.cpp.git
```

**Directory structure:**
```
your-project/
├── app/
│   └── src/
│       └── main/
│           └── cpp/
│               ├── CMakeLists.txt
│               └── whisper_jni.cpp
└── whisper.cpp/  (cloned here)
    ├── src/
    ├── include/
    └── ggml/
```

### 2. Add Dependencies

**`app/build.gradle.kts`:**

```kotlin
android {
    // ... existing config ...
    
    // NDK configuration
    ndkVersion = "25.2.9519653"
    
    defaultConfig {
        // ... existing config ...
        
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DGGML_OPENMP=OFF"  // Disable OpenMP for Android
                )
            }
        }
        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // ... existing dependencies ...
    
    // OkHttp for model download
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Hilt for dependency injection
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
}
```

### 3. Create CMakeLists.txt

**`app/src/main/cpp/CMakeLists.txt`:**

```cmake
cmake_minimum_required(VERSION 3.10)

project(whisper)

cmake_policy(SET CMP0079 NEW)
set(CMAKE_CXX_STANDARD 17)

# Path to whisper.cpp (adjust based on your directory structure)
set(WHISPER_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../../whisper.cpp)

if(NOT EXISTS "${WHISPER_LIB_DIR}")
    message(FATAL_ERROR "Whisper.cpp directory not found at: ${WHISPER_LIB_DIR}")
endif()

message(STATUS "Whisper.cpp directory: ${WHISPER_LIB_DIR}")

# Source files
set(SOURCE_FILES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_SOURCE_DIR}/whisper_jni.cpp
)

find_library(LOG_LIB log)

# Build library
add_library(whisper SHARED ${SOURCE_FILES})

target_compile_definitions(whisper PUBLIC GGML_USE_CPU)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="1.5.4")

# Optimizations
if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    target_compile_options(whisper PRIVATE -O3)
else
    target_compile_options(whisper PRIVATE -O0 -g)
endif()

# Disable OpenMP for Android
set(GGML_OPENMP OFF CACHE BOOL "ggml: use OpenMP" FORCE)

include(FetchContent)
FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

# Link libraries
target_link_libraries(whisper 
    ${LOG_LIB} 
    android 
    ggml
    c++_shared
)

# Include directories
include_directories(${WHISPER_LIB_DIR})
include_directories(${WHISPER_LIB_DIR}/src)
include_directories(${WHISPER_LIB_DIR}/include)
include_directories(${WHISPER_LIB_DIR}/ggml/include)
include_directories(${WHISPER_LIB_DIR}/ggml/src)
```

### 4. Create JNI Wrapper

**`app/src/main/cpp/whisper_jni.cpp`:**

```cpp
#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_yourpackage_data_stt_WhisperEngine_initModel(
    JNIEnv *env, jobject thiz, jstring modelPath) {
    
    if (modelPath == nullptr) {
        LOGE("Model path is null");
        return 0;
    }
    
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) {
        LOGE("Failed to get model path string");
        return 0;
    }
    
    LOGI("Loading model from: %s", path);
    
    struct whisper_context *ctx = whisper_init_from_file_with_params(
        path,
        whisper_context_default_params()
    );
    
    if (ctx == nullptr) {
        LOGE("Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }
    
    env->ReleaseStringUTFChars(modelPath, path);
    LOGI("Model loaded successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_example_yourpackage_data_stt_WhisperEngine_transcribeAudio(
    JNIEnv *env, jobject thiz, jlong modelPtr, jshortArray audioData, jint sampleRate) {
    
    if (modelPtr == 0 || audioData == nullptr) {
        LOGE("Invalid parameters");
        return env->NewStringUTF("");
    }
    
    jsize length = env->GetArrayLength(audioData);
    if (length == 0) {
        return env->NewStringUTF("");
    }
    
    jshort *samples = env->GetShortArrayElements(audioData, nullptr);
    if (samples == nullptr) {
        return env->NewStringUTF("");
    }
    
    // Convert to float array (Whisper requires float)
    std::vector<float> pcmf32(length);
    for (int i = 0; i < length; i++) {
        pcmf32[i] = samples[i] / 32768.0f;
    }
    
    env->ReleaseShortArrayElements(audioData, samples, JNI_ABORT);
    
    struct whisper_context *ctx = reinterpret_cast<struct whisper_context*>(modelPtr);
    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }
    
    // Configure Whisper parameters
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = 4;
    params.no_context = true;
    params.single_segment = false;
    
    // Run transcription
    if (whisper_full(ctx, params, pcmf32.data(), pcmf32.size()) != 0) {
        LOGE("Failed to transcribe audio");
        return env->NewStringUTF("");
    }
    
    // Build JSON result with timestamps
    int n_segments = whisper_full_n_segments(ctx);
    std::string json = "{\"segments\":[";
    
    for (int i = 0; i < n_segments; i++) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text == nullptr) continue;
        
        int64_t t0 = whisper_full_get_segment_t0(ctx, i);
        int64_t t1 = whisper_full_get_segment_t1(ctx, i);
        
        double start = t0 * 0.01;  // Convert to seconds
        double end = t1 * 0.01;
        
        // Escape JSON
        std::string escaped_text;
        for (const char *p = text; *p != '\0'; p++) {
            if (*p == '"') escaped_text += "\\\"";
            else if (*p == '\\') escaped_text += "\\\\";
            else if (*p == '\n') escaped_text += "\\n";
            else escaped_text += *p;
        }
        
        if (i > 0) json += ",";
        json += "{\"text\":\"" + escaped_text + "\",\"start\":" + 
                std::to_string(start) + ",\"end\":" + std::to_string(end) + "}";
    }
    
    json += "]}";
    return env->NewStringUTF(json.c_str());
}

JNIEXPORT void JNICALL
Java_com_example_yourpackage_data_stt_WhisperEngine_freeModel(
    JNIEnv *env, jobject thiz, jlong modelPtr) {
    
    if (modelPtr == 0) return;
    
    struct whisper_context *ctx = reinterpret_cast<struct whisper_context*>(modelPtr);
    if (ctx != nullptr) {
        whisper_free(ctx);
    }
}

} // extern "C"
```

**Important:** Replace `com_example_yourpackage` with your actual package name (use underscores instead of dots).

---

## 🏗️ Architecture

### Component Diagram

```
┌─────────────────────────────────────────┐
│         Your App                        │
│  (ViewModel/UI Layer)                  │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│      WhisperAudioTranscriber            │
│  (High-level transcription interface)   │
└──────────────┬──────────────────────────┘
               │
    ┌──────────┴──────────┐
    │                     │
┌───▼──────┐    ┌─────────▼─────────┐
│ Audio    │    │ WhisperModel      │
│ Converter│    │ Provider          │
└──────────┘    └─────────┬─────────┘
                           │
                    ┌──────▼──────┐
                    │ Whisper     │
                    │ Engine      │
                    │ (JNI)       │
                    └──────┬───────┘
                           │
                    ┌──────▼──────┐
                    │ Whisper.cpp │
                    │ (Native)    │
                    └──────┬───────┘
                           │
                    ┌──────▼──────┐
                    │ GGML Model   │
                    │ (tiny.en)    │
                    └──────────────┘
```

### Module Responsibilities

1. **WhisperModelManager**: Downloads, verifies, and caches GGML model
2. **WhisperModelProvider**: Loads and caches model instance (singleton)
3. **AudioConverter**: Converts audio formats → WAV PCM 16kHz mono
4. **WhisperEngine**: JNI wrapper to call Whisper.cpp native functions
5. **WhisperAudioTranscriber**: High-level interface integrating all components

---

## 💻 Implementation Guide

### 1. WhisperEngine (JNI Interface)

**`app/src/main/java/com/example/yourpackage/data/stt/WhisperEngine.kt`:**

```kotlin
package com.example.yourpackage.data.stt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperEngine @Inject constructor() {

    companion object {
        private const val TAG = "WhisperEngine"
        private const val WAV_HEADER_SIZE = 44
        private const val SAMPLE_RATE = 16000
        private const val INVALID_MODEL_PTR = 0L
        
        init {
            try {
                System.loadLibrary("whisper")
                android.util.Log.d(TAG, "Native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                android.util.Log.e(TAG, "Failed to load native library", e)
            }
        }
    }
    
    // JNI functions
    external fun initModel(modelPath: String): Long
    external fun transcribeAudio(modelPtr: Long, audioData: ShortArray, sampleRate: Int): String
    external fun freeModel(modelPtr: Long)
    
    data class WhisperSegment(
        val text: String,
        val start: Double,
        val end: Double
    )
    
    suspend fun transcribe(
        modelPtr: Long,
        wavFile: File,
        onProgress: (Int) -> Unit = {}
    ): List<WhisperSegment> = withContext(Dispatchers.IO) {
        
        if (modelPtr == INVALID_MODEL_PTR) {
            throw IllegalStateException("Invalid model pointer")
        }
        
        val pcmDataSize = maxOf(0, wavFile.length() - WAV_HEADER_SIZE)
        if (pcmDataSize <= 0) {
            throw IllegalStateException("Invalid WAV file")
        }
        
        val pcmData = mutableListOf<Short>()
        
        FileInputStream(wavFile).use { stream ->
            stream.skip(WAV_HEADER_SIZE.toLong())
            
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                
                val byteBuffer = ByteBuffer.wrap(buffer, 0, read)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                
                while (byteBuffer.remaining() >= 2) {
                    pcmData.add(byteBuffer.short)
                }
            }
        }
        
        val audioSamples = pcmData.toShortArray()
        onProgress(50)
        
        val jsonResult = transcribeAudio(modelPtr, audioSamples, SAMPLE_RATE)
        onProgress(100)
        
        parseWhisperJson(jsonResult)
    }
    
    suspend fun initModelFromPath(modelPath: String): Long = withContext(Dispatchers.IO) {
        val modelPtr = initModel(modelPath)
        if (modelPtr == INVALID_MODEL_PTR) {
            throw IllegalStateException("Failed to initialize model")
        }
        modelPtr
    }
    
    private fun parseWhisperJson(jsonResult: String): List<WhisperSegment> {
        try {
            val json = JSONObject(jsonResult)
            val segmentsArray = json.getJSONArray("segments")
            val segments = mutableListOf<WhisperSegment>()
            
            for (i in 0 until segmentsArray.length()) {
                val segmentObj = segmentsArray.getJSONObject(i)
                val text = segmentObj.getString("text").trim()
                val start = segmentObj.getDouble("start")
                val end = segmentObj.getDouble("end")
                
                if (text.isNotBlank()) {
                    segments.add(WhisperSegment(text, start, end))
                }
            }
            
            return segments
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to parse JSON", e)
            return listOf(WhisperSegment(jsonResult.trim(), 0.0, 0.0))
        }
    }
}
```

### 2. WhisperModelManager (Download & Cache)

**`app/src/main/java/com/example/yourpackage/data/stt/WhisperModelManager.kt`:**

```kotlin
package com.example.yourpackage.data.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperModelManager @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(600, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private val modelDir = File(context.filesDir, "whisper-models")
    private val modelName = "ggml-tiny.en.bin"
    private val modelFile = File(modelDir, modelName)
    
    companion object {
        private const val TAG = "WhisperModelManager"
        private const val MODEL_SIZE = 75L * 1024 * 1024 // 75MB
        private const val MODEL_SIZE_TOLERANCE = 0.05 // 5%
        
        private val MODEL_URLS = listOf(
            // Primary URL
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
            // Fallback URLs
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin?download=true",
            "https://github.com/ggerganov/whisper.cpp/releases/download/v1.5.4/ggml-tiny.en.bin"
        )
    }
    
    suspend fun downloadModel(
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        
        // Check if already downloaded
        if (modelFile.exists() && isModelValid(modelFile)) {
            android.util.Log.d(TAG, "Model already exists")
            onProgress(100)
            return@withContext
        }
        
        modelDir.mkdirs()
        val tempFile = File(context.cacheDir, "$modelName.tmp")
        
        // Try each URL
        for ((urlIndex, url) in MODEL_URLS.withIndex()) {
            try {
                android.util.Log.d(TAG, "Downloading from URL ${urlIndex + 1}/${MODEL_URLS.size}")
                
                val downloadedFile = downloadFile(url, tempFile) { progress ->
                    onProgress(progress)
                }
                
                // Verify model
                if (!isModelValid(downloadedFile)) {
                    throw IllegalStateException("Model verification failed")
                }
                
                // Move to final location
                if (modelFile.exists()) {
                    modelFile.delete()
                }
                downloadedFile.renameTo(modelFile)
                
                if (!modelFile.exists()) {
                    downloadedFile.copyTo(modelFile, overwrite = true)
                    downloadedFile.delete()
                }
                
                android.util.Log.d(TAG, "Model downloaded successfully")
                onProgress(100)
                return@withContext
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Download failed from URL $urlIndex", e)
                tempFile.delete()
                
                if (urlIndex == MODEL_URLS.size - 1) {
                    throw IllegalStateException("All download URLs failed", e)
                }
            }
        }
    }
    
    private suspend fun downloadFile(
        url: String,
        outputFile: File,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}")
        }
        
        val totalBytes = response.body?.contentLength() ?: 0L
        if (totalBytes <= 0) {
            throw IllegalStateException("Invalid content length")
        }
        
        outputFile.parentFile?.mkdirs()
        var downloadedBytes = 0L
        
        onProgress(0)
        
        response.body?.byteStream()?.use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    if (totalBytes > 0) {
                        val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                        onProgress(progress)
                    }
                }
            }
        }
        
        onProgress(100)
        outputFile
    }
    
    private fun isModelValid(modelFile: File): Boolean {
        if (!modelFile.exists() || !modelFile.isFile) {
            return false
        }
        
        val fileSize = modelFile.length()
        val minSize = (MODEL_SIZE * (1 - MODEL_SIZE_TOLERANCE)).toLong()
        val maxSize = (MODEL_SIZE * (1 + MODEL_SIZE_TOLERANCE)).toLong()
        
        return fileSize in minSize..maxSize
    }
    
    fun isModelDownloaded(): Boolean {
        return modelFile.exists() && isModelValid(modelFile)
    }
    
    fun getModelPath(): String = modelFile.absolutePath
    
    suspend fun deleteModel() = withContext(Dispatchers.IO) {
        if (modelFile.exists()) {
            modelFile.delete()
        }
    }
}
```

### 3. WhisperModelProvider (Model Loading)

**`app/src/main/java/com/example/yourpackage/data/stt/WhisperModelProvider.kt`:**

```kotlin
package com.example.yourpackage.data.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperModelProvider @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val modelManager: WhisperModelManager,
    private val engine: WhisperEngine
) {
    private var cachedModelPtr: Long? = null
    
    companion object {
        private const val TAG = "WhisperModelProvider"
        private const val INVALID_MODEL_PTR = 0L
    }
    
    suspend fun getModel(): Long = withContext(Dispatchers.IO) {
        // Return cached model if available
        cachedModelPtr?.let {
            if (it != INVALID_MODEL_PTR) {
                return@withContext it
            }
        }
        
        // Check if model exists
        if (!modelManager.isModelDownloaded()) {
            throw IllegalStateException(
                "Model not found. Please download first using WhisperModelManager.downloadModel()"
            )
        }
        
        // Load model
        val modelPath = modelManager.getModelPath()
        val modelPtr = engine.initModelFromPath(modelPath)
        
        if (modelPtr == INVALID_MODEL_PTR) {
            throw IllegalStateException("Failed to load model")
        }
        
        cachedModelPtr = modelPtr
        android.util.Log.d(TAG, "Model loaded successfully")
        modelPtr
    }
    
    suspend fun freeModel() = withContext(Dispatchers.IO) {
        cachedModelPtr?.let { modelPtr ->
            if (modelPtr != INVALID_MODEL_PTR) {
                engine.freeModel(modelPtr)
            }
            cachedModelPtr = null
        }
    }
    
    fun isModelReady(): Boolean {
        return modelManager.isModelDownloaded()
    }
    
    fun clearCache() {
        cachedModelPtr = null
    }
}
```

### 4. WhisperAudioTranscriber (High-level Interface)

**`app/src/main/java/com/example/yourpackage/data/stt/WhisperAudioTranscriber.kt`:**

```kotlin
package com.example.yourpackage.data.stt

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface AudioTranscriber {
    suspend fun transcribeFile(uri: Uri, onProgress: (Int) -> Unit): String
}

@Singleton
class WhisperAudioTranscriber @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val converter: AudioConverter,  // You need to implement this
    private val modelProvider: WhisperModelProvider,
    private val engine: WhisperEngine
) : AudioTranscriber {
    
    companion object {
        private const val TAG = "WhisperAudioTranscriber"
    }
    
    override suspend fun transcribeFile(
        uri: Uri,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        
        var tempFile: File? = null
        
        try {
            // Stage 1: Load model (0-10%)
            onProgress(0)
            val modelPtr = modelProvider.getModel()
            onProgress(10)
            
            // Stage 2: Convert audio to WAV (10-30%)
            tempFile = converter.convertToWav(uri) { conversionProgress ->
                onProgress(10 + (conversionProgress * 20 / 100))
            }
            onProgress(30)
            
            // Stage 3: Transcribe (30-95%)
            val segments = engine.transcribe(modelPtr, tempFile) { transcriptionProgress ->
                onProgress(30 + (transcriptionProgress * 65 / 100))
            }
            
            // Stage 4: Combine segments (95-100%)
            onProgress(95)
            val transcript = segments.joinToString(" ") { it.text }
            onProgress(100)
            
            android.util.Log.d(TAG, "Transcription completed: ${transcript.length} chars")
            transcript
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Transcription failed", e)
            throw e
        } finally {
            tempFile?.delete()
        }
    }
}
```

### 5. Dependency Injection Setup

**`app/src/main/java/com/example/yourpackage/di/AppModule.kt`:**

```kotlin
package com.example.yourpackage.di

import android.content.Context
import com.example.yourpackage.data.stt.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideWhisperModelManager(
        @ApplicationContext context: Context
    ): WhisperModelManager {
        return WhisperModelManager(context)
    }
    
    @Provides
    @Singleton
    fun provideWhisperEngine(): WhisperEngine {
        return WhisperEngine()
    }
    
    @Provides
    @Singleton
    fun provideWhisperModelProvider(
        @ApplicationContext context: Context,
        modelManager: WhisperModelManager,
        engine: WhisperEngine
    ): WhisperModelProvider {
        return WhisperModelProvider(context, modelManager, engine)
    }
    
    @Provides
    @Singleton
    fun provideWhisperAudioTranscriber(
        @ApplicationContext context: Context,
        converter: AudioConverter,  // You need to provide this
        modelProvider: WhisperModelProvider,
        engine: WhisperEngine
    ): WhisperAudioTranscriber {
        return WhisperAudioTranscriber(context, converter, modelProvider, engine)
    }
}
```

---

## 📥 Model Management

### Download Model

```kotlin
// In your ViewModel or Activity
class MainViewModel @Inject constructor(
    private val modelManager: WhisperModelManager
) : ViewModel() {
    
    fun downloadModel(onProgress: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                modelManager.downloadModel { progress ->
                    onProgress(progress)
                }
                // Model downloaded successfully
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
```

### Check Model Status

```kotlin
fun isModelReady(): Boolean {
    return modelProvider.isModelReady()
}
```

### Delete Model (for re-download)

```kotlin
suspend fun deleteModel() {
    modelManager.deleteModel()
    modelProvider.clearCache()
}
```

### Model Storage Location

- **Path**: `context.filesDir/whisper-models/ggml-tiny.en.bin`
- **Size**: ~75MB
- **Persistence**: Survives app updates
- **Verification**: File size check (75MB ± 5%)

---

## 📊 Logging & Monitoring

### Logging Setup

**Create a simple logger:**

```kotlin
object ProductionLogger {
    private const val TAG_PREFIX = "Whisper"
    
    fun d(message: String) {
        android.util.Log.d(TAG_PREFIX, message)
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        android.util.Log.e(TAG_PREFIX, message, throwable)
    }
    
    fun w(message: String) {
        android.util.Log.w(TAG_PREFIX, message)
    }
}
```

### Key Log Points

1. **Model Download:**
   - Start download
   - Progress updates (every 10%)
   - Download completion
   - Verification status

2. **Model Loading:**
   - Model initialization start
   - Model load time
   - Model ready status

3. **Transcription:**
   - Transcription start
   - Progress updates
   - Completion status
   - Error details

### View Logs

```bash
# Filter Whisper logs
adb logcat | grep -i whisper

# View all errors
adb logcat *:E

# View specific tag
adb logcat WhisperEngine:D WhisperModelManager:D WhisperAudioTranscriber:D
```

### Analytics Events (Optional)

```kotlin
// Track model download
analytics.logEvent("whisper_model_download", mapOf(
    "success" to true,
    "duration_ms" to downloadDuration,
    "url_index" to urlIndex
))

// Track transcription
analytics.logEvent("whisper_transcription", mapOf(
    "success" to true,
    "duration_ms" to transcriptionDuration,
    "audio_duration_ms" to audioDuration,
    "text_length" to transcript.length
))
```

---

## 🧪 Testing

### Unit Tests

**`app/src/test/java/com/example/yourpackage/data/stt/WhisperModelManagerTest.kt`:**

```kotlin
@Test
fun `test model validation`() {
    val validFile = File.createTempFile("test_model", ".bin")
    validFile.writeBytes(ByteArray(75 * 1024 * 1024)) // 75MB
    
    val isValid = modelManager.isModelValid(validFile)
    assertTrue(isValid)
    
    validFile.delete()
}

@Test
fun `test model path`() {
    val path = modelManager.getModelPath()
    assertTrue(path.endsWith("ggml-tiny.en.bin"))
}
```

### Integration Tests

**`app/src/androidTest/java/com/example/yourpackage/data/stt/WhisperIntegrationTest.kt`:**

```kotlin
@RunWith(AndroidJUnit4::class)
class WhisperIntegrationTest {
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var modelManager: WhisperModelManager
    
    @Inject
    lateinit var modelProvider: WhisperModelProvider
    
    @Inject
    lateinit var transcriber: WhisperAudioTranscriber
    
    @Before
    fun init() {
        hiltRule.inject()
    }
    
    @Test
    fun testModelDownload() = runTest {
        modelManager.downloadModel { progress ->
            assertTrue(progress in 0..100)
        }
        
        assertTrue(modelManager.isModelDownloaded())
    }
    
    @Test
    fun testTranscription() = runTest {
        // Ensure model is downloaded
        if (!modelManager.isModelDownloaded()) {
            modelManager.downloadModel {}
        }
        
        // Load model
        val modelPtr = modelProvider.getModel()
        assertNotEquals(0L, modelPtr)
        
        // Test transcription with sample audio file
        // (You need to provide a test audio file)
    }
}
```

### Manual Testing Checklist

1. **Model Download:**
   - [ ] Download starts correctly
   - [ ] Progress updates smoothly
   - [ ] Download completes successfully
   - [ ] Model file exists after download
   - [ ] Re-download skips if model exists

2. **Model Loading:**
   - [ ] Model loads successfully
   - [ ] Model loads from cache on second call
   - [ ] Error handling when model missing

3. **Transcription:**
   - [ ] Transcription works with WAV file
   - [ ] Progress updates correctly
   - [ ] Output includes punctuation
   - [ ] Timestamps are accurate
   - [ ] Error handling for invalid files

4. **Performance:**
   - [ ] Model load time < 5 seconds
   - [ ] Transcription time ~2-3x audio duration
   - [ ] Memory usage < 300MB

---

## 🔧 Troubleshooting

### Issue 1: Native Library Not Found

**Error:**
```
UnsatisfiedLinkError: dlopen failed: library "libwhisper.so" not found
```

**Solutions:**
1. Verify `libwhisper.so` exists in APK:
   ```bash
   unzip -l app-debug.apk | grep whisper
   ```
2. Check CMake build succeeded
3. Verify NDK version is correct
4. Clean and rebuild:
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

### Issue 2: Model Download Fails

**Error:**
```
Failed to download model from all URLs
```

**Solutions:**
1. Check internet connection
2. Verify URLs are accessible:
   ```bash
   curl -I https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin
   ```
3. Check device storage space (need > 100MB free)
4. Try fallback URLs manually

### Issue 3: Transcription Very Slow

**Symptoms:** Transcription takes > 5x audio duration

**Solutions:**
1. Normal for first run (model loading)
2. Check device performance (use newer device)
3. Consider using smaller model (tiny.en)
4. Reduce audio file length (process in chunks)

### Issue 4: App Crashes on Transcription

**Error:** App force closes during transcription

**Solutions:**
1. Check logcat for native crash:
   ```bash
   adb logcat | grep -i "fatal\|crash\|signal"
   ```
2. Verify model file integrity
3. Check memory usage (may be OOM)
4. Test with smaller audio file first

### Issue 5: CMake Build Fails

**Error:**
```
CMake Error: Whisper.cpp directory not found
```

**Solutions:**
1. Verify whisper.cpp path in CMakeLists.txt
2. Check directory structure:
   ```
   your-project/
   ├── app/
   └── whisper.cpp/  (should be here)
   ```
3. Update path in CMakeLists.txt if needed

---

## ✅ Best Practices

### 1. Model Management

- ✅ **Download once**: Check if model exists before downloading
- ✅ **Verify model**: Always verify file size after download
- ✅ **Cache model pointer**: Reuse loaded model instance
- ✅ **Handle errors gracefully**: Provide user-friendly error messages

### 2. Performance

- ✅ **Lazy loading**: Load model only when needed
- ✅ **Progress tracking**: Show progress for long operations
- ✅ **Timeout protection**: Set timeouts for transcription
- ✅ **Memory management**: Clean up temp files after use

### 3. Error Handling

- ✅ **Specific errors**: Use custom error types
- ✅ **User messages**: Convert technical errors to user-friendly messages
- ✅ **Retry logic**: Implement retry for network operations
- ✅ **Fallback options**: Provide alternative transcription methods

### 4. Logging

- ✅ **Structured logging**: Use consistent log format
- ✅ **Log levels**: Use appropriate log levels (D/E/W)
- ✅ **Performance metrics**: Log timing information
- ✅ **Error details**: Include stack traces for errors

### 5. Testing

- ✅ **Unit tests**: Test individual components
- ✅ **Integration tests**: Test full transcription flow
- ✅ **Performance tests**: Measure transcription speed
- ✅ **Error tests**: Test error handling scenarios

---

## 📚 References

### Official Resources

- **Whisper.cpp GitHub**: https://github.com/ggerganov/whisper.cpp
- **Whisper.cpp Models**: https://huggingface.co/ggerganov/whisper.cpp
- **Whisper Paper**: https://arxiv.org/abs/2212.04356

### Android Resources

- **Android NDK**: https://developer.android.com/ndk
- **JNI Guide**: https://developer.android.com/training/articles/perf-jni
- **CMake Guide**: https://developer.android.com/ndk/guides/cmake

### Model Downloads

- **Primary**: https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin
- **GitHub Releases**: https://github.com/ggerganov/whisper.cpp/releases

---

## 📝 License

This implementation guide is provided as-is. Whisper.cpp is licensed under MIT License.

---

**Last Updated**: 2025-01-21  
**Status**: ✅ Complete Implementation Guide
