package com.yourname.smartrecorder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val filePath: String,
    val createdAt: Long,
    val durationMs: Long,
    val mode: String, // MEETING, LECTURE, STUDY, IMPORTED, DEFAULT
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)

