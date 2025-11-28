package com.yourname.smartrecorder.data.repository

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.domain.state.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for playback session state.
 * Service is the source of truth, this repository exposes state via StateFlow.
 * ViewModels observe this state to render UI.
 */
@Singleton
class PlaybackSessionRepository @Inject constructor() {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    
    /**
     * Start playback
     * Called by PlaybackForegroundService when playback starts
     */
    fun setPlaying(
        recordingId: String,
        positionMs: Long = 0L,
        durationMs: Long,
        isLooping: Boolean = false
    ) {
        _state.value = PlaybackState.Playing(
            recordingId = recordingId,
            positionMs = positionMs,
            durationMs = durationMs,
            isLooping = isLooping
        )
        AppLogger.d(TAG_VIEWMODEL, "[PlaybackSessionRepository] setPlaying", 
            "recordingId=$recordingId, positionMs=$positionMs, durationMs=$durationMs")
    }
    
    /**
     * Update playback position
     * Called by PlaybackForegroundService during playback
     */
    fun updatePosition(positionMs: Long) {
        val current = _state.value
        when (current) {
            is PlaybackState.Playing -> {
                _state.value = current.copy(positionMs = positionMs)
            }
            is PlaybackState.Paused -> {
                _state.value = current.copy(positionMs = positionMs)
            }
            is PlaybackState.Idle -> {
                // Ignore position update if not playing
            }
        }
    }
    
    /**
     * Pause playback
     * Called by PlaybackForegroundService when pause is requested
     */
    fun pause() {
        val current = _state.value
        if (current is PlaybackState.Playing) {
            _state.value = PlaybackState.Paused(
                recordingId = current.recordingId,
                positionMs = current.positionMs,
                durationMs = current.durationMs,
                isLooping = current.isLooping
            )
            AppLogger.d(TAG_VIEWMODEL, "[PlaybackSessionRepository] pause", 
                "recordingId=${current.recordingId}, positionMs=${current.positionMs}")
        } else {
            AppLogger.logRareCondition(TAG_VIEWMODEL, 
                "Attempted to pause when not playing")
        }
    }
    
    /**
     * Resume playback
     * Called by PlaybackForegroundService when resume is requested
     */
    fun resume() {
        val current = _state.value
        if (current is PlaybackState.Paused) {
            _state.value = PlaybackState.Playing(
                recordingId = current.recordingId,
                positionMs = current.positionMs,
                durationMs = current.durationMs,
                isLooping = current.isLooping
            )
            AppLogger.d(TAG_VIEWMODEL, "[PlaybackSessionRepository] resume", 
                "recordingId=${current.recordingId}, positionMs=${current.positionMs}")
        } else {
            AppLogger.logRareCondition(TAG_VIEWMODEL, 
                "Attempted to resume when not paused")
        }
    }
    
    /**
     * Stop playback
     * Called by PlaybackForegroundService when playback stops
     */
    fun setIdle() {
        val current = _state.value
        if (current !is PlaybackState.Idle) {
            AppLogger.d(TAG_VIEWMODEL, "[PlaybackSessionRepository] setIdle", 
                "previousState=${current.javaClass.simpleName}")
        }
        _state.value = PlaybackState.Idle
    }
    
    /**
     * Update looping state
     */
    fun setLooping(isLooping: Boolean) {
        val current = _state.value
        when (current) {
            is PlaybackState.Playing -> {
                _state.value = current.copy(isLooping = isLooping)
                AppLogger.d(TAG_VIEWMODEL, "[PlaybackSessionRepository] setLooping", 
                    "recordingId=${current.recordingId}, isLooping=$isLooping")
            }
            is PlaybackState.Paused -> {
                _state.value = current.copy(isLooping = isLooping)
                AppLogger.d(TAG_VIEWMODEL, "[PlaybackSessionRepository] setLooping", 
                    "recordingId=${current.recordingId}, isLooping=$isLooping")
            }
            is PlaybackState.Idle -> {
                // Ignore if not playing
            }
        }
    }
    
    /**
     * Get current state (for testing/debugging)
     */
    fun getCurrentState(): PlaybackState = _state.value
}

