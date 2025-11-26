package com.yourname.smartrecorder.core.speech

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for NoiseFilter
 */
class NoiseFilterTest {
    
    @Test
    fun `filter out filler words`() {
        val tokens = listOf(
            RecognizedToken("uh", 0.8f),
            RecognizedToken("hello", 0.9f),
            RecognizedToken("um", 0.7f),
            RecognizedToken("world", 0.85f)
        )
        
        val cleaned = NoiseFilter.clean(tokens)
        
        assertEquals("Should filter out filler words", 2, cleaned.size)
        assertTrue("Should contain 'hello'", cleaned.any { it.text == "hello" })
        assertTrue("Should contain 'world'", cleaned.any { it.text == "world" })
        assertFalse("Should not contain 'uh'", cleaned.any { it.text == "uh" })
        assertFalse("Should not contain 'um'", cleaned.any { it.text == "um" })
    }
    
    @Test
    fun `filter out low confidence tokens`() {
        val tokens = listOf(
            RecognizedToken("hello", 0.4f),  // Above threshold
            RecognizedToken("world", 0.3f),  // Below threshold (0.35)
            RecognizedToken("test", 0.5f)   // Above threshold
        )
        
        val cleaned = NoiseFilter.clean(tokens)
        
        assertEquals("Should filter out low confidence", 2, cleaned.size)
        assertTrue("Should contain 'hello'", cleaned.any { it.text == "hello" })
        assertTrue("Should contain 'test'", cleaned.any { it.text == "test" })
        assertFalse("Should not contain 'world'", cleaned.any { it.text == "world" })
    }
    
    @Test
    fun `filter out very short tokens with low confidence`() {
        val tokens = listOf(
            RecognizedToken("a", 0.4f),  // Short with low confidence
            RecognizedToken("a", 0.6f),  // Short with high confidence (should keep)
            RecognizedToken("hello", 0.4f)  // Normal length
        )
        
        val cleaned = NoiseFilter.clean(tokens)
        
        assertTrue("Should filter out short low confidence", cleaned.none { it.text == "a" && it.confidence < 0.5f })
        assertTrue("Should keep short high confidence", cleaned.any { it.text == "a" && it.confidence >= 0.5f })
        assertTrue("Should keep normal tokens", cleaned.any { it.text == "hello" })
    }
    
    @Test
    fun `filter out punctuation-only tokens`() {
        val tokens = listOf(
            RecognizedToken(".", 0.8f),
            RecognizedToken("hello", 0.9f),
            RecognizedToken("!", 0.7f),
            RecognizedToken("world", 0.85f)
        )
        
        val cleaned = NoiseFilter.clean(tokens)
        
        assertEquals("Should filter out punctuation", 2, cleaned.size)
        assertTrue("Should contain 'hello'", cleaned.any { it.text == "hello" })
        assertTrue("Should contain 'world'", cleaned.any { it.text == "world" })
        assertFalse("Should not contain '.'", cleaned.any { it.text == "." })
        assertFalse("Should not contain '!'", cleaned.any { it.text == "!" })
    }
    
    @Test
    fun `remove repeated tokens`() {
        val tokens = listOf(
            RecognizedToken("hello", 0.9f),
            RecognizedToken("hello", 0.85f),  // Duplicate
            RecognizedToken("world", 0.8f),
            RecognizedToken("world", 0.75f),  // Duplicate
            RecognizedToken("test", 0.7f)
        )
        
        val cleaned = NoiseFilter.clean(tokens)
        
        assertEquals("Should remove duplicates", 3, cleaned.size)
        val texts = cleaned.map { it.text.lowercase() }
        assertEquals("Should have unique texts", 3, texts.distinct().size)
    }
    
    @Test
    fun `handle empty list`() {
        val tokens = emptyList<RecognizedToken>()
        
        val cleaned = NoiseFilter.clean(tokens)
        
        assertTrue("Should return empty list", cleaned.isEmpty())
    }
    
    @Test
    fun `handle case insensitive filtering`() {
        val tokens = listOf(
            RecognizedToken("UH", 0.8f),  // Uppercase filler
            RecognizedToken("Um", 0.7f),  // Mixed case filler
            RecognizedToken("Hello", 0.9f)
        )
        
        val cleaned = NoiseFilter.clean(tokens)
        
        assertEquals("Should filter case insensitive", 1, cleaned.size)
        assertTrue("Should contain 'Hello'", cleaned.any { it.text == "Hello" })
    }
    
    @Test
    fun `keep high confidence tokens`() {
        val tokens = listOf(
            RecognizedToken("hello", 0.9f),
            RecognizedToken("world", 0.85f),
            RecognizedToken("test", 0.8f)
        )
        
        val cleaned = NoiseFilter.clean(tokens)
        
        assertEquals("Should keep all high confidence tokens", 3, cleaned.size)
    }
    
    @Test
    fun `filter all low quality tokens`() {
        val tokens = listOf(
            RecognizedToken("uh", 0.3f),  // Filler + low confidence
            RecognizedToken(".", 0.2f),   // Punctuation
            RecognizedToken("a", 0.3f)    // Short + low confidence
        )
        
        val cleaned = NoiseFilter.clean(tokens)
        
        assertTrue("Should filter all low quality", cleaned.isEmpty())
    }
    
    @Test
    fun `preserve alternatives in filtered tokens`() {
        val tokens = listOf(
            RecognizedToken("hello", 0.9f, listOf("hi", "hey")),
            RecognizedToken("world", 0.85f, listOf("earth"))
        )
        
        val cleaned = NoiseFilter.clean(tokens)
        
        assertEquals("Should preserve tokens", 2, cleaned.size)
        val helloToken = cleaned.find { it.text == "hello" }
        assertNotNull("Should have hello token", helloToken)
        assertEquals("Should preserve alternatives", 2, helloToken?.alternatives?.size)
    }
    
    @Test
    fun `handle mixed valid and invalid tokens`() {
        val tokens = listOf(
            RecognizedToken("uh", 0.8f),  // Filler
            RecognizedToken("hello", 0.9f),  // Valid
            RecognizedToken(".", 0.7f),  // Punctuation
            RecognizedToken("world", 0.3f),  // Low confidence
            RecognizedToken("test", 0.85f)  // Valid
        )
        
        val cleaned = NoiseFilter.clean(tokens)
        
        assertEquals("Should filter mixed tokens correctly", 2, cleaned.size)
        assertTrue("Should contain 'hello'", cleaned.any { it.text == "hello" })
        assertTrue("Should contain 'test'", cleaned.any { it.text == "test" })
    }
}

