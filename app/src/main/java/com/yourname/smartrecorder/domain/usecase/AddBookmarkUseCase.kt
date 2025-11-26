package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Bookmark
import com.yourname.smartrecorder.domain.repository.BookmarkRepository
import javax.inject.Inject

class AddBookmarkUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) {
    suspend operator fun invoke(
        recordingId: String,
        timestampMs: Long,
        note: String = ""
    ): Long {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "AddBookmarkUseCase", "Starting", 
            mapOf("recordingId" to recordingId, "timestampMs" to timestampMs, "note" to note))
        
        val bookmark = Bookmark(
            recordingId = recordingId,
            timestampMs = timestampMs,
            note = note
        )
        
        val id = bookmarkRepository.insertBookmark(bookmark)
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_USECASE, "AddBookmarkUseCase", "Completed", 
            mapOf("bookmarkId" to id, "duration" to "${duration}ms"))
        
        return id
    }
}

