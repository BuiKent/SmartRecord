package com.yourname.smartrecorder.data.repository

import com.yourname.smartrecorder.data.local.dao.RecordingDao
import com.yourname.smartrecorder.data.local.entity.RecordingEntity
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepositoryImpl @Inject constructor(
    private val recordingDao: RecordingDao
) : RecordingRepository {
    
    override suspend fun insertRecording(recording: Recording) {
        recordingDao.insertRecording(recording.toEntity())
    }
    
    override suspend fun updateRecording(recording: Recording) {
        recordingDao.updateRecording(recording.toEntity())
    }
    
    override suspend fun getRecording(id: String): Recording? {
        return recordingDao.getRecordingById(id)?.toDomain()
    }
    
    override fun getRecordingsFlow(): Flow<List<Recording>> {
        return recordingDao.getAllRecordings().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getPinnedRecordingsFlow(): Flow<List<Recording>> {
        return recordingDao.getPinnedRecordings().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getArchivedRecordingsFlow(): Flow<List<Recording>> {
        return recordingDao.getArchivedRecordings().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun searchRecordings(query: String): Flow<List<Recording>> {
        return recordingDao.searchRecordings(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun deleteRecording(recording: Recording) {
        recordingDao.deleteRecording(recording.toEntity())
    }
    
    override suspend fun updatePinnedStatus(id: String, isPinned: Boolean) {
        recordingDao.updatePinnedStatus(id, isPinned)
    }
    
    override suspend fun updateArchivedStatus(id: String, isArchived: Boolean) {
        recordingDao.updateArchivedStatus(id, isArchived)
    }
    
    private fun Recording.toEntity(): RecordingEntity {
        return RecordingEntity(
            id = id,
            title = title,
            filePath = filePath,
            createdAt = createdAt,
            durationMs = durationMs,
            mode = mode,
            isPinned = isPinned,
            isArchived = isArchived
        )
    }
    
    private fun RecordingEntity.toDomain(): Recording {
        return Recording(
            id = id,
            title = title,
            filePath = filePath,
            createdAt = createdAt,
            durationMs = durationMs,
            mode = mode,
            isPinned = isPinned,
            isArchived = isArchived
        )
    }
}

