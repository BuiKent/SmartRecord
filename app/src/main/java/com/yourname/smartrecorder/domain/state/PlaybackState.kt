package com.yourname.smartrecorder.domain.state

/**
 * Playback state managed by PlaybackSessionRepository.
 * Service is the source of truth, repository exposes state via StateFlow.
 */
sealed interface PlaybackState {
    object Idle : PlaybackState
    
    data class Playing(
        val recordingId: String,
        val positionMs: Long,
        val durationMs: Long,
        val isLooping: Boolean = false
    ) : PlaybackState
    
    data class Paused(
        val recordingId: String,
        val positionMs: Long,
        val durationMs: Long,
        val isLooping: Boolean = false
    ) : PlaybackState
}

