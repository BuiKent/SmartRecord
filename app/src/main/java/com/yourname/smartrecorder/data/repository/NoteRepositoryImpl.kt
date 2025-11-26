package com.yourname.smartrecorder.data.repository

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_REPOSITORY
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
        AppLogger.logFlow(TAG_REPOSITORY, "getNotesByRecordingId", "Subscribed", "recordingId=$recordingId")
        return noteDao.getNotesByRecordingId(recordingId).map { entities ->
            AppLogger.logFlow(TAG_REPOSITORY, "getNotesByRecordingId", "Emitted", 
                "recordingId=$recordingId, count=${entities.size}")
            entities.map { it.toDomain() }
        }
    }
    
    override fun getNotesByType(recordingId: String, type: String): Flow<List<Note>> {
        AppLogger.logFlow(TAG_REPOSITORY, "getNotesByType", "Subscribed", "recordingId=$recordingId, type=$type")
        return noteDao.getNotesByType(recordingId, type).map { entities ->
            AppLogger.logFlow(TAG_REPOSITORY, "getNotesByType", "Emitted", 
                "recordingId=$recordingId, type=$type, count=${entities.size}")
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun insertNote(note: Note) {
        AppLogger.logDatabase(TAG_REPOSITORY, "INSERT", "notes", 
            "recordingId=${note.recordingId}, type=${note.type}")
        noteDao.insertNote(note.toEntity())
        AppLogger.logDatabase(TAG_REPOSITORY, "INSERT_COMPLETE", "notes", "id=${note.id}")
    }
    
    override suspend fun updateNote(note: Note) {
        AppLogger.logDatabase(TAG_REPOSITORY, "UPDATE", "notes", "id=${note.id}")
        noteDao.updateNote(note.toEntity())
        AppLogger.logDatabase(TAG_REPOSITORY, "UPDATE_COMPLETE", "notes", "id=${note.id}")
    }
    
    override suspend fun deleteNote(note: Note) {
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE", "notes", "id=${note.id}")
        noteDao.deleteNote(note.toEntity())
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE_COMPLETE", "notes", "id=${note.id}")
    }
    
    override suspend fun deleteNotesByRecordingId(recordingId: String) {
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE", "notes", "recordingId=$recordingId")
        noteDao.deleteNotesByRecordingId(recordingId)
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE_COMPLETE", "notes", "recordingId=$recordingId")
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

