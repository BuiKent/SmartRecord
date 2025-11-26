package com.yourname.smartrecorder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("recordingId"),
        Index("recordingId", "createdAt") // Composite for ORDER BY queries
    ]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: String,
    val question: String,
    val answer: String,
    val segmentId: Long? = null, // Optional reference to transcript segment
    val timestampMs: Long? = null, // Optional timestamp in recording
    val difficulty: Int = 0, // 0 = new, 1 = easy, 2 = medium, 3 = hard
    val lastReviewed: Long? = null,
    val reviewCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

