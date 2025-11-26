package com.yourname.smartrecorder.core.export

import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ExportFormatter implementations
 */
class ExportFormatterTest {
    
    private fun createTestRecording(): Recording {
        return Recording(
            id = "test-id",
            title = "Test Recording",
            filePath = "/test/path",
            createdAt = 1609459200000L, // 2021-01-01 00:00:00
            durationMs = 120000, // 2 minutes
            mode = "DEFAULT"
        )
    }
    
    private fun createTestSegments(): List<TranscriptSegment> {
        return listOf(
            TranscriptSegment(
                id = 1,
                recordingId = "test-id",
                startTimeMs = 0,
                endTimeMs = 5000,
                text = "First segment",
                isQuestion = false
            ),
            TranscriptSegment(
                id = 2,
                recordingId = "test-id",
                startTimeMs = 5000,
                endTimeMs = 10000,
                text = "Second segment?",
                isQuestion = true
            ),
            TranscriptSegment(
                id = 3,
                recordingId = "test-id",
                startTimeMs = 10000,
                endTimeMs = 15000,
                text = "Third segment",
                isQuestion = false
            )
        )
    }
    
    @Test
    fun `PlainTextFormatter formats correctly`() {
        val formatter = PlainTextFormatter()
        val recording = createTestRecording()
        val segments = createTestSegments()
        
        val result = formatter.format(recording, segments)
        
        assertTrue("Result should not be empty", result.isNotBlank())
        assertTrue("Should contain first segment", result.contains("First segment"))
        assertTrue("Should contain second segment", result.contains("Second segment"))
        assertTrue("Should contain third segment", result.contains("Third segment"))
    }
    
    @Test
    fun `PlainTextFormatter handles empty segments`() {
        val formatter = PlainTextFormatter()
        val recording = createTestRecording()
        val segments = emptyList<TranscriptSegment>()
        
        val result = formatter.format(recording, segments)
        
        assertNotNull("Result should not be null", result)
        assertTrue("Result should be empty or minimal", result.isBlank() || result.length < 100)
    }
    
    @Test
    fun `MarkdownFormatter formats correctly`() {
        val formatter = MarkdownFormatter()
        val recording = createTestRecording()
        val segments = createTestSegments()
        
        val result = formatter.format(recording, segments)
        
        assertTrue("Result should not be empty", result.isNotBlank())
        assertTrue("Should contain markdown title", result.contains("#"))
        assertTrue("Should contain date", result.contains("2021"))
        assertTrue("Should contain segment text", result.contains("First segment"))
    }
    
    @Test
    fun `MarkdownFormatter handles empty title`() {
        val formatter = MarkdownFormatter()
        val recording = createTestRecording().copy(title = "")
        val segments = createTestSegments()
        
        val result = formatter.format(recording, segments)
        
        assertTrue("Should handle empty title", result.contains("Untitled"))
    }
    
    @Test
    fun `SrtFormatter formats correctly`() {
        val formatter = SrtFormatter()
        val recording = createTestRecording()
        val segments = createTestSegments()
        
        val result = formatter.format(recording, segments)
        
        assertTrue("Result should not be empty", result.isNotBlank())
        assertTrue("Should contain subtitle index", result.contains("1"))
        assertTrue("Should contain time arrow", result.contains("-->"))
        assertTrue("Should contain segment text", result.contains("First segment"))
    }
    
    @Test
    fun `SrtFormatter formats time correctly`() {
        val formatter = SrtFormatter()
        val recording = createTestRecording()
        val segments = listOf(
            TranscriptSegment(
                id = 1,
                recordingId = "test-id",
                startTimeMs = 0,
                endTimeMs = 5000,
                text = "Test",
                isQuestion = false
            )
        )
        
        val result = formatter.format(recording, segments)
        
        // SRT time format: 00:00:00,000 --> 00:00:05,000
        assertTrue("Should contain SRT time format", result.contains("00:00:00"))
        assertTrue("Should contain comma in milliseconds", result.contains(","))
    }
    
    @Test
    fun `SrtFormatter increments subtitle index`() {
        val formatter = SrtFormatter()
        val recording = createTestRecording()
        val segments = createTestSegments()
        
        val result = formatter.format(recording, segments)
        
        assertTrue("Should contain index 1", result.contains("1\n"))
        assertTrue("Should contain index 2", result.contains("2\n"))
        assertTrue("Should contain index 3", result.contains("3\n"))
    }
    
    @Test
    fun `MeetingFormatter formats correctly`() {
        val formatter = MeetingFormatter()
        val recording = createTestRecording()
        val segments = createTestSegments()
        
        val result = formatter.format(recording, segments)
        
        assertTrue("Result should not be empty", result.isNotBlank())
        assertTrue("Should contain meeting title", result.contains("Meeting Notes"))
        assertTrue("Should contain action items section", result.contains("Action Items"))
        assertTrue("Should contain next steps section", result.contains("Next Steps"))
        assertTrue("Should contain segment text", result.contains("First segment"))
    }
    
    @Test
    fun `LectureFormatter formats correctly`() {
        val formatter = LectureFormatter()
        val recording = createTestRecording()
        val segments = createTestSegments()
        
        val result = formatter.format(recording, segments)
        
        assertTrue("Result should not be empty", result.isNotBlank())
        assertTrue("Should contain lecture title", result.contains("Lecture Notes"))
        assertTrue("Should contain summary section", result.contains("Summary"))
        assertTrue("Should contain key points section", result.contains("Key Points"))
        assertTrue("Should contain questions section", result.contains("Questions"))
        assertTrue("Should contain segment text", result.contains("First segment"))
    }
    
    @Test
    fun `LectureFormatter lists questions`() {
        val formatter = LectureFormatter()
        val recording = createTestRecording()
        val segments = createTestSegments()
        
        val result = formatter.format(recording, segments)
        
        // Should list questions
        assertTrue("Should contain question text", result.contains("Second segment"))
    }
    
    @Test
    fun `InterviewFormatter formats correctly`() {
        val formatter = InterviewFormatter()
        val recording = createTestRecording()
        val segments = createTestSegments()
        
        val result = formatter.format(recording, segments)
        
        assertTrue("Result should not be empty", result.isNotBlank())
        assertTrue("Should contain interview title", result.contains("Interview Transcript"))
        assertTrue("Should contain key insights section", result.contains("Key Insights"))
        assertTrue("Should contain segment text", result.contains("First segment"))
    }
    
    @Test
    fun `InterviewFormatter detects speakers`() {
        val formatter = InterviewFormatter()
        val recording = createTestRecording()
        val segments = listOf(
            TranscriptSegment(
                id = 1,
                recordingId = "test-id",
                startTimeMs = 0,
                endTimeMs = 5000,
                text = "Question?",
                isQuestion = true
            ),
            TranscriptSegment(
                id = 2,
                recordingId = "test-id",
                startTimeMs = 5000,
                endTimeMs = 10000,
                text = "Answer",
                isQuestion = false
            )
        )
        
        val result = formatter.format(recording, segments)
        
        // Should detect interviewer and interviewee
        assertTrue("Should contain interviewer or interviewee", 
            result.contains("Interviewer") || result.contains("Interviewee"))
    }
    
    @Test
    fun `all formatters handle segments with speaker info`() {
        val recording = createTestRecording()
        val segments = listOf(
            TranscriptSegment(
                id = 1,
                recordingId = "test-id",
                startTimeMs = 0,
                endTimeMs = 5000,
                text = "Speaker 1",
                isQuestion = false,
                speaker = 1
            ),
            TranscriptSegment(
                id = 2,
                recordingId = "test-id",
                startTimeMs = 5000,
                endTimeMs = 10000,
                text = "Speaker 2",
                isQuestion = false,
                speaker = 2
            )
        )
        
        val plainText = PlainTextFormatter().format(recording, segments)
        val markdown = MarkdownFormatter().format(recording, segments)
        
        assertTrue("PlainText should handle speakers", plainText.contains("Speaker"))
        assertTrue("Markdown should handle speakers", markdown.contains("Speaker"))
    }
}

