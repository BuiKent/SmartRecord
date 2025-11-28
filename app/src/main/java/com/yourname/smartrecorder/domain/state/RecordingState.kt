package com.yourname.smartrecorder.domain.state

/**
 * Recording state managed by RecordingSessionRepository.
 * Service is the source of truth, repository exposes state via StateFlow.
 */
sealed interface RecordingState {
    object Idle : RecordingState
    
    data class Active(
        val recordingId: String,
        val filePath: String,
        val startTimeMs: Long,
        val isPaused: Boolean = false,
        val pauseStartTimeMs: Long? = null,  // null nếu không paused
        val totalPausedDurationMs: Long = 0L  // Tổng thời gian đã pause
    ) : RecordingState {
        /**
         * Tính elapsed time (không tính pause time)
         * 
         * @return Elapsed time in milliseconds, excluding paused time
         */
        fun getElapsedMs(): Long {
            val now = System.currentTimeMillis()
            val baseElapsed = now - startTimeMs - totalPausedDurationMs
            return if (isPaused && pauseStartTimeMs != null) {
                // Đang paused: trừ thêm thời gian pause hiện tại
                baseElapsed - (now - pauseStartTimeMs)
            } else {
                baseElapsed
            }
        }
    }
}

