package com.yourname.smartrecorder.core.export

import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import java.text.SimpleDateFormat
import java.util.*

enum class TemplateType {
    MEETING, LECTURE, INTERVIEW
}

/**
 * Template-based formatters for different use cases.
 */
class MeetingFormatter : ExportFormatter {
    override fun format(recording: Recording, segments: List<TranscriptSegment>): String {
        val sb = StringBuilder()
        sb.appendLine("# Meeting Notes")
        sb.appendLine()
        sb.appendLine("**Title:** ${recording.title.ifBlank { "Untitled Meeting" }}")
        sb.appendLine("**Date:** ${formatDate(recording.createdAt)}")
        sb.appendLine("**Duration:** ${formatDuration(recording.durationMs)}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
        
        // Detect speakers
        val segmentsWithSpeakers = detectSpeakersForExport(segments)
        
        var prevSpeaker: Int? = null
        segmentsWithSpeakers.forEach { segment ->
            if (segment.speaker != null && segment.speaker != prevSpeaker) {
                if (prevSpeaker != null) {
                    sb.appendLine()
                }
                sb.appendLine("## Participant ${segment.speaker}")
                sb.appendLine()
            }
            sb.appendLine("- ${segment.text.trim()}")
            prevSpeaker = segment.speaker
        }
        
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("## Action Items")
        sb.appendLine()
        sb.appendLine("_Add action items here_")
        sb.appendLine()
        sb.appendLine("## Next Steps")
        sb.appendLine()
        sb.appendLine("_Add next steps here_")
        
        return sb.toString()
    }
}

class LectureFormatter : ExportFormatter {
    override fun format(recording: Recording, segments: List<TranscriptSegment>): String {
        val sb = StringBuilder()
        sb.appendLine("# Lecture Notes")
        sb.appendLine()
        sb.appendLine("**Subject:** ${recording.title.ifBlank { "Untitled Lecture" }}")
        sb.appendLine("**Date:** ${formatDate(recording.createdAt)}")
        sb.appendLine("**Duration:** ${formatDuration(recording.durationMs)}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("## Summary")
        sb.appendLine()
        sb.appendLine("_Add lecture summary here_")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("## Transcript")
        sb.appendLine()
        
        segments.forEach { segment ->
            sb.appendLine("**[${formatDuration(segment.startTimeMs)}]** ${segment.text.trim()}")
            sb.appendLine()
        }
        
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("## Key Points")
        sb.appendLine()
        sb.appendLine("_Add key points here_")
        sb.appendLine()
        sb.appendLine("## Questions")
        sb.appendLine()
        segments.filter { it.isQuestion }.forEach { segment ->
            sb.appendLine("- ${segment.text.trim()}")
        }
        if (segments.none { it.isQuestion }) {
            sb.appendLine("_No questions found_")
        }
        
        return sb.toString()
    }
}

class InterviewFormatter : ExportFormatter {
    override fun format(recording: Recording, segments: List<TranscriptSegment>): String {
        val sb = StringBuilder()
        sb.appendLine("# Interview Transcript")
        sb.appendLine()
        sb.appendLine("**Interviewee:** ${recording.title.ifBlank { "Unknown" }}")
        sb.appendLine("**Date:** ${formatDate(recording.createdAt)}")
        sb.appendLine("**Duration:** ${formatDuration(recording.durationMs)}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
        
        // Detect speakers (Interviewer vs Interviewee)
        val segmentsWithSpeakers = detectSpeakersForExport(segments)
        
        var prevSpeaker: Int? = null
        segmentsWithSpeakers.forEach { segment ->
            if (segment.speaker != null && segment.speaker != prevSpeaker) {
                if (prevSpeaker != null) {
                    sb.appendLine()
                }
                val speakerLabel = if (segment.speaker == 1) "Interviewer" else "Interviewee"
                sb.appendLine("**$speakerLabel:**")
            }
            sb.appendLine(segment.text.trim())
            prevSpeaker = segment.speaker
        }
        
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("## Key Insights")
        sb.appendLine()
        sb.appendLine("_Add key insights here_")
        
        return sb.toString()
    }
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

// Reuse speaker detection from ExportFormatter.kt
private fun detectSpeakersForExport(segments: List<TranscriptSegment>): List<TranscriptSegment> {
    if (segments.isEmpty()) return segments
    
    val speakerAssignments = mutableListOf<Int>()
    var currentSpeaker = 1
    var lastEndTime = 0L
    
    segments.forEachIndexed { index, segment ->
        val isQuestion = segment.isQuestion || segment.text.trim().endsWith("?")
        val prevSegment = segments.getOrNull(index - 1)
        val prevIsQuestion = prevSegment?.isQuestion ?: false
        
        val silenceGap = if (index > 0) {
            (segment.startTimeMs - lastEndTime) / 1000.0
        } else {
            0.0
        }
        val isLongPause = silenceGap > 1.5
        
        var shouldChangeSpeaker = false
        
        if (isQuestion) {
            shouldChangeSpeaker = true
        } else if (isLongPause && !prevIsQuestion) {
            shouldChangeSpeaker = true
        } else if (prevIsQuestion && !isQuestion) {
            shouldChangeSpeaker = true
        }
        
        if (shouldChangeSpeaker && index > 0) {
            currentSpeaker = if (currentSpeaker == 1) 2 else 1
        }
        
        speakerAssignments.add(currentSpeaker)
        lastEndTime = segment.endTimeMs
    }
    
    val uniqueSpeakers = speakerAssignments.distinct()
    val hasMultipleSpeakers = uniqueSpeakers.size > 1
    
    return if (hasMultipleSpeakers) {
        segments.mapIndexed { index, segment ->
            segment.copy(speaker = speakerAssignments[index])
        }
    } else {
        segments
    }
}

