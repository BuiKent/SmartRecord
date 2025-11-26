package com.yourname.smartrecorder.core.logging

import android.util.Log

object AppLogger {
    private const val TAG_PREFIX = "SmartRecorder"
    
    // Component tags
    const val TAG_RECORDING = "$TAG_PREFIX/Recording"
    const val TAG_AUDIO = "$TAG_PREFIX/Audio"
    const val TAG_TRANSCRIPT = "$TAG_PREFIX/Transcript"
    const val TAG_DATABASE = "$TAG_PREFIX/Database"
    const val TAG_REPOSITORY = "$TAG_PREFIX/Repository"
    const val TAG_USECASE = "$TAG_PREFIX/UseCase"
    const val TAG_VIEWMODEL = "$TAG_PREFIX/ViewModel"
    const val TAG_IMPORT = "$TAG_PREFIX/Import"
    const val TAG_REALTIME = "$TAG_PREFIX/Realtime"
    
    fun d(tag: String, message: String, vararg args: Any?) {
        Log.d(tag, formatMessage(message, *args))
    }
    
    fun i(tag: String, message: String, vararg args: Any?) {
        Log.i(tag, formatMessage(message, *args))
    }
    
    fun w(tag: String, message: String, vararg args: Any?) {
        Log.w(tag, formatMessage(message, *args))
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null, vararg args: Any?) {
        if (throwable != null) {
            Log.e(tag, formatMessage(message, *args), throwable)
        } else {
            Log.e(tag, formatMessage(message, *args))
        }
    }
    
    private fun formatMessage(message: String, vararg args: Any?): String {
        return if (args.isEmpty()) {
            message
        } else {
            try {
                String.format(message, *args)
            } catch (e: Exception) {
                "$message [Args: ${args.joinToString()}]"
            }
        }
    }
    
    // Helper methods for common logging patterns
    fun logFlow(tag: String, flowName: String, action: String, data: Any? = null) {
        d(tag, "[Flow: $flowName] $action${if (data != null) " -> $data" else ""}")
    }
    
    fun logUseCase(tag: String, useCaseName: String, action: String, params: Map<String, Any?>? = null) {
        val paramsStr = params?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: ""
        d(tag, "[UseCase: $useCaseName] $action${if (paramsStr.isNotEmpty()) " ($paramsStr)" else ""}")
    }
    
    fun logViewModel(tag: String, viewModelName: String, action: String, state: Any? = null) {
        d(tag, "[ViewModel: $viewModelName] $action${if (state != null) " -> State: $state" else ""}")
    }
    
    fun logDatabase(tag: String, operation: String, table: String, details: String? = null) {
        d(tag, "[DB: $table] $operation${if (details != null) " -> $details" else ""}")
    }
    
    fun logPerformance(tag: String, operation: String, durationMs: Long, details: String? = null) {
        i(tag, "[Performance] $operation took ${durationMs}ms${if (details != null) " ($details)" else ""}")
    }
}

