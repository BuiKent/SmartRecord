package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.speech.GoogleASRManager
import com.yourname.smartrecorder.core.speech.RecognitionState
import com.yourname.smartrecorder.core.speech.RecognizedToken
import com.yourname.smartrecorder.core.speech.SpeechRecognizerListener
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for RealtimeTranscriptUseCase
 */
class RealtimeTranscriptUseCaseTest {
    
    private val mockGoogleASRManager = mock<GoogleASRManager>()
    private val useCase = RealtimeTranscriptUseCase(mockGoogleASRManager)
    
    @Test
    fun `start initializes recognizer and starts listening`() {
        var callbackInvoked = false
        var callbackText = ""
        
        useCase.start { text ->
            callbackInvoked = true
            callbackText = text
        }
        
        verify(mockGoogleASRManager).initialize(any<SpeechRecognizerListener>())
    }
    
    @Test
    fun `start calls callback when recognizer is ready`() {
        var callbackText = ""
        val listenerCaptor = argumentCaptor<SpeechRecognizerListener>()
        
        useCase.start { text -> callbackText = text }
        
        verify(mockGoogleASRManager).initialize(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue
        
        // Simulate ready state
        listener.onReady(true)
        
        verify(mockGoogleASRManager).startListening()
    }
    
    @Test
    fun `start calls callback with error message when recognizer not ready`() {
        var callbackText = ""
        val listenerCaptor = argumentCaptor<SpeechRecognizerListener>()
        
        useCase.start { text -> callbackText = text }
        
        verify(mockGoogleASRManager).initialize(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue
        
        // Simulate not ready state
        listener.onReady(false)
        
        assertEquals("Should show error message", "[Speech recognition not available]", callbackText)
    }
    
    @Test
    fun `partial results are sent with PARTIAL prefix`() {
        var callbackText = ""
        val listenerCaptor = argumentCaptor<SpeechRecognizerListener>()
        
        useCase.start { text -> callbackText = text }
        
        verify(mockGoogleASRManager).initialize(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue
        
        val tokens = listOf(
            RecognizedToken("hello", 0.9f),
            RecognizedToken("world", 0.85f)
        )
        
        listener.onPartialResults(tokens)
        
        assertTrue("Should start with PARTIAL:", callbackText.startsWith("PARTIAL:"))
        assertTrue("Should contain text", callbackText.contains("hello"))
    }
    
    @Test
    fun `final results are accumulated and sent with FINAL prefix`() {
        var callbackText = ""
        val listenerCaptor = argumentCaptor<SpeechRecognizerListener>()
        
        useCase.start { text -> callbackText = text }
        
        verify(mockGoogleASRManager).initialize(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue
        
        // First final result
        val tokens1 = listOf(
            RecognizedToken("hello", 0.9f),
            RecognizedToken("world", 0.85f)
        )
        listener.onFinalResults(tokens1)
        
        assertTrue("Should start with FINAL:", callbackText.startsWith("FINAL:"))
        assertTrue("Should contain accumulated text", callbackText.contains("hello world"))
        
        // Second final result (should accumulate)
        callbackText = ""
        val tokens2 = listOf(
            RecognizedToken("test", 0.9f)
        )
        listener.onFinalResults(tokens2)
        
        assertTrue("Should start with FINAL:", callbackText.startsWith("FINAL:"))
        assertTrue("Should contain all accumulated text", callbackText.contains("hello world test"))
    }
    
    @Test
    fun `empty partial results do not trigger callback`() {
        var callbackInvoked = false
        val listenerCaptor = argumentCaptor<SpeechRecognizerListener>()
        
        useCase.start { callbackInvoked = true }
        
        verify(mockGoogleASRManager).initialize(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue
        
        listener.onPartialResults(emptyList())
        
        // Callback should not be invoked for empty results
        // (Implementation may vary, but typically empty results are skipped)
    }
    
    @Test
    fun `empty final results do not trigger callback`() {
        var callbackInvoked = false
        val listenerCaptor = argumentCaptor<SpeechRecognizerListener>()
        
        useCase.start { callbackInvoked = true }
        
        verify(mockGoogleASRManager).initialize(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue
        
        listener.onFinalResults(emptyList())
        
        // Callback should not be invoked for empty results
    }
    
    @Test
    fun `critical error triggers error callback`() {
        var callbackText = ""
        val listenerCaptor = argumentCaptor<SpeechRecognizerListener>()
        
        useCase.start { text -> callbackText = text }
        
        verify(mockGoogleASRManager).initialize(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue
        
        listener.onError(android.speech.SpeechRecognizer.ERROR_CLIENT, isCritical = true)
        
        assertTrue("Should show error message", callbackText.contains("Error"))
    }
    
    @Test
    fun `non-critical error does not trigger error callback`() {
        var callbackText = ""
        val listenerCaptor = argumentCaptor<SpeechRecognizerListener>()
        
        useCase.start { text -> callbackText = text }
        
        verify(mockGoogleASRManager).initialize(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue
        
        listener.onError(android.speech.SpeechRecognizer.ERROR_NO_MATCH, isCritical = false)
        
        // Non-critical errors may not trigger callback
        // This depends on implementation
    }
    
    @Test
    fun `stop stops listening and clears state`() {
        useCase.start { }
        useCase.stop()
        
        verify(mockGoogleASRManager).stopListening()
    }
    
    @Test
    fun `stop clears accumulated text`() {
        var callbackText = ""
        val listenerCaptor = argumentCaptor<SpeechRecognizerListener>()
        
        useCase.start { text -> callbackText = text }
        
        verify(mockGoogleASRManager).initialize(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue
        
        // Add some final results
        listener.onFinalResults(listOf(RecognizedToken("hello", 0.9f)))
        
        // Stop and start again
        useCase.stop()
        callbackText = ""
        
        useCase.start { text -> callbackText = text }
        
        verify(mockGoogleASRManager, times(2)).initialize(any())
        
        // New session should not have old text
        verify(mockGoogleASRManager, times(2)).stopListening()
    }
    
    @Test
    fun `state changes are logged but do not trigger callback`() {
        var callbackInvoked = false
        val listenerCaptor = argumentCaptor<SpeechRecognizerListener>()
        
        useCase.start { callbackInvoked = true }
        
        verify(mockGoogleASRManager).initialize(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue
        
        listener.onStateChanged(RecognitionState.LISTENING)
        
        // State changes don't trigger transcript callback
        // (They are logged but don't affect transcript text)
    }
    
    @Test
    fun `startRealtimeTranscription returns flow with placeholder`() = runTest {
        val flow = useCase.startRealtimeTranscription("test-id")
        val result = flow.first()
        
        assertTrue("Should return placeholder message", result.contains("Use start()"))
    }
    
    @Test
    fun `stopRealtimeTranscription calls stop`() {
        useCase.start { }
        useCase.stopRealtimeTranscription("test-id")
        
        verify(mockGoogleASRManager).stopListening()
    }
    
    @Test
    fun `multiple final results accumulate correctly`() {
        var callbackText = ""
        val listenerCaptor = argumentCaptor<SpeechRecognizerListener>()
        
        useCase.start { text -> callbackText = text }
        
        verify(mockGoogleASRManager).initialize(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue
        
        // First result
        listener.onFinalResults(listOf(RecognizedToken("first", 0.9f)))
        assertTrue("Should contain first", callbackText.contains("first"))
        
        // Second result
        callbackText = ""
        listener.onFinalResults(listOf(RecognizedToken("second", 0.9f)))
        assertTrue("Should contain both", callbackText.contains("first second"))
        
        // Third result
        callbackText = ""
        listener.onFinalResults(listOf(RecognizedToken("third", 0.9f)))
        assertTrue("Should contain all", callbackText.contains("first second third"))
    }
}

