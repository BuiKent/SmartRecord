package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.repository.RecordingRepository
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

class StopRecordingAndSaveUseCase @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val recordingRepository: RecordingRepository,
    private val generateAutoTitle: GenerateAutoTitleUseCase
) {
    suspend operator fun invoke(recording: Recording, durationMs: Long): Recording {
        val startTime = System.currentTimeMillis()
        AppLogger.logUseCase(TAG_USECASE, "StopRecordingAndSaveUseCase", "Starting", 
            mapOf("recordingId" to recording.id, "durationMs" to durationMs))
        
        val file = audioRecorder.stopRecording()
        val fileSize = file.length()
        AppLogger.d(TAG_USECASE, "Audio file saved -> size: %d bytes, path: %s", fileSize, file.absolutePath)
        
        // Generate auto title if empty
        val title = if (recording.title.isBlank()) {
            generateSequentialTitle(recording.createdAt)
        } else {
            recording.title
        }
        
        val finalRecording = recording.copy(
            filePath = file.absolutePath,
            durationMs = durationMs,
            title = title
        )
        
        AppLogger.d(TAG_USECASE, "Saving recording to database -> id: %s, title: %s, duration: %d ms", 
            finalRecording.id, finalRecording.title, finalRecording.durationMs)
        
        recordingRepository.insertRecording(finalRecording)
        
        val duration = System.currentTimeMillis() - startTime
        AppLogger.logUseCase(TAG_USECASE, "StopRecordingAndSaveUseCase", "Completed", 
            mapOf("recordingId" to finalRecording.id, "duration" to "${duration}ms"))
        AppLogger.logPerformance(TAG_USECASE, "StopRecordingAndSaveUseCase", duration, 
            "fileSize=${fileSize}bytes, durationMs=${durationMs}ms")
        
        return finalRecording
    }
    
    /**
     * Generate sequential title: Record-001-29.11.2025, Record-002-29.11.2025, ...
     * Format: Record-{sequence}-{date}
     * Date format follows system locale (dd.MM.yyyy or MM.dd.yyyy)
     */
    private suspend fun generateSequentialTitle(createdAt: Long): String {
        // Get start and end of day for the recording date
        val calendar = Calendar.getInstance().apply {
            timeInMillis = createdAt
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis
        
        // Get all recordings created on the same day
        val recordingsToday = recordingRepository.getRecordingsByDate(startOfDay, endOfDay)
        
        // Extract sequence numbers from titles matching pattern "Record-XXX-..."
        val sequenceNumbers = recordingsToday.mapNotNull { rec ->
            val title = rec.title
            if (title.startsWith("Record-")) {
                val parts = title.split("-")
                if (parts.size >= 2) {
                    parts[1].toIntOrNull()
                } else {
                    null
                }
            } else {
                null
            }
        }
        
        // Find next sequence number
        val nextSequence = if (sequenceNumbers.isEmpty()) {
            1
        } else {
            (sequenceNumbers.maxOrNull() ?: 0) + 1
        }
        
        // Format date according to system locale (dd.MM.yyyy or MM.dd.yyyy)
        val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(java.util.Date(createdAt))
        
        val title = "Record-%03d-%s".format(nextSequence, dateStr)
        AppLogger.d(TAG_USECASE, "Generated sequential title: %s", title)
        return title
    }
}

