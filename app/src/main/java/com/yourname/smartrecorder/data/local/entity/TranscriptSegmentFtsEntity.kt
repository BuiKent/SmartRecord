package com.yourname.smartrecorder.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * FTS4 virtual table for full-text search on transcript segments.
 * This table is automatically maintained by Room when transcript_segments is updated.
 */
@Entity(tableName = "transcript_segments_fts")
@Fts4(contentEntity = TranscriptSegmentEntity::class)
data class TranscriptSegmentFtsEntity(
    @PrimaryKey val rowid: Long,
    val recordingId: String,
    val text: String
)

