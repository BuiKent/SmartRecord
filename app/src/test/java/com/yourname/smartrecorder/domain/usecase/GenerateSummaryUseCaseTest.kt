package com.yourname.smartrecorder.domain.usecase

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for GenerateSummaryUseCase
 */
class GenerateSummaryUseCaseTest {
    
    private val useCase = GenerateSummaryUseCase()
    
    @Test
    fun `generate summary from text with sentences`() {
        val text = "This is the first sentence. This is the second sentence. This is the third sentence."
        val summary = useCase(text)
        
        assertTrue("Summary should not be empty", summary.isNotBlank())
        assertTrue("Summary should contain some sentences", summary.contains("."))
    }
    
    @Test
    fun `handle empty text`() {
        val summary = useCase("")
        
        assertEquals("Summary should be empty for empty text", "", summary)
    }
    
    @Test
    fun `handle blank text`() {
        val summary = useCase("   ")
        
        assertEquals("Summary should be empty for blank text", "", summary)
    }
    
    @Test
    fun `use first 3 sentences when no summary cues found`() {
        val text = "First sentence here. Second sentence here. Third sentence here. Fourth sentence here."
        val summary = useCase(text)
        
        val sentences = summary.split(". ").filter { it.isNotBlank() }
        assertTrue("Should use first 3 sentences", sentences.size <= 3)
    }
    
    @Test
    fun `use sentences with summary cues when found`() {
        val text = "This is a normal sentence. In conclusion, this is important. Another normal sentence. Therefore, this is also important."
        val summary = useCase(text)
        
        assertTrue("Summary should contain 'conclusion' or 'therefore'", 
            summary.lowercase().contains("conclusion") || summary.lowercase().contains("therefore"))
    }
    
    @Test
    fun `handle text with Vietnamese summary cues`() {
        val text = "Đây là câu đầu tiên. Tóm lại, đây là kết luận quan trọng. Câu thứ ba."
        val summary = useCase(text)
        
        assertTrue("Summary should contain Vietnamese cues", 
            summary.lowercase().contains("tóm") || summary.lowercase().contains("kết"))
    }
    
    @Test
    fun `handle text with multiple sentence separators`() {
        val text = "Sentence one! Sentence two? Sentence three. Sentence four."
        val summary = useCase(text)
        
        assertTrue("Summary should handle multiple separators", summary.isNotBlank())
    }
    
    @Test
    fun `summary ends with period`() {
        val text = "First sentence. Second sentence. Third sentence."
        val summary = useCase(text)
        
        if (summary.isNotEmpty()) {
            assertTrue("Summary should end with period", summary.endsWith("."))
        }
    }
    
    @Test
    fun `handle single sentence`() {
        val text = "This is a single sentence."
        val summary = useCase(text)
        
        assertTrue("Summary should handle single sentence", summary.isNotBlank())
    }
    
    @Test
    fun `handle text without sentence separators`() {
        val text = "This is text without proper sentence separators just words"
        val summary = useCase(text)
        
        // Should handle gracefully
        assertNotNull("Summary should not be null", summary)
    }
    
    @Test
    fun `prioritize summary cues over first sentences`() {
        val text = "First sentence. Second sentence. Third sentence. In conclusion, this is the most important part."
        val summary = useCase(text)
        
        assertTrue("Summary should prioritize conclusion sentence", 
            summary.lowercase().contains("conclusion"))
    }
}

