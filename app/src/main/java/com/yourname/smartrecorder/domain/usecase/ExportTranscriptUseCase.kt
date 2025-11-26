package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.export.ExportFormatter
import com.yourname.smartrecorder.core.export.InterviewFormatter
import com.yourname.smartrecorder.core.export.LectureFormatter
import com.yourname.smartrecorder.core.export.MarkdownFormatter
import com.yourname.smartrecorder.core.export.MeetingFormatter
import com.yourname.smartrecorder.core.export.PlainTextFormatter
import com.yourname.smartrecorder.core.export.SrtFormatter
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import javax.inject.Inject

enum class ExportFormat {
    TXT, MARKDOWN, SRT, MEETING, LECTURE, INTERVIEW
}

class ExportTranscriptUseCase @Inject constructor() {
    
    fun export(
        recording: Recording,
        segments: List<TranscriptSegment>,
        format: ExportFormat
    ): String {
        val formatter: ExportFormatter = when (format) {
            ExportFormat.TXT -> PlainTextFormatter()
            ExportFormat.MARKDOWN -> MarkdownFormatter()
            ExportFormat.SRT -> SrtFormatter()
            ExportFormat.MEETING -> MeetingFormatter()
            ExportFormat.LECTURE -> LectureFormatter()
            ExportFormat.INTERVIEW -> InterviewFormatter()
        }
        
        return formatter.format(recording, segments)
    }
}

