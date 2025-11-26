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
Java_com_yourname_smartrecorder_data_stt_WhisperEngine_initModel(
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
Java_com_yourname_smartrecorder_data_stt_WhisperEngine_transcribeAudio(
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
Java_com_yourname_smartrecorder_data_stt_WhisperEngine_freeModel(
    JNIEnv *env, jobject thiz, jlong modelPtr) {
    
    if (modelPtr == 0) return;
    
    struct whisper_context *ctx = reinterpret_cast<struct whisper_context*>(modelPtr);
    if (ctx != nullptr) {
        whisper_free(ctx);
    }
}

} // extern "C"

