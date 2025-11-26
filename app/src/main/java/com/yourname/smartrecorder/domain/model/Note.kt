package com.yourname.smartrecorder.domain.model

data class Note(
    val id: Long = 0,
    val recordingId: String,
    val segmentId: Long? = null,
    val type: String, // SUMMARY, TODO, NOTE, FLASHCARD_Q, FLASHCARD_A, BOOKMARK
    val content: String,
    val createdAt: Long
)

