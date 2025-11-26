package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.BookmarkRepository
import com.yourname.smartrecorder.domain.repository.FlashcardRepository
import com.yourname.smartrecorder.domain.repository.NoteRepository
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import com.yourname.smartrecorder.domain.repository.TranscriptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class DeleteRecordingUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val transcriptRepository: TranscriptRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val noteRepository: NoteRepository,
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(recording: Recording) = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "DeleteRecordingUseCase", "Starting", 
            mapOf("recordingId" to recording.id, "title" to recording.title))
        
        try {
            // 1. Delete audio file from filesystem
            val audioFile = File(recording.filePath)
            if (audioFile.exists()) {
                val deleted = audioFile.delete()
                AppLogger.d(TAG_USECASE, "Audio file deletion -> path: %s, success: %b", 
                    recording.filePath, deleted)
                if (!deleted) {
                    AppLogger.w(TAG_USECASE, "Failed to delete audio file -> path: %s", recording.filePath)
                }
            } else {
                AppLogger.w(TAG_USECASE, "Audio file not found -> path: %s", recording.filePath)
            }
            
            // 2. Delete all related data from database
            transcriptRepository.deleteSegmentsByRecordingId(recording.id)
            bookmarkRepository.deleteBookmarksByRecordingId(recording.id)
            noteRepository.deleteNotesByRecordingId(recording.id)
            flashcardRepository.deleteFlashcardsByRecordingId(recording.id)
            
            AppLogger.d(TAG_USECASE, "Related data deleted -> recordingId: %s", recording.id)
            
            // 3. Delete recording from database (should be last to maintain referential integrity)
            recordingRepository.deleteRecording(recording)
            
            val duration = System.currentTimeMillis() - startTime
            AppLogger.logUseCase(TAG_USECASE, "DeleteRecordingUseCase", "Completed", 
                mapOf("recordingId" to recording.id, "duration" to "${duration}ms"))
            AppLogger.logPerformance(TAG_USECASE, "DeleteRecordingUseCase", duration)
        } catch (e: Exception) {
            AppLogger.e(TAG_USECASE, "Failed to delete recording -> recordingId: %s", e, recording.id)
            throw e
        }
    }
}

