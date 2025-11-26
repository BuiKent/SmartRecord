package com.yourname.smartrecorder.core.service

import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.core.audio.AudioPlayer
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_AUDIO
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles rare conditions and edge cases for recording and playback.
 * Prevents data loss and ensures system stability.
 */
@Singleton
class RareConditionHandler @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer
) {
    
    /**
     * Checks if recording can start safely.
     * Handles conflicts with playback or existing recording.
     */
    fun canStartRecording(): Boolean {
        // Check if playback is active
        if (audioPlayer.isPlaying()) {
            AppLogger.logRareCondition(TAG_AUDIO, 
                "Cannot start recording - playback is active")
            return false
        }
        
        // Additional checks can be added here
        return true
    }
    
    /**
     * Checks if playback can start safely.
     * Handles conflicts with recording.
     */
    fun canStartPlayback(): Boolean {
        // Check if recording is active (if AudioRecorder exposes this)
        // For now, we rely on MediaRecorder's internal state
        return true
    }
    
    /**
     * Validates audio file before operations.
     * Handles file corruption, missing files, etc.
     */
    fun validateAudioFile(file: File): ValidationResult {
        if (!file.exists()) {
            AppLogger.logRareCondition(TAG_AUDIO, 
                "Audio file does not exist", "path=${file.absolutePath}")
            return ValidationResult.Error("File does not exist")
        }
        
        if (file.length() == 0L) {
            AppLogger.logRareCondition(TAG_AUDIO, 
                "Audio file is empty", "path=${file.absolutePath}")
            return ValidationResult.Error("File is empty")
        }
        
        // Check if file is readable
        if (!file.canRead()) {
            AppLogger.logRareCondition(TAG_AUDIO, 
                "Audio file is not readable", "path=${file.absolutePath}")
            return ValidationResult.Error("File is not readable")
        }
        
        // Check file extension
        val extension = file.extension.lowercase()
        val supportedFormats = listOf("3gp", "mp3", "m4a", "wav", "aac")
        if (extension !in supportedFormats) {
            AppLogger.logRareCondition(TAG_AUDIO, 
                "Unsupported audio format", "extension=$extension, path=${file.absolutePath}")
            return ValidationResult.Warning("Unsupported format: $extension")
        }
        
        return ValidationResult.Success
    }
    
    /**
     * Handles concurrent operation attempts.
     * Prevents race conditions.
     */
    fun handleConcurrentOperation(operation: String, isAlreadyActive: Boolean): Boolean {
        if (isAlreadyActive) {
            AppLogger.logRareCondition(TAG_AUDIO, 
                "Concurrent operation attempt detected", 
                "operation=$operation, alreadyActive=$isAlreadyActive")
            return false
        }
        return true
    }
    
    /**
     * Handles file system errors.
     * Provides recovery suggestions.
     */
    fun handleFileSystemError(file: File, error: Exception): RecoveryAction {
        AppLogger.logRareCondition(TAG_AUDIO, 
            "File system error", 
            "file=${file.absolutePath}, error=${error.message}")
        
        return when {
            file.parentFile?.exists() == false -> {
                RecoveryAction.CreateDirectory(file.parentFile!!)
            }
            file.parentFile?.canWrite() == false -> {
                RecoveryAction.RequestPermission
            }
            else -> {
                RecoveryAction.Retry
            }
        }
    }
    
    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Warning(val message: String) : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
    
    sealed class RecoveryAction {
        data class CreateDirectory(val directory: File) : RecoveryAction()
        object RequestPermission : RecoveryAction()
        object Retry : RecoveryAction()
        object Abort : RecoveryAction()
    }
}

