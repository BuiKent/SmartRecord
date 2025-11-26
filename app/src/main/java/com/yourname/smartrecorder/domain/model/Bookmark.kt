package com.yourname.smartrecorder.domain.model

data class Bookmark(
    val id: Long = 0,
    val recordingId: String,
    val timestampMs: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

