package com.yourname.smartrecorder.domain.repository

import com.yourname.smartrecorder.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun getBookmarksByRecordingId(recordingId: String): Flow<List<Bookmark>>
    suspend fun getBookmarksByRecordingIdSync(recordingId: String): List<Bookmark>
    suspend fun insertBookmark(bookmark: Bookmark): Long
    suspend fun deleteBookmark(bookmark: Bookmark)
    suspend fun deleteBookmarkById(bookmarkId: Long)
    suspend fun deleteBookmarksByRecordingId(recordingId: String)
    suspend fun getBookmarksInRange(recordingId: String, startMs: Long, endMs: Long): List<Bookmark>
}

