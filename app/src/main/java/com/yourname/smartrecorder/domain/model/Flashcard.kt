package com.yourname.smartrecorder.domain.model

data class Flashcard(
    val id: Long = 0,
    val recordingId: String,
    val question: String,
    val answer: String,
    val segmentId: Long? = null,
    val timestampMs: Long? = null,
    val difficulty: Int = 0, // 0 = new, 1 = easy, 2 = medium, 3 = hard
    val lastReviewed: Long? = null,
    val reviewCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

