package com.yourname.smartrecorder.core.service

import android.content.Context
import android.content.SharedPreferences
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_SERVICE
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages recording state persistence to survive process death.
 * Used for auto-save and recovery.
 */
@Singleton
class RecordingStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "recording_state", Context.MODE_PRIVATE
    )
    
    private val KEY_RECORDING_ID = "recording_id"
    private val KEY_FILE_NAME = "file_name"
    private val KEY_START_TIME = "start_time"
    private val KEY_LAST_SAVE_TIME = "last_save_time"
    private val KEY_IS_RECORDING = "is_recording"
    
    fun setRecordingActive(recordingId: String, fileName: String, startTime: Long) {
        prefs.edit().apply {
            putString(KEY_RECORDING_ID, recordingId)
            putString(KEY_FILE_NAME, fileName)
            putLong(KEY_START_TIME, startTime)
            putLong(KEY_LAST_SAVE_TIME, System.currentTimeMillis())
            putBoolean(KEY_IS_RECORDING, true)
            apply()
        }
        AppLogger.logBackground(TAG_SERVICE, "Recording state saved", 
            "recordingId=$recordingId, fileName=$fileName")
    }
    
    fun updateLastSaveTime() {
        prefs.edit().putLong(KEY_LAST_SAVE_TIME, System.currentTimeMillis()).apply()
    }
    
    fun clearRecordingState() {
        prefs.edit().clear().apply()
        AppLogger.logBackground(TAG_SERVICE, "Recording state cleared")
    }
    
    fun getRecordingState(): RecordingState? {
        val isRecording = prefs.getBoolean(KEY_IS_RECORDING, false)
        if (!isRecording) return null
        
        val recordingId = prefs.getString(KEY_RECORDING_ID, null) ?: return null
        val fileName = prefs.getString(KEY_FILE_NAME, null) ?: return null
        val startTime = prefs.getLong(KEY_START_TIME, 0L)
        val lastSaveTime = prefs.getLong(KEY_LAST_SAVE_TIME, 0L)
        
        return RecordingState(recordingId, fileName, startTime, lastSaveTime)
    }
    
    data class RecordingState(
        val recordingId: String,
        val fileName: String,
        val startTime: Long,
        val lastSaveTime: Long
    )
}

