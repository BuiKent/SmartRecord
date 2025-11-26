package com.yourname.smartrecorder.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yourname.smartrecorder.data.local.dao.NoteDao
import com.yourname.smartrecorder.data.local.dao.RecordingDao
import com.yourname.smartrecorder.data.local.dao.TranscriptDao
import com.yourname.smartrecorder.data.local.entity.NoteEntity
import com.yourname.smartrecorder.data.local.entity.RecordingEntity
import com.yourname.smartrecorder.data.local.entity.RecordingTagCrossRef
import com.yourname.smartrecorder.data.local.entity.TagEntity
import com.yourname.smartrecorder.data.local.entity.TranscriptSegmentEntity

@Database(
    entities = [
        RecordingEntity::class,
        TranscriptSegmentEntity::class,
        NoteEntity::class,
        TagEntity::class,
        RecordingTagCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SmartRecorderDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun noteDao(): NoteDao
    
    companion object {
        const val DATABASE_NAME = "smart_recorder_db"
    }
}

