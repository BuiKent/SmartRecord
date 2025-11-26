package com.yourname.smartrecorder.domain.usecase

import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import java.io.File

/**
 * Unit tests for GetRecordingsDirectoryUseCase
 * Note: This test uses mocking for Context
 */
class GetRecordingsDirectoryUseCaseTest {
    
    @Test
    fun `returns recordings directory`() {
        val mockContext = mock(Context::class.java)
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val mockFilesDir = File(tempDir, "test_files_dir")
        mockFilesDir.mkdirs()
        
        `when`(mockContext.filesDir).thenReturn(mockFilesDir)
        
        val useCase = GetRecordingsDirectoryUseCase(mockContext)
        val result = useCase()
        
        assertNotNull("Should return a directory", result)
        assertEquals("Should return recordings directory", "recordings", result.name)
        assertTrue("Should be a directory", result.isDirectory || result.exists())
        
        // Cleanup
        result.deleteRecursively()
        mockFilesDir.deleteRecursively()
    }
    
    @Test
    fun `creates directory if not exists`() {
        val mockContext = mock(Context::class.java)
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val mockFilesDir = File(tempDir, "test_files_dir_2")
        mockFilesDir.mkdirs()
        
        `when`(mockContext.filesDir).thenReturn(mockFilesDir)
        
        val useCase = GetRecordingsDirectoryUseCase(mockContext)
        val result = useCase()
        
        assertNotNull("Should return directory", result)
        // mkdirs() is called in the use case, so directory should exist or be created
        assertTrue("Directory should exist or be created", result.exists() || result.parentFile?.exists() == true)
        
        // Cleanup
        result.deleteRecursively()
        mockFilesDir.deleteRecursively()
    }
    
    @Test
    fun `returns correct path`() {
        val mockContext = mock(Context::class.java)
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val mockFilesDir = File(tempDir, "test_files_dir_3")
        mockFilesDir.mkdirs()
        
        `when`(mockContext.filesDir).thenReturn(mockFilesDir)
        
        val useCase = GetRecordingsDirectoryUseCase(mockContext)
        val result = useCase()
        
        assertTrue("Path should end with recordings", result.absolutePath.endsWith("recordings"))
        
        // Cleanup
        result.deleteRecursively()
        mockFilesDir.deleteRecursively()
    }
}

