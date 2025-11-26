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
}

