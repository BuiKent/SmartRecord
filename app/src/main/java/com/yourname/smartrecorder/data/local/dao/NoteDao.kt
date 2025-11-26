package com.yourname.smartrecorder.data.local.dao

import androidx.room.*
import com.yourname.smartrecorder.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE recordingId = :recordingId ORDER BY createdAt DESC")
    fun getNotesByRecordingId(recordingId: String): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes WHERE recordingId = :recordingId AND type = :type ORDER BY createdAt DESC")
    fun getNotesByType(recordingId: String, type: String): Flow<List<NoteEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)
    
    @Update
    suspend fun updateNote(note: NoteEntity)
    
    @Delete
    suspend fun deleteNote(note: NoteEntity)
    
    @Query("DELETE FROM notes WHERE recordingId = :recordingId")
    suspend fun deleteNotesByRecordingId(recordingId: String)
}

