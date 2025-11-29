package com.yourname.smartrecorder.domain.repository

import com.yourname.smartrecorder.domain.model.Recording
import kotlinx.coroutines.flow.Flow

interface RecordingRepository {
    suspend fun insertRecording(recording: Recording)
    suspend fun updateRecording(recording: Recording)
    suspend fun getRecording(id: String): Recording?
    fun getRecordingsFlow(): Flow<List<Recording>>
    fun getPinnedRecordingsFlow(): Flow<List<Recording>>
    fun getArchivedRecordingsFlow(): Flow<List<Recording>>
    fun searchRecordings(query: String): Flow<List<Recording>>
    suspend fun deleteRecording(recording: Recording)
    suspend fun updatePinnedStatus(id: String, isPinned: Boolean)
    suspend fun updateArchivedStatus(id: String, isArchived: Boolean)
    suspend fun getRecordingsByDate(startOfDay: Long, endOfDay: Long): List<Recording>
}

