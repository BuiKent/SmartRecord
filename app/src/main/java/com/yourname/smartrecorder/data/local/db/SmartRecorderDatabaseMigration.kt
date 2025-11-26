package com.yourname.smartrecorder.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SmartRecorderDatabaseMigration {
    
    /**
     * Migration from version 1 to 2: Add Bookmarks table
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS bookmarks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    recordingId TEXT NOT NULL,
                    timestampMs INTEGER NOT NULL,
                    note TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(recordingId) REFERENCES recordings(id) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_recordingId ON bookmarks(recordingId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_recordingId_timestampMs ON bookmarks(recordingId, timestampMs)")
        }
    }
    
    /**
     * Migration from version 2 to 3: Add Flashcards table and FTS virtual table
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create flashcards table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS flashcards (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    recordingId TEXT NOT NULL,
                    question TEXT NOT NULL,
                    answer TEXT NOT NULL,
                    segmentId INTEGER,
                    timestampMs INTEGER,
                    difficulty INTEGER NOT NULL,
                    lastReviewed INTEGER,
                    reviewCount INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(recordingId) REFERENCES recordings(id) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_flashcards_recordingId ON flashcards(recordingId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_flashcards_recordingId_createdAt ON flashcards(recordingId, createdAt)")
            
            // Create FTS4 virtual table for transcript segments
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS transcript_segments_fts USING fts4(
                    content='transcript_segments',
                    rowid,
                    recordingId,
                    text
                )
            """)
            
            // Populate FTS table with existing data
            db.execSQL("""
                INSERT INTO transcript_segments_fts(rowid, recordingId, text)
                SELECT id, recordingId, text FROM transcript_segments
            """)
        }
    }
    
    /**
     * Migration from version 1 to 3: Combined migration for new installations
     */
    val MIGRATION_1_3 = object : Migration(1, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Apply migration 1->2
            MIGRATION_1_2.migrate(db)
            // Apply migration 2->3
            MIGRATION_2_3.migrate(db)
        }
    }
}

