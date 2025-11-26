package com.yourname.smartrecorder.domain.repository

import com.yourname.smartrecorder.domain.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow

interface TranscriptRepository {
    suspend fun saveTranscriptSegments(
        recordingId: String,
        segments: List<TranscriptSegment>
    )
    fun getTranscriptSegments(recordingId: String): Flow<List<TranscriptSegment>>
    suspend fun getTranscriptSegmentsSync(recordingId: String): List<TranscriptSegment>
    suspend fun deleteSegmentsByRecordingId(recordingId: String)
    fun getQuestions(recordingId: String): Flow<List<TranscriptSegment>>
    
    // FTS search methods
    suspend fun searchTranscripts(query: String): List<TranscriptSegment>
    suspend fun searchTranscriptsInRecording(recordingId: String, query: String): List<TranscriptSegment>
    suspend fun searchRecordingsByTranscript(query: String): List<com.yourname.smartrecorder.domain.model.Recording>
}

