package com.yourname.smartrecorder.data.repository

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_REPOSITORY
import com.yourname.smartrecorder.data.local.dao.BookmarkDao
import com.yourname.smartrecorder.data.local.entity.BookmarkEntity
import com.yourname.smartrecorder.domain.model.Bookmark
import com.yourname.smartrecorder.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {
    
    override fun getBookmarksByRecordingId(recordingId: String): Flow<List<Bookmark>> {
        AppLogger.logFlow(TAG_REPOSITORY, "getBookmarksByRecordingId", "Subscribed", "recordingId=$recordingId")
        return bookmarkDao.getBookmarksByRecordingId(recordingId).map { entities ->
            AppLogger.logFlow(TAG_REPOSITORY, "getBookmarksByRecordingId", "Emitted", 
                "recordingId=$recordingId, count=${entities.size}")
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getBookmarksByRecordingIdSync(recordingId: String): List<Bookmark> {
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT", "bookmarks", "recordingId=$recordingId (sync)")
        val result = bookmarkDao.getBookmarksByRecordingIdSync(recordingId).map { it.toDomain() }
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT_COMPLETE", "bookmarks", 
            "recordingId=$recordingId, count=${result.size}")
        return result
    }
    
    override suspend fun insertBookmark(bookmark: Bookmark): Long {
        AppLogger.logDatabase(TAG_REPOSITORY, "INSERT", "bookmarks", 
            "recordingId=${bookmark.recordingId}, timestamp=${bookmark.timestampMs}ms")
        val id = bookmarkDao.insertBookmark(bookmark.toEntity())
        AppLogger.logDatabase(TAG_REPOSITORY, "INSERT_COMPLETE", "bookmarks", "id=$id")
        return id
    }
    
    override suspend fun deleteBookmark(bookmark: Bookmark) {
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE", "bookmarks", "id=${bookmark.id}")
        bookmarkDao.deleteBookmark(bookmark.toEntity())
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE_COMPLETE", "bookmarks", "id=${bookmark.id}")
    }
    
    override suspend fun deleteBookmarkById(bookmarkId: Long) {
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE", "bookmarks", "id=$bookmarkId")
        bookmarkDao.deleteBookmarkById(bookmarkId)
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE_COMPLETE", "bookmarks", "id=$bookmarkId")
    }
    
    override suspend fun deleteBookmarksByRecordingId(recordingId: String) {
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE", "bookmarks", "recordingId=$recordingId")
        bookmarkDao.deleteBookmarksByRecordingId(recordingId)
        AppLogger.logDatabase(TAG_REPOSITORY, "DELETE_COMPLETE", "bookmarks", "recordingId=$recordingId")
    }
    
    override suspend fun getBookmarksInRange(recordingId: String, startMs: Long, endMs: Long): List<Bookmark> {
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT", "bookmarks", 
            "recordingId=$recordingId, range=${startMs}ms-${endMs}ms")
        val result = bookmarkDao.getBookmarksInRange(recordingId, startMs, endMs).map { it.toDomain() }
        AppLogger.logDatabase(TAG_REPOSITORY, "SELECT_COMPLETE", "bookmarks", 
            "recordingId=$recordingId, count=${result.size}")
        return result
    }
    
    private fun Bookmark.toEntity(): BookmarkEntity {
        return BookmarkEntity(
            id = id,
            recordingId = recordingId,
            timestampMs = timestampMs,
            note = note,
            createdAt = createdAt
        )
    }
    
    private fun BookmarkEntity.toDomain(): Bookmark {
        return Bookmark(
            id = id,
            recordingId = recordingId,
            timestampMs = timestampMs,
            note = note,
            createdAt = createdAt
        )
    }
}

