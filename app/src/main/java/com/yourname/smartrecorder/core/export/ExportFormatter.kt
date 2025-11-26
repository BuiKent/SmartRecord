package com.yourname.smartrecorder.core.export

import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import java.text.SimpleDateFormat
import java.util.*

interface ExportFormatter {
    fun format(recording: Recording, segments: List<TranscriptSegment>): String
}

/**
 * Detect speakers in transcript segments for export.
 * Uses question-based and time-gap heuristics.
 */
private fun detectSpeakersForExport(segments: List<TranscriptSegment>): List<TranscriptSegment> {
    if (segments.isEmpty()) return segments
    
    val speakerAssignments = mutableListOf<Int>()
    var currentSpeaker = 1
    var lastEndTime = 0L
    
    segments.forEachIndexed { index, segment ->
        val isQuestion = segment.isQuestion || segment.text.trim().endsWith("?")
        val prevSegment = segments.getOrNull(index - 1)
        val prevIsQuestion = prevSegment?.isQuestion ?: false
        
        // Calculate time gap (silence) in seconds
        val silenceGap = if (index > 0) {
            (segment.startTimeMs - lastEndTime) / 1000.0
        } else {
            0.0
        }
        val isLongPause = silenceGap > 1.5
        
        // Logic: Priority 1 = question mark, Priority 2 = time gap
        var shouldChangeSpeaker = false
        
        if (isQuestion) {
            // Priority 1: Question → change speaker
            shouldChangeSpeaker = true
        } else if (isLongPause && !prevIsQuestion) {
            // Priority 2: Time gap > 1.5s (only if not after question)
            shouldChangeSpeaker = true
        } else if (prevIsQuestion && !isQuestion) {
            // After question, next sentence is not question → back to speaker 1
            shouldChangeSpeaker = true
        }
        
        // Change speaker if needed
        if (shouldChangeSpeaker && index > 0) {
            currentSpeaker = if (currentSpeaker == 1) 2 else 1
        }
        
        speakerAssignments.add(currentSpeaker)
        lastEndTime = segment.endTimeMs
    }
    
    // Check if we have multiple speakers
    val uniqueSpeakers = speakerAssignments.distinct()
    val hasMultipleSpeakers = uniqueSpeakers.size > 1
    
    // Return segments with speaker info (only if multiple speakers detected)
    return if (hasMultipleSpeakers) {
        segments.mapIndexed { index, segment ->
            segment.copy(speaker = speakerAssignments[index])
        }
    } else {
        // Single speaker - no speaker labels needed
        segments
    }
}

class PlainTextFormatter : ExportFormatter {
    override fun format(recording: Recording, segments: List<TranscriptSegment>): String {
        val sb = StringBuilder()
        sb.appendLine(recording.title.ifBlank { "Untitled Recording" })
        sb.appendLine(formatDate(recording.createdAt))
        sb.appendLine("Duration: ${formatDuration(recording.durationMs)}")
        sb.appendLine()
        sb.appendLine("--- Transcript ---")
        sb.appendLine()
        
        // Detect speakers if not already detected
        val segmentsWithSpeakers = if (segments.any { it.speaker != null }) {
            segments  // Already has speaker info
        } else {
            detectSpeakersForExport(segments)  // Calculate speakers
        }
        
        var prevSpeaker: Int? = null
        segmentsWithSpeakers.forEach { segment ->
            // Add speaker label if speaker changed
            if (segment.speaker != null && segment.speaker != prevSpeaker) {
                if (prevSpeaker != null) {
                    sb.appendLine() // Add blank line between speakers
                }
                sb.append("[Speaker ${segment.speaker}]: ")
            }
            sb.appendLine("[${formatDuration(segment.startTimeMs)}] ${segment.text}")
            prevSpeaker = segment.speaker
        }
        
        return sb.toString()
    }
    
    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%d:%02d".format(m, s)
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

class MarkdownFormatter : ExportFormatter {
    override fun format(recording: Recording, segments: List<TranscriptSegment>): String {
        val sb = StringBuilder()
        sb.appendLine("# ${recording.title.ifBlank { "Untitled Recording" }}")
        sb.appendLine()
        sb.appendLine("**Date:** ${formatDate(recording.createdAt)}")
        sb.appendLine("**Duration:** ${formatDuration(recording.durationMs)}")
        sb.appendLine()
        sb.appendLine("## Transcript")
        sb.appendLine()
        
        // Detect speakers if not already detected
        val segmentsWithSpeakers = if (segments.any { it.speaker != null }) {
            segments  // Already has speaker info
        } else {
            detectSpeakersForExport(segments)  // Calculate speakers
        }
        
        var prevSpeaker: Int? = null
        segmentsWithSpeakers.forEach { segment ->
            // Add speaker label if speaker changed
            if (segment.speaker != null && segment.speaker != prevSpeaker) {
                if (prevSpeaker != null) {
                    sb.appendLine() // Add blank line between speakers
                }
                sb.append("### Speaker ${segment.speaker}\n\n")
            }
            sb.appendLine("- **[${formatDuration(segment.startTimeMs)}]** ${segment.text}")
            prevSpeaker = segment.speaker
        }
        
        return sb.toString()
    }
    
    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%d:%02d".format(m, s)
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

class SrtFormatter : ExportFormatter {
    override fun format(recording: Recording, segments: List<TranscriptSegment>): String {
        val sb = StringBuilder()
        
        // Detect speakers if not already detected
        val segmentsWithSpeakers = if (segments.any { it.speaker != null }) {
            segments  // Already has speaker info
        } else {
            detectSpeakersForExport(segments)  // Calculate speakers
        }
        
        var subtitleIndex = 1
        var prevSpeaker: Int? = null
        segmentsWithSpeakers.forEach { segment ->
            sb.appendLine("$subtitleIndex")
            sb.appendLine("${formatSrtTime(segment.startTimeMs)} --> ${formatSrtTime(segment.endTimeMs)}")
            // Add speaker label in SRT if speaker changed
            val text = if (segment.speaker != null && segment.speaker != prevSpeaker) {
                "[Speaker ${segment.speaker}] ${segment.text.trim()}"
            } else {
                segment.text.trim()
            }
            sb.appendLine(text)
            sb.appendLine()
            subtitleIndex++
            prevSpeaker = segment.speaker
        }
        
        return sb.toString()
    }
    
    private fun formatSrtTime(ms: Long): String {
        val totalMs = ms
        val hours = totalMs / 3600000
        val minutes = (totalMs % 3600000) / 60000
        val seconds = (totalMs % 60000) / 1000
        val millis = totalMs % 1000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }
}

