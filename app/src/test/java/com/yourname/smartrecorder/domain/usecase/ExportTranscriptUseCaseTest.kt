package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ExportTranscriptUseCase
 */
class ExportTranscriptUseCaseTest {
    
    private val useCase = ExportTranscriptUseCase()
    
    private fun createTestRecording(): Recording {
        return Recording(
            id = "test-id",
            title = "Test Recording",
            filePath = "/test/path",
            createdAt = System.currentTimeMillis(),
            durationMs = 60000,
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
    fun `export to TXT format`() {
        val recording = createTestRecording()
        val segments = createTestSegments()
        val result = useCase.export(recording, segments, ExportFormat.TXT)
        
        assertTrue("TXT export should not be empty", result.isNotBlank())
        assertTrue("TXT export should contain segment text", result.contains("First segment"))
    }
    
    @Test
    fun `export to MARKDOWN format`() {
        val recording = createTestRecording()
        val segments = createTestSegments()
        val result = useCase.export(recording, segments, ExportFormat.MARKDOWN)
        
        assertTrue("Markdown export should not be empty", result.isNotBlank())
        assertTrue("Markdown export should contain title", result.contains("#"))
        assertTrue("Markdown export should contain segment text", result.contains("First segment"))
    }
    
    @Test
    fun `export to SRT format`() {
        val recording = createTestRecording()
        val segments = createTestSegments()
        val result = useCase.export(recording, segments, ExportFormat.SRT)
        
        assertTrue("SRT export should not be empty", result.isNotBlank())
        assertTrue("SRT export should contain subtitle index", result.contains("1"))
        assertTrue("SRT export should contain time format", result.contains("-->"))
        assertTrue("SRT export should contain segment text", result.contains("First segment"))
    }
    
    @Test
    fun `export to MEETING format`() {
        val recording = createTestRecording()
        val segments = createTestSegments()
        val result = useCase.export(recording, segments, ExportFormat.MEETING)
        
        assertTrue("Meeting export should not be empty", result.isNotBlank())
        assertTrue("Meeting export should contain title", result.contains("Meeting"))
        assertTrue("Meeting export should contain segment text", result.contains("First segment"))
    }
    
    @Test
    fun `export to LECTURE format`() {
        val recording = createTestRecording()
        val segments = createTestSegments()
        val result = useCase.export(recording, segments, ExportFormat.LECTURE)
        
        assertTrue("Lecture export should not be empty", result.isNotBlank())
        assertTrue("Lecture export should contain title", result.contains("Lecture"))
        assertTrue("Lecture export should contain segment text", result.contains("First segment"))
    }
    
    @Test
    fun `export to INTERVIEW format`() {
        val recording = createTestRecording()
        val segments = createTestSegments()
        val result = useCase.export(recording, segments, ExportFormat.INTERVIEW)
        
        assertTrue("Interview export should not be empty", result.isNotBlank())
        assertTrue("Interview export should contain title", result.contains("Interview"))
        assertTrue("Interview export should contain segment text", result.contains("First segment"))
    }
    
    @Test
    fun `export with empty segments`() {
        val recording = createTestRecording()
        val segments = emptyList<TranscriptSegment>()
        val result = useCase.export(recording, segments, ExportFormat.TXT)
        
        assertNotNull("Export should not be null even with empty segments", result)
    }
    
    @Test
    fun `export with empty recording title`() {
        val recording = createTestRecording().copy(title = "")
        val segments = createTestSegments()
        val result = useCase.export(recording, segments, ExportFormat.MARKDOWN)
        
        assertTrue("Export should handle empty title", result.isNotBlank())
        assertTrue("Export should contain 'Untitled' or similar", 
            result.contains("Untitled") || result.contains("Recording"))
    }
    
    @Test
    fun `all formats produce different outputs`() {
        val recording = createTestRecording()
        val segments = createTestSegments()
        
        val txt = useCase.export(recording, segments, ExportFormat.TXT)
        val markdown = useCase.export(recording, segments, ExportFormat.MARKDOWN)
        val srt = useCase.export(recording, segments, ExportFormat.SRT)
        
        assertNotEquals("TXT and Markdown should be different", txt, markdown)
        assertNotEquals("TXT and SRT should be different", txt, srt)
        assertNotEquals("Markdown and SRT should be different", markdown, srt)
    }
}

