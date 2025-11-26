package com.yourname.smartrecorder.core.service

import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_SERVICE
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-save manager to periodically save recording progress.
 * Prevents data loss if app is killed unexpectedly.
 * 
 * Trade-offs:
 * - PRO: Prevents data loss
 * - PRO: Allows recovery after crash
 * - CON: Additional I/O operations (minimal impact with proper intervals)
 * - CON: Slightly more complex state management
 * 
 * Strategy: Save metadata every 30 seconds, actual audio file is written continuously by MediaRecorder
 */
@Singleton
class AutoSaveManager @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val recordingStateManager: RecordingStateManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoSaveJob: Job? = null
    private var currentRecording: Recording? = null
    private var recordingStartTime: Long = 0L
    
    companion object {
        private const val AUTO_SAVE_INTERVAL_MS = 30_000L // 30 seconds
        private const val MIN_RECORDING_DURATION_FOR_SAVE = 5_000L // 5 seconds minimum
    }
    
    fun startAutoSave(recording: Recording, startTime: Long) {
        stopAutoSave() // Stop any existing auto-save
        
        currentRecording = recording
        recordingStartTime = startTime
        
        AppLogger.logBackground(TAG_SERVICE, "Auto-save started", 
            "recordingId=${recording.id}, interval=${AUTO_SAVE_INTERVAL_MS}ms")
        
        autoSaveJob = scope.launch {
            while (currentRecording != null) {
                delay(AUTO_SAVE_INTERVAL_MS)
                
                val recording = currentRecording ?: break
                val duration = System.currentTimeMillis() - recordingStartTime
                
                // Only save if recording has been active for minimum duration
                if (duration < MIN_RECORDING_DURATION_FOR_SAVE) {
                    AppLogger.logBackground(TAG_SERVICE, "Skipping auto-save - duration too short", 
                        "duration=${duration}ms")
                    continue
                }
                
                try {
                    // Check if audio file exists and has content
                    val audioFile = File(recording.filePath)
                    if (!audioFile.exists() || audioFile.length() == 0L) {
                        AppLogger.logRareCondition(TAG_SERVICE, 
                            "Auto-save skipped - audio file missing or empty", 
                            "file=${audioFile.absolutePath}, exists=${audioFile.exists()}, size=${audioFile.length()}")
                        continue
                    }
                    
                    // Update recording with current duration
                    val updatedRecording = recording.copy(durationMs = duration)
                    
                    // Save to database (upsert - insert or update)
                    recordingRepository.insertRecording(updatedRecording)
                    recordingStateManager.updateLastSaveTime()
                    
                    AppLogger.logBackground(TAG_SERVICE, "Auto-save completed", 
                        "recordingId=${recording.id}, duration=${duration}ms, fileSize=${audioFile.length()}bytes")
                } catch (e: Exception) {
                    AppLogger.e(TAG_SERVICE, "Auto-save failed", e)
                    // Continue auto-save even if one save fails
                }
            }
        }
    }
    
    fun stopAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        currentRecording = null
        recordingStartTime = 0L
        AppLogger.logBackground(TAG_SERVICE, "Auto-save stopped")
    }
    
    /**
     * Force immediate save (e.g., before app goes to background)
     */
    suspend fun forceSaveNow() {
        val recording = currentRecording ?: return
        val duration = System.currentTimeMillis() - recordingStartTime
        
        if (duration < MIN_RECORDING_DURATION_FOR_SAVE) {
            AppLogger.logBackground(TAG_SERVICE, "Force save skipped - duration too short", 
                "duration=${duration}ms")
            return
        }
        
        try {
            val audioFile = File(recording.filePath)
            if (!audioFile.exists() || audioFile.length() == 0L) {
                AppLogger.logRareCondition(TAG_SERVICE, 
                    "Force save skipped - audio file missing or empty", 
                    "file=${audioFile.absolutePath}")
                return
            }
            
            val updatedRecording = recording.copy(durationMs = duration)
            recordingRepository.insertRecording(updatedRecording)
            recordingStateManager.updateLastSaveTime()
            
            AppLogger.logCritical(TAG_SERVICE, "Force save completed", 
                "recordingId=${recording.id}, duration=${duration}ms")
        } catch (e: Exception) {
            AppLogger.e(TAG_SERVICE, "Force save failed", e)
            throw e
        }
    }
    
    fun cleanup() {
        stopAutoSave()
        scope.cancel()
    }
}

