package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import com.yourname.smartrecorder.domain.repository.TranscriptRepository
import javax.inject.Inject

class SearchTranscriptsUseCase @Inject constructor(
    private val transcriptRepository: TranscriptRepository,
    private val recordingRepository: RecordingRepository
) {
    /**
     * Search transcripts using FTS.
     * @param query Search query (will be formatted for FTS MATCH)
     * @param recordingId Optional recording ID to limit search scope
     * @return List of matching transcript segments
     */
    suspend operator fun invoke(
        query: String,
        recordingId: String? = null
    ): List<TranscriptSegment> {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "SearchTranscriptsUseCase", "Starting", 
            mapOf("query" to query, "recordingId" to recordingId))
        
        // Format query for FTS (escape special characters, add wildcards if needed)
        val formattedQuery = formatFtsQuery(query)
        
        val segments = if (recordingId != null) {
            transcriptRepository.searchTranscriptsInRecording(recordingId, formattedQuery)
        } else {
            transcriptRepository.searchTranscripts(formattedQuery)
        }
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_USECASE, "SearchTranscriptsUseCase", "Completed", 
            mapOf("query" to query, "count" to segments.size, "duration" to "${duration}ms"))
        AppLogger.logPerformance(TAG_USECASE, "SearchTranscriptsUseCase", duration, 
            "query=$query, results=${segments.size}")
        
        return segments
    }
    
    /**
     * Search recordings that contain matching transcript segments.
     */
    suspend fun searchRecordings(query: String): List<Recording> {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "SearchTranscriptsUseCase", "Searching recordings", 
            mapOf("query" to query))
        
        val formattedQuery = formatFtsQuery(query)
        val recordings = transcriptRepository.searchRecordingsByTranscript(formattedQuery)
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_USECASE, "SearchTranscriptsUseCase", "Completed", 
            mapOf("query" to query, "count" to recordings.size, "duration" to "${duration}ms"))
        
        return recordings
    }
    
    /**
     * Format query string for FTS MATCH.
     * FTS uses a special syntax: words are separated by spaces, and can use operators like * for prefix matching.
     */
    private fun formatFtsQuery(query: String): String {
        // Remove special FTS characters that might break the query
        val cleaned = query
            .replace("\"", "")
            .replace("'", "")
            .trim()
        
        // Split into words and join with space (FTS default is AND)
        // For OR search, user can use "word1 OR word2"
        // For phrase search, user can use "word1 word2"
        return cleaned.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}

