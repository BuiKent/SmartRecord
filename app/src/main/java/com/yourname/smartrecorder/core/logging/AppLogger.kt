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
    const val TAG_SERVICE = "$TAG_PREFIX/Service"
    const val TAG_LIFECYCLE = "$TAG_PREFIX/Lifecycle"
    
    // Log level prefixes to distinguish foreground vs background
    private const val PREFIX_MAIN = "[MAIN]"      // Main/foreground operations
    private const val PREFIX_BG = "[BG]"          // Background operations
    private const val PREFIX_CRITICAL = "[CRIT]"   // Critical operations
    
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
    
    // Main/foreground operations logging
    fun logMain(tag: String, message: String, vararg args: Any?) {
        i(tag, "$PREFIX_MAIN ${formatMessage(message, *args)}")
    }
    
    // Background operations logging
    fun logBackground(tag: String, message: String, vararg args: Any?) {
        d(tag, "$PREFIX_BG ${formatMessage(message, *args)}")
    }
    
    // Critical operations logging (always logged as INFO)
    fun logCritical(tag: String, message: String, vararg args: Any?) {
        i(tag, "$PREFIX_CRITICAL ${formatMessage(message, *args)}")
    }
    
    // Helper methods for common logging patterns
    fun logFlow(tag: String, flowName: String, action: String, data: Any? = null) {
        d(tag, "[Flow: $flowName] $action${if (data != null) " -> $data" else ""}")
    }
    
    fun logUseCase(tag: String, useCaseName: String, action: String, params: Map<String, Any?>? = null) {
        val paramsStr = params?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: ""
        logMain(tag, "[UseCase: $useCaseName] $action${if (paramsStr.isNotEmpty()) " ($paramsStr)" else ""}")
    }
    
    fun logViewModel(tag: String, viewModelName: String, action: String, state: Any? = null) {
        logMain(tag, "[ViewModel: $viewModelName] $action${if (state != null) " -> State: $state" else ""}")
    }
    
    fun logDatabase(tag: String, operation: String, table: String, details: String? = null) {
        logBackground(tag, "[DB: $table] $operation${if (details != null) " -> $details" else ""}")
    }
    
    fun logPerformance(tag: String, operation: String, durationMs: Long, details: String? = null) {
        i(tag, "[Performance] $operation took ${durationMs}ms${if (details != null) " ($details)" else ""}")
    }
    
    // Lifecycle logging
    fun logLifecycle(tag: String, component: String, event: String, details: String? = null) {
        logMain(tag, "[Lifecycle: $component] $event${if (details != null) " -> $details" else ""}")
    }
    
    // Service logging
    fun logService(tag: String, serviceName: String, action: String, details: String? = null) {
        logCritical(tag, "[Service: $serviceName] $action${if (details != null) " -> $details" else ""}")
    }
    
    // Rare condition/edge case logging
    fun logRareCondition(tag: String, condition: String, context: String? = null) {
        w(tag, "[RARE] $condition${if (context != null) " -> Context: $context" else ""}")
    }
    
    /**
     * Progress logger that only logs at milestones (every 20%)
     * Reduces log spam from frequent progress callbacks
     */
    class ProgressLogger(private val tag: String, private val operation: String) {
        private var lastLoggedProgress = -1
        
        @Synchronized
        fun logProgress(currentProgress: Int) {
            // Determine which milestone this progress reaches
            val milestone = when {
                currentProgress >= 100 -> 100
                currentProgress >= 80 -> 80
                currentProgress >= 60 -> 60
                currentProgress >= 40 -> 40
                currentProgress >= 20 -> 20
                currentProgress >= 0 -> 0
                else -> -1
            }
            
            // Only log if we've reached a new milestone
            if (milestone != -1 && milestone > lastLoggedProgress) {
                d(tag, "$operation progress: $milestone%%")
                lastLoggedProgress = milestone
            }
        }
    }
}

