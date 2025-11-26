package com.yourname.smartrecorder.domain.repository

import com.yourname.smartrecorder.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getNotesByRecordingId(recordingId: String): Flow<List<Note>>
    fun getNotesByType(recordingId: String, type: String): Flow<List<Note>>
    suspend fun insertNote(note: Note)
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(note: Note)
    suspend fun deleteNotesByRecordingId(recordingId: String)
}

