package com.yourname.smartrecorder.core.utils

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for TimeFormatter utility
 */
class TimeFormatterTest {
    
    @Test
    fun `formatTime formats seconds correctly`() {
        val result = TimeFormatter.formatTime(5000L) // 5 seconds
        
        assertEquals("Should format 5 seconds as 00:05", "00:05", result)
    }
    
    @Test
    fun `formatTime formats minutes correctly`() {
        val result = TimeFormatter.formatTime(120000L) // 2 minutes
        
        assertEquals("Should format 2 minutes as 02:00", "02:00", result)
    }
    
    @Test
    fun `formatTime formats minutes and seconds correctly`() {
        val result = TimeFormatter.formatTime(125000L) // 2 minutes 5 seconds
        
        assertEquals("Should format 2:05 as 02:05", "02:05", result)
    }
    
    @Test
    fun `formatTime formats hours correctly`() {
        val result = TimeFormatter.formatTime(3600000L) // 1 hour
        
        assertEquals("Should format 1 hour as 1:00:00", "1:00:00", result)
    }
    
    @Test
    fun `formatTime formats hours minutes and seconds correctly`() {
        val result = TimeFormatter.formatTime(3665000L) // 1 hour 1 minute 5 seconds
        
        assertEquals("Should format 1:01:05", "1:01:05", result)
    }
    
    @Test
    fun `formatTime handles zero milliseconds`() {
        val result = TimeFormatter.formatTime(0L)
        
        assertEquals("Should format 0 as 00:00", "00:00", result)
    }
    
    @Test
    fun `formatTime handles less than one second`() {
        val result = TimeFormatter.formatTime(500L) // 0.5 seconds
        
        assertEquals("Should format 0.5s as 00:00", "00:00", result)
    }
    
    @Test
    fun `formatTime handles large hours`() {
        val result = TimeFormatter.formatTime(7200000L) // 2 hours
        
        assertEquals("Should format 2 hours as 2:00:00", "2:00:00", result)
    }
    
    @Test
    fun `formatTime handles 59 minutes 59 seconds`() {
        val result = TimeFormatter.formatTime(3599000L) // 59:59
        
        assertEquals("Should format 59:59", "59:59", result)
    }
    
    @Test
    fun `formatTime handles exactly one hour`() {
        val result = TimeFormatter.formatTime(3600000L) // Exactly 1 hour
        
        assertEquals("Should format exactly 1 hour", "1:00:00", result)
    }
    
    @Test
    fun `formatTime handles 10 hours`() {
        val result = TimeFormatter.formatTime(36000000L) // 10 hours
        
        assertEquals("Should format 10 hours as 10:00:00", "10:00:00", result)
    }
    
    @Test
    fun `formatDuration is alias for formatTime`() {
        val ms = 125000L // 2:05
        
        val formatTimeResult = TimeFormatter.formatTime(ms)
        val formatDurationResult = TimeFormatter.formatDuration(ms)
        
        assertEquals("formatDuration should return same as formatTime", 
            formatTimeResult, formatDurationResult)
    }
    
    @Test
    fun `formatTime handles milliseconds rounding`() {
        // 1 second 999 milliseconds should round to 1 second
        val result = TimeFormatter.formatTime(1999L)
        
        assertEquals("Should round to 00:01", "00:01", result)
    }
    
    @Test
    fun `formatTime handles edge case 59 seconds`() {
        val result = TimeFormatter.formatTime(59000L) // 59 seconds
        
        assertEquals("Should format 59 seconds", "00:59", result)
    }
    
    @Test
    fun `formatTime handles edge case 1 minute`() {
        val result = TimeFormatter.formatTime(60000L) // 1 minute
        
        assertEquals("Should format 1 minute", "01:00", result)
    }
}

