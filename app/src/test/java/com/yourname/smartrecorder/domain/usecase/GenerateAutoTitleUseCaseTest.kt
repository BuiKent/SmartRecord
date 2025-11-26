package com.yourname.smartrecorder.domain.usecase

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for GenerateAutoTitleUseCase
 */
class GenerateAutoTitleUseCaseTest {
    
    private val extractKeywords = ExtractKeywordsUseCase()
    private val useCase = GenerateAutoTitleUseCase(extractKeywords)
    
    @Test
    fun `generate title from text with keywords`() {
        val text = "This is a meeting about machine learning and artificial intelligence"
        val timestamp = System.currentTimeMillis()
        val title = useCase(text, timestamp)
        
        assertTrue("Title should not be empty", title.isNotBlank())
        assertTrue("Title should contain date", title.contains("202") || title.contains("2024") || title.contains("2025"))
        // Should contain some keywords
        assertTrue("Title should be meaningful", title.length > 10)
    }
    
    @Test
    fun `generate title from empty text`() {
        val timestamp = System.currentTimeMillis()
        val title = useCase("", timestamp)
        
        assertTrue("Title should start with 'Recording'", title.startsWith("Recording"))
        assertTrue("Title should contain date", title.contains("202") || title.contains("2024") || title.contains("2025"))
    }
    
    @Test
    fun `generate title from blank text`() {
        val timestamp = System.currentTimeMillis()
        val title = useCase("   ", timestamp)
        
        assertTrue("Title should start with 'Recording'", title.startsWith("Recording"))
        assertTrue("Title should contain date", title.contains("202") || title.contains("2024") || title.contains("2025"))
    }
    
    @Test
    fun `title format with keywords`() {
        val text = "machine learning artificial intelligence deep learning"
        val timestamp = System.currentTimeMillis()
        val title = useCase(text, timestamp)
        
        // Should contain keywords separated by " - "
        assertTrue("Title should contain ' - ' separator", title.contains(" - "))
        // Should end with date
        assertTrue("Title should end with date", title.contains("202") || title.contains("2024") || title.contains("2025"))
    }
    
    @Test
    fun `title format without keywords`() {
        val text = "the a an and or but"
        val timestamp = System.currentTimeMillis()
        val title = useCase(text, timestamp)
        
        // Should fallback to "Recording - date"
        assertTrue("Title should start with 'Recording'", title.startsWith("Recording"))
        assertTrue("Title should contain ' - '", title.contains(" - "))
    }
    
    @Test
    fun `title uses top 3 keywords`() {
        val text = "one two three four five six seven eight nine ten"
        val timestamp = System.currentTimeMillis()
        val title = useCase(text, timestamp)
        
        // Should use at most 3 keywords
        val parts = title.split(" - ")
        assertTrue("Should have at most 3 keywords before date", parts.size <= 2)
    }
    
    @Test
    fun `title includes date in correct format`() {
        val timestamp = System.currentTimeMillis()
        val title = useCase("test text", timestamp)
        
        // Date format should be "MMM dd, yyyy" (e.g., "Jan 15, 2024")
        val datePattern = Regex("""[A-Za-z]{3}\s+\d{1,2},\s+\d{4}""")
        assertTrue("Title should contain date in MMM dd, yyyy format", 
            datePattern.containsMatchIn(title))
    }
}

