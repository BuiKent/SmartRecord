package com.yourname.smartrecorder.core.utils

/**
 * Shared utility for formatting time durations.
 * THỐNG NHẤT cho cả TranscriptScreen và các screen khác.
 */
object TimeFormatter {
    /**
     * Format milliseconds to MM:SS or H:MM:SS
     * 
     * @param ms Duration in milliseconds
     * @return Formatted string (e.g., "00:26", "03:31", "1:23:45")
     */
    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    /**
     * Alias for formatTime - for backward compatibility
     */
    fun formatDuration(ms: Long): String = formatTime(ms)
}

