package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Bookmark
import com.yourname.smartrecorder.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class GetBookmarksUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) {
    operator fun invoke(recordingId: String): Flow<List<Bookmark>> {
        AppLogger.logUseCase(TAG_USECASE, "GetBookmarksUseCase", "Invoked", 
            mapOf("recordingId" to recordingId))
        return bookmarkRepository.getBookmarksByRecordingId(recordingId)
            .onEach { bookmarks ->
                AppLogger.logUseCase(TAG_USECASE, "GetBookmarksUseCase", "Emitted", 
                    mapOf("recordingId" to recordingId, "count" to bookmarks.size))
            }
    }
}

