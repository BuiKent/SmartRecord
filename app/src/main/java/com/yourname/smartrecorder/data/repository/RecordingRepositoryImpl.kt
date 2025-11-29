package com.yourname.smartrecorder.data.repository

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_REPOSITORY
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
        val startTime = System.currentTimeMillis()
        AppLogger.logDatabase(TAG_REPOSITORY, "INSERT", "recordings", 
            "id=${recording.id}, title=${recording.title}, duration=${recording.durationMs}ms")
        
        recordingDao.insertRecording(recording.toEntity())
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logDatabase(TAG_REPOSITORY, "INSERT_COMPLETE", "recordings", 
            "id=${recording.id}, duration=${duration}ms")
    }
    
    override suspend fun updateRecording(recording: Recording) {
        AppLogger.logDatabase(TAG_REPOSITORY, "UPDATE", "recordings", "id=${recording.id}")
        recordingDao.updateRecording(recording.toEntity())
        AppLogger.logDatabase(TAG_REPOSITORY, "UPDATE_COMPLETE", "recordings", "id=${recording.id}")
    }
    
    override suspend fun getRecording(id: String): Recording? {
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT", "recordings", "id=$id")
        val result = recordingDao.getRecordingById(id)?.toDomain()
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT_COMPLETE", "recordings", 
            "id=$id, found=${result != null}")
        return result
    }
    
    override fun getRecordingsFlow(): Flow<List<Recording>> {
        AppLogger.logFlow(TAG_REPOSITORY, "getRecordingsFlow", "Subscribed")
        return recordingDao.getAllRecordings().map { entities ->
            AppLogger.logFlow(TAG_REPOSITORY, "getRecordingsFlow", "Emitted", 
                "count=${entities.size}")
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
        AppLogger.logFlow(TAG_REPOSITORY, "searchRecordings", "Subscribed", "query=$query")
        return recordingDao.searchRecordings(query).map { entities ->
            AppLogger.logFlow(TAG_REPOSITORY, "searchRecordings", "Emitted", 
                "query=$query, count=${entities.size}")
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun deleteRecording(recording: Recording) {
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE", "recordings", "id=${recording.id}")
        recordingDao.deleteRecording(recording.toEntity())
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE_COMPLETE", "recordings", "id=${recording.id}")
    }
    
    override suspend fun updatePinnedStatus(id: String, isPinned: Boolean) {
        AppLogger.logDatabase(TAG_REPOSITORY, "UPDATE", "recordings", "id=$id, isPinned=$isPinned")
        recordingDao.updatePinnedStatus(id, isPinned)
        AppLogger.logDatabase(TAG_REPOSITORY, "UPDATE_COMPLETE", "recordings", "id=$id")
    }
    
    override suspend fun updateArchivedStatus(id: String, isArchived: Boolean) {
        AppLogger.logDatabase(TAG_REPOSITORY, "UPDATE", "recordings", "id=$id, isArchived=$isArchived")
        recordingDao.updateArchivedStatus(id, isArchived)
        AppLogger.logDatabase(TAG_REPOSITORY, "UPDATE_COMPLETE", "recordings", "id=$id")
    }
    
    override suspend fun getRecordingsByDate(startOfDay: Long, endOfDay: Long): List<Recording> {
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT", "recordings", "startOfDay=$startOfDay, endOfDay=$endOfDay")
        val entities = recordingDao.getRecordingsByDate(startOfDay, endOfDay)
        val recordings = entities.map { it.toDomain() }
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT_COMPLETE", "recordings", "count=${recordings.size}")
        return recordings
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

