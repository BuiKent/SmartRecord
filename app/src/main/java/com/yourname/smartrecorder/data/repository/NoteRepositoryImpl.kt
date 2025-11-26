package com.yourname.smartrecorder.data.repository

import com.yourname.smartrecorder.data.local.dao.NoteDao
import com.yourname.smartrecorder.data.local.entity.NoteEntity
import com.yourname.smartrecorder.domain.model.Note
import com.yourname.smartrecorder.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao
) : NoteRepository {
    
    override fun getNotesByRecordingId(recordingId: String): Flow<List<Note>> {
        return noteDao.getNotesByRecordingId(recordingId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getNotesByType(recordingId: String, type: String): Flow<List<Note>> {
        return noteDao.getNotesByType(recordingId, type).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun insertNote(note: Note) {
        noteDao.insertNote(note.toEntity())
    }
    
    override suspend fun updateNote(note: Note) {
        noteDao.updateNote(note.toEntity())
    }
    
    override suspend fun deleteNote(note: Note) {
        noteDao.deleteNote(note.toEntity())
    }
    
    override suspend fun deleteNotesByRecordingId(recordingId: String) {
        noteDao.deleteNotesByRecordingId(recordingId)
    }
    
    private fun Note.toEntity(): NoteEntity {
        return NoteEntity(
            id = id,
            recordingId = recordingId,
            segmentId = segmentId,
            type = type,
            content = content,
            createdAt = createdAt
        )
    }
    
    private fun NoteEntity.toDomain(): Note {
        return Note(
            id = id,
            recordingId = recordingId,
            segmentId = segmentId,
            type = type,
            content = content,
            createdAt = createdAt
        )
    }
}

