package com.yourname.smartrecorder.ui.transcript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.audio.AudioPlayer
import com.yourname.smartrecorder.domain.model.Note
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import com.yourname.smartrecorder.domain.repository.NoteRepository
import com.yourname.smartrecorder.domain.usecase.ExportFormat
import com.yourname.smartrecorder.domain.usecase.ExportTranscriptUseCase
import com.yourname.smartrecorder.domain.usecase.ExtractKeywordsUseCase
import com.yourname.smartrecorder.domain.usecase.GenerateSummaryUseCase
import com.yourname.smartrecorder.domain.usecase.GetRecordingDetailUseCase
import com.yourname.smartrecorder.domain.usecase.GetTranscriptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.jvm.Volatile

data class TranscriptUiState(
    val recording: Recording? = null,
    val segments: List<TranscriptSegment> = emptyList(),
    val notes: List<Note> = emptyList(),
    val summary: String = "",
    val keywords: List<String> = emptyList(),
    val questions: List<TranscriptSegment> = emptyList(),
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val currentSegmentId: Long? = null,
    val error: String? = null
)

enum class TranscriptTab {
    TRANSCRIPT, NOTES, SUMMARY
}

@HiltViewModel
class TranscriptViewModel @Inject constructor(
    private val getRecordingDetail: GetRecordingDetailUseCase,
    private val getTranscript: GetTranscriptUseCase,
    private val generateSummary: GenerateSummaryUseCase,
    private val extractKeywords: ExtractKeywordsUseCase,
    private val exportTranscript: ExportTranscriptUseCase,
    private val noteRepository: NoteRepository,
    private val audioPlayer: AudioPlayer
) : ViewModel() {
    
    private var positionUpdateJob: Job? = null

    private val _uiState = MutableStateFlow(TranscriptUiState())
    val uiState: StateFlow<TranscriptUiState> = _uiState.asStateFlow()

    fun loadRecording(recordingId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val recording = getRecordingDetail(recordingId)
                if (recording == null) {
                    _uiState.update { 
                        it.copy(
                            error = "Recording not found",
                            isLoading = false
                        )
                    }
                    return@launch
                }

                // Load transcript segments
                var segmentsLoaded = false
                var notesLoaded = false
                var currentSegments = emptyList<TranscriptSegment>()
                var currentNotes = emptyList<Note>()
                
                fun updateStateIfReady() {
                    if (segmentsLoaded && notesLoaded) {
                        val fullText = currentSegments.joinToString(" ") { it.text }
                        val summary = generateSummary(fullText)
                        val keywords = extractKeywords(fullText, topN = 10)
                        val questions = currentSegments.filter { 
                            it.text.trim().endsWith("?") || 
                            it.isQuestion 
                        }
                        
                        _uiState.update {
                            it.copy(
                                recording = recording,
                                segments = currentSegments,
                                notes = currentNotes,
                                summary = summary,
                                keywords = keywords,
                                questions = questions,
                                isLoading = false
                            )
                        }
                    }
                }
                
                // Load segments
                getTranscript(recordingId)
                    .catch { e ->
                        _uiState.update { it.copy(error = e.message, isLoading = false) }
                    }
                    .collect { segments ->
                        currentSegments = segments
                        segmentsLoaded = true
                        updateStateIfReady()
                    }
                
                // Load notes in parallel (separate coroutine)
                val notesJob = launch {
                    noteRepository.getNotesByRecordingId(recordingId)
                        .catch { e ->
                            // Handle error silently for notes
                            notesLoaded = true
                            updateStateIfReady()
                        }
                        .collect { notes ->
                            currentNotes = notes
                            notesLoaded = true
                            updateStateIfReady()
                        }
                }
                
                // Ensure notes job is cancelled if segments collection fails
                // (This is handled by viewModelScope automatically)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val recording = _uiState.value.recording ?: return
        val file = File(recording.filePath)
        if (file.exists()) {
            audioPlayer.seekTo(positionMs.toInt())
            _uiState.update { it.copy(currentPositionMs = positionMs) }
            updateCurrentSegment(positionMs)
        }
    }

    @Volatile
    private var isToggling: Boolean = false
    
    fun togglePlayPause() {
        if (isToggling) return // Prevent concurrent toggles
        
        val recording = _uiState.value.recording ?: return
        val file = File(recording.filePath)
        
        if (!file.exists()) {
            _uiState.update { it.copy(error = "Audio file not found") }
            return
        }
        
        isToggling = true
        try {
            if (_uiState.value.isPlaying) {
                audioPlayer.pause()
                positionUpdateJob?.cancel()
                _uiState.update { it.copy(isPlaying = false) }
            } else {
                if (audioPlayer.isPlaying()) {
                    audioPlayer.resume()
                } else {
                    audioPlayer.play(file) {
                        // On completion
                        _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                        positionUpdateJob?.cancel()
                    }
                }
                startPositionUpdates()
                _uiState.update { it.copy(isPlaying = true) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message, isPlaying = false) }
        } finally {
            isToggling = false
        }
    }
    
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                delay(100)
                if (_uiState.value.isPlaying) {
                    val position = audioPlayer.getCurrentPosition()
                    _uiState.update { it.copy(currentPositionMs = position.toLong()) }
                    updateCurrentSegment(position.toLong())
                    
                    // Check if finished
                    if (position >= (_uiState.value.recording?.durationMs ?: 0)) {
                        audioPlayer.pause()
                        _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                        break
                    }
                }
            }
        }
    }
    
    private fun updateCurrentSegment(positionMs: Long) {
        val segment = _uiState.value.segments.find { 
            positionMs >= it.startTimeMs && positionMs <= it.endTimeMs 
        }
        _uiState.update { it.copy(currentSegmentId = segment?.id) }
    }
    
    fun exportTranscript(format: ExportFormat): String? {
        val recording = _uiState.value.recording ?: return null
        val segments = _uiState.value.segments
        if (segments.isEmpty()) return null
        
        return exportTranscript.export(recording, segments, format)
    }
    
    override fun onCleared() {
        super.onCleared()
        positionUpdateJob?.cancel()
        // Don't release AudioPlayer here as it's a singleton shared across ViewModels
        // It will be cleaned up when app is destroyed
        audioPlayer.stop()
    }
}

