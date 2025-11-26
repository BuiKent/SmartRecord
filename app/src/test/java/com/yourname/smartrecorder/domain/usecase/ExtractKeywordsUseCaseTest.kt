package com.yourname.smartrecorder.domain.usecase

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ExtractKeywordsUseCase
 */
class ExtractKeywordsUseCaseTest {
    
    private val useCase = ExtractKeywordsUseCase()
    
    @Test
    fun `extract keywords from simple text`() {
        val text = "This is a test about machine learning and artificial intelligence"
        val keywords = useCase(text, topN = 5)
        
        assertTrue("Should extract keywords", keywords.isNotEmpty())
        assertTrue("Should contain 'machine'", keywords.contains("machine") || keywords.contains("learning"))
        assertTrue("Should contain 'artificial' or 'intelligence'", 
            keywords.contains("artificial") || keywords.contains("intelligence"))
    }
    
    @Test
    fun `filter out stopwords`() {
        val text = "the a an and or but in on at to for of with by from"
        val keywords = useCase(text, topN = 10)
        
        // Should filter out common stopwords
        assertFalse("Should not contain 'the'", keywords.contains("the"))
        assertFalse("Should not contain 'a'", keywords.contains("a"))
        assertFalse("Should not contain 'an'", keywords.contains("an"))
        assertFalse("Should not contain 'and'", keywords.contains("and"))
    }
    
    @Test
    fun `filter out short words`() {
        val text = "a an it is we us me my"
        val keywords = useCase(text, topN = 10)
        
        // Should filter out words with length <= 3
        assertTrue("Should filter out short words", keywords.isEmpty() || 
            keywords.all { it.length > 3 })
    }
    
    @Test
    fun `return top N keywords by frequency`() {
        val text = "machine learning machine learning artificial intelligence artificial intelligence deep learning"
        val keywords = useCase(text, topN = 3)
        
        assertEquals("Should return top 3 keywords", 3, keywords.size)
        // "machine" and "learning" should be in top keywords
        assertTrue("Should contain frequent words", 
            keywords.contains("machine") || keywords.contains("learning"))
    }
    
    @Test
    fun `handle empty text`() {
        val keywords = useCase("", topN = 10)
        
        assertTrue("Should return empty list for empty text", keywords.isEmpty())
    }
    
    @Test
    fun `handle blank text`() {
        val keywords = useCase("   ", topN = 10)
        
        assertTrue("Should return empty list for blank text", keywords.isEmpty())
    }
    
    @Test
    fun `handle text with only stopwords`() {
        val text = "the a an and or but in on at"
        val keywords = useCase(text, topN = 10)
        
        assertTrue("Should return empty list for only stopwords", keywords.isEmpty())
    }
    
    @Test
    fun `handle text with special characters`() {
        val text = "Hello, world! This is a test. How are you? I'm fine."
        val keywords = useCase(text, topN = 10)
        
        assertTrue("Should handle special characters", keywords.isNotEmpty())
        // Should not contain special characters
        keywords.forEach { keyword ->
            assertTrue("Keyword should only contain letters", keyword.all { it.isLetter() })
        }
    }
    
    @Test
    fun `case insensitive extraction`() {
        val text = "MACHINE LEARNING Machine Learning machine learning"
        val keywords = useCase(text, topN = 5)
        
        // Should treat all as same word
        val lowerKeywords = keywords.map { it.lowercase() }
        assertTrue("Should be case insensitive", 
            lowerKeywords.contains("machine") || lowerKeywords.contains("learning"))
    }
    
    @Test
    fun `respect topN parameter`() {
        val text = "one two three four five six seven eight nine ten eleven twelve"
        val keywords5 = useCase(text, topN = 5)
        val keywords10 = useCase(text, topN = 10)
        
        assertTrue("Should respect topN=5", keywords5.size <= 5)
        assertTrue("Should respect topN=10", keywords10.size <= 10)
        assertTrue("topN=10 should have more or equal keywords than topN=5", 
            keywords10.size >= keywords5.size)
    }
}

