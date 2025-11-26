package com.yourname.smartrecorder.core.export

import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import java.text.SimpleDateFormat
import java.util.*

interface ExportFormatter {
    fun format(recording: Recording, segments: List<TranscriptSegment>): String
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
        
        segments.forEach { segment ->
            sb.appendLine("[${formatDuration(segment.startTimeMs)}] ${segment.text}")
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
        
        segments.forEach { segment ->
            sb.appendLine("- **[${formatDuration(segment.startTimeMs)}]** ${segment.text}")
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
        
        segments.forEachIndexed { index, segment ->
            sb.appendLine("${index + 1}")
            sb.appendLine("${formatSrtTime(segment.startTimeMs)} --> ${formatSrtTime(segment.endTimeMs)}")
            sb.appendLine(segment.text.trim())
            sb.appendLine()
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

