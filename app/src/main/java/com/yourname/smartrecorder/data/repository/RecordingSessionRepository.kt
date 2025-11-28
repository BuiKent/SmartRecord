package com.yourname.smartrecorder.data.repository

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.domain.state.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for recording session state.
 * Service is the source of truth, this repository exposes state via StateFlow.
 * ViewModels observe this state to render UI.
 */
@Singleton
class RecordingSessionRepository @Inject constructor() {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()
    
    /**
     * Set recording to active state
     * Called by RecordingForegroundService when recording starts
     */
    fun setActive(
        recordingId: String,
        filePath: String,
        startTimeMs: Long = System.currentTimeMillis()
    ) {
        _state.value = RecordingState.Active(
            recordingId = recordingId,
            filePath = filePath,
            startTimeMs = startTimeMs,
            isPaused = false,
            pauseStartTimeMs = null,
            totalPausedDurationMs = 0L
        )
        AppLogger.d(TAG_VIEWMODEL, "[RecordingSessionRepository] setActive", 
            "recordingId=$recordingId, startTimeMs=$startTimeMs")
    }
    
    /**
     * Pause recording
     * Called by RecordingForegroundService when pause is requested
     */
    fun pause() {
        val current = _state.value
        if (current is RecordingState.Active && !current.isPaused) {
            _state.value = current.copy(
                isPaused = true,
                pauseStartTimeMs = System.currentTimeMillis()
            )
            AppLogger.d(TAG_VIEWMODEL, "[RecordingSessionRepository] pause", 
                "recordingId=${current.recordingId}")
        } else {
            AppLogger.logRareCondition(TAG_VIEWMODEL, 
                "Attempted to pause when not recording or already paused")
        }
    }
    
    /**
     * Resume recording
     * Called by RecordingForegroundService when resume is requested
     */
    fun resume() {
        val current = _state.value
        if (current is RecordingState.Active && current.isPaused) {
            val pauseDuration = current.pauseStartTimeMs?.let {
                System.currentTimeMillis() - it
            } ?: 0L
            
            _state.value = current.copy(
                isPaused = false,
                pauseStartTimeMs = null,
                totalPausedDurationMs = current.totalPausedDurationMs + pauseDuration
            )
            AppLogger.d(TAG_VIEWMODEL, "[RecordingSessionRepository] resume", 
                "recordingId=${current.recordingId}, pauseDuration=${pauseDuration}ms")
        } else {
            AppLogger.logRareCondition(TAG_VIEWMODEL, 
                "Attempted to resume when not recording or not paused")
        }
    }
    
    /**
     * Set recording to idle state
     * Called by RecordingForegroundService when recording stops
     */
    fun setIdle() {
        val current = _state.value
        if (current is RecordingState.Active) {
            AppLogger.d(TAG_VIEWMODEL, "[RecordingSessionRepository] setIdle", 
                "recordingId=${current.recordingId}")
        }
        _state.value = RecordingState.Idle
    }
    
    /**
     * Get current state (for testing/debugging)
     */
    fun getCurrentState(): RecordingState = _state.value
}

