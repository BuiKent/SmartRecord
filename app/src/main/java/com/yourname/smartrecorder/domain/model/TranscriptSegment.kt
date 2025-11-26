package com.yourname.smartrecorder.domain.model

data class TranscriptSegment(
    val id: Long = 0,
    val recordingId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val isQuestion: Boolean = false
)

