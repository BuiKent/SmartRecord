package com.yourname.smartrecorder.domain.model

data class Recording(
    val id: String,
    val title: String,
    val filePath: String,
    val createdAt: Long,
    val durationMs: Long,
    val mode: String, // MEETING, LECTURE, STUDY, IMPORTED, DEFAULT
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)

