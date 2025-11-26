package com.yourname.smartrecorder.core.speech

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for RecognizedToken data class
 */
class RecognizedTokenTest {
    
    @Test
    fun `create token with required fields`() {
        val token = RecognizedToken(
            text = "hello",
            confidence = 0.9f
        )
        
        assertEquals("Text should match", "hello", token.text)
        assertEquals("Confidence should match", 0.9f, token.confidence, 0.001f)
        assertTrue("Alternatives should be empty by default", token.alternatives.isEmpty())
    }
    
    @Test
    fun `create token with alternatives`() {
        val token = RecognizedToken(
            text = "hello",
            confidence = 0.9f,
            alternatives = listOf("hi", "hey")
        )
        
        assertEquals("Text should match", "hello", token.text)
        assertEquals("Confidence should match", 0.9f, token.confidence, 0.001f)
        assertEquals("Alternatives should match", 2, token.alternatives.size)
        assertTrue("Should contain 'hi'", token.alternatives.contains("hi"))
        assertTrue("Should contain 'hey'", token.alternatives.contains("hey"))
    }
    
    @Test
    fun `token equality`() {
        val token1 = RecognizedToken("hello", 0.9f, listOf("hi"))
        val token2 = RecognizedToken("hello", 0.9f, listOf("hi"))
        val token3 = RecognizedToken("world", 0.9f, listOf("hi"))
        
        assertEquals("Equal tokens should be equal", token1, token2)
        assertNotEquals("Different tokens should not be equal", token1, token3)
    }
    
    @Test
    fun `token copy with modified fields`() {
        val original = RecognizedToken("hello", 0.9f, listOf("hi"))
        val copied = original.copy(
            text = "world",
            confidence = 0.8f
        )
        
        assertEquals("Copied text should match", "world", copied.text)
        assertEquals("Copied confidence should match", 0.8f, copied.confidence, 0.001f)
        assertEquals("Alternatives should be preserved", 1, copied.alternatives.size)
        assertEquals("Original should be unchanged", "hello", original.text)
    }
    
    @Test
    fun `token with empty alternatives`() {
        val token = RecognizedToken("hello", 0.9f, emptyList())
        
        assertTrue("Alternatives should be empty", token.alternatives.isEmpty())
    }
    
    @Test
    fun `token with multiple alternatives`() {
        val token = RecognizedToken(
            text = "hello",
            confidence = 0.9f,
            alternatives = listOf("hi", "hey", "greetings")
        )
        
        assertEquals("Should have 3 alternatives", 3, token.alternatives.size)
    }
    
    @Test
    fun `token confidence bounds`() {
        val lowConfidence = RecognizedToken("hello", 0.0f)
        val highConfidence = RecognizedToken("hello", 1.0f)
        val midConfidence = RecognizedToken("hello", 0.5f)
        
        assertEquals("Low confidence should be 0.0", 0.0f, lowConfidence.confidence, 0.001f)
        assertEquals("High confidence should be 1.0", 1.0f, highConfidence.confidence, 0.001f)
        assertEquals("Mid confidence should be 0.5", 0.5f, midConfidence.confidence, 0.001f)
    }
    
    @Test
    fun `token with special characters in text`() {
        val token = RecognizedToken("hello-world", 0.9f)
        
        assertEquals("Should handle special characters", "hello-world", token.text)
    }
    
    @Test
    fun `token with unicode characters`() {
        val token = RecognizedToken("héllo", 0.9f)
        
        assertEquals("Should handle unicode", "héllo", token.text)
    }
}

