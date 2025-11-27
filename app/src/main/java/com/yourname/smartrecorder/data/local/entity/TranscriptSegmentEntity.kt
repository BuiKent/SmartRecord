package com.yourname.smartrecorder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcript_segments",
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
        Index("recordingId", "startTimeMs"), // Composite for ORDER BY queries
        Index("isQuestion") // For question filtering
    ]
)
data class TranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val isQuestion: Boolean = false,
    val speaker: Int? = null  // Speaker number (1, 2, 3, ...), null if not processed yet
)

