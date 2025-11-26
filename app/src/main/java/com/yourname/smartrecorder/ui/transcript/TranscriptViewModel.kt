package com.yourname.smartrecorder.ui.transcript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.audio.AudioPlayer
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import com.yourname.smartrecorder.domain.model.Note
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import com.yourname.smartrecorder.domain.repository.NoteRepository
import com.yourname.smartrecorder.domain.model.Bookmark
import com.yourname.smartrecorder.domain.usecase.AddBookmarkUseCase
import com.yourname.smartrecorder.domain.usecase.ExportFormat
import com.yourname.smartrecorder.domain.usecase.ExportTranscriptUseCase
import com.yourname.smartrecorder.domain.usecase.ExtractKeywordsUseCase
import com.yourname.smartrecorder.domain.usecase.GenerateSummaryUseCase
import com.yourname.smartrecorder.domain.usecase.GenerateTranscriptUseCase
import com.yourname.smartrecorder.domain.usecase.GenerateFlashcardsUseCase
import com.yourname.smartrecorder.domain.usecase.GetBookmarksUseCase
import com.yourname.smartrecorder.domain.usecase.GetRecordingDetailUseCase
import com.yourname.smartrecorder.domain.usecase.GetTranscriptUseCase
import com.yourname.smartrecorder.domain.usecase.SearchTranscriptsUseCase
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
    val bookmarks: List<Bookmark> = emptyList(),
    val summary: String = "",
    val keywords: List<String> = emptyList(),
    val questions: List<TranscriptSegment> = emptyList(),
    val isLoading: Boolean = false,
    val isGeneratingTranscript: Boolean = false,
    val transcriptProgress: Int = 0,
    val isPlaying: Boolean = false,
    val isLooping: Boolean = false,
    val currentPositionMs: Long = 0L,
    val currentSegmentId: Long? = null,
    val searchQuery: String = "",
    val searchResults: List<TranscriptSegment> = emptyList(),
    val isGeneratingFlashcards: Boolean = false,
    val flashcardsGenerated: Boolean = false,
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
    private val generateTranscript: GenerateTranscriptUseCase,
    private val exportTranscript: ExportTranscriptUseCase,
    private val getBookmarks: GetBookmarksUseCase,
    private val addBookmark: AddBookmarkUseCase,
    private val searchTranscripts: SearchTranscriptsUseCase,
    private val generateFlashcards: GenerateFlashcardsUseCase,
    private val noteRepository: NoteRepository,
    private val audioPlayer: AudioPlayer
) : ViewModel() {
    
    private var positionUpdateJob: Job? = null

    private val _uiState = MutableStateFlow(TranscriptUiState())
    val uiState: StateFlow<TranscriptUiState> = _uiState.asStateFlow()

    fun loadRecording(recordingId: String) {
        val startTime = System.currentTimeMillis()
        AppLogger.logViewModel(TAG_TRANSCRIPT, "TranscriptViewModel", "loadRecording", 
            "recordingId=$recordingId")
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                AppLogger.d(TAG_TRANSCRIPT, "Loading recording details -> recordingId: %s", recordingId)
                val recording = getRecordingDetail(recordingId)
                if (recording == null) {
                    AppLogger.w(TAG_TRANSCRIPT, "Recording not found -> recordingId: %s", recordingId)
                    _uiState.update { 
                        it.copy(
                            error = "Recording not found. It may have been deleted.",
                            isLoading = false
                        )
                    }
                    return@launch
                }
                
                // Check if audio file exists
                val audioFile = File(recording.filePath)
                if (!audioFile.exists()) {
                    AppLogger.w(TAG_TRANSCRIPT, "Audio file not found -> path: %s", recording.filePath)
                    // Still load transcript if available, but show warning
                }
                
                AppLogger.d(TAG_TRANSCRIPT, "Recording loaded -> id: %s, title: %s, duration: %d ms, file: %s", 
                    recording.id, recording.title, recording.durationMs, recording.filePath)

                // Load transcript segments
                var segmentsLoaded = false
                var notesLoaded = false
                var currentSegments = emptyList<TranscriptSegment>()
                var currentNotes = emptyList<Note>()
                
                fun updateStateIfReady() {
                    if (segmentsLoaded && notesLoaded) {
                        val fullText = currentSegments.joinToString(" ") { it.text }
                        AppLogger.d(TAG_TRANSCRIPT, "All data loaded -> segments: %d, notes: %d, textLength: %d", 
                            currentSegments.size, currentNotes.size, fullText.length)
                        
                        val summaryStartTime = System.currentTimeMillis()
                        val summary = generateSummary(fullText)
                        AppLogger.logPerformance(TAG_TRANSCRIPT, "GenerateSummary", 
                            System.currentTimeMillis() - summaryStartTime)
                        
                        val keywordsStartTime = System.currentTimeMillis()
                        val keywords = extractKeywords(fullText, topN = 10)
                        AppLogger.logPerformance(TAG_TRANSCRIPT, "ExtractKeywords", 
                            System.currentTimeMillis() - keywordsStartTime, "count=${keywords.size}")
                        
                        val questions = currentSegments.filter { 
                            it.text.trim().endsWith("?") || 
                            it.isQuestion 
                        }
                        AppLogger.d(TAG_TRANSCRIPT, "Questions detected: %d", questions.size)
                        
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
                        
                        val duration = System.currentTimeMillis() - startTime
                        AppLogger.logViewModel(TAG_TRANSCRIPT, "TranscriptViewModel", "loadRecording completed", 
                            "duration=${duration}ms, segments=${currentSegments.size}, notes=${currentNotes.size}")
                        AppLogger.logPerformance(TAG_TRANSCRIPT, "loadRecording", duration)
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
                
                // Load bookmarks in parallel
                val bookmarksJob = launch {
                    getBookmarks(recordingId)
                        .catch { e ->
                            AppLogger.e(TAG_TRANSCRIPT, "Failed to load bookmarks", e)
                        }
                        .collect { bookmarks ->
                            _uiState.update { it.copy(bookmarks = bookmarks) }
                            AppLogger.d(TAG_TRANSCRIPT, "Bookmarks loaded: %d", bookmarks.size)
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
            AppLogger.d(TAG_TRANSCRIPT, "Seeking to position: %d ms", positionMs)
            audioPlayer.seekTo(positionMs.toInt())
            _uiState.update { it.copy(currentPositionMs = positionMs) }
            updateCurrentSegment(positionMs)
        } else {
            AppLogger.w(TAG_TRANSCRIPT, "Seek rejected - file not found: %s", file.absolutePath)
        }
    }

    @Volatile
    private var isToggling: Boolean = false
    
    fun togglePlayPause() {
        if (isToggling) {
            AppLogger.w(TAG_TRANSCRIPT, "Toggle play/pause rejected - already toggling")
            return // Prevent concurrent toggles
        }
        
        val recording = _uiState.value.recording ?: run {
            AppLogger.w(TAG_TRANSCRIPT, "Toggle play/pause rejected - no recording")
            return
        }
        val file = File(recording.filePath)
        
        if (!file.exists()) {
            AppLogger.e(TAG_TRANSCRIPT, "Audio file not found -> path: %s", null, recording.filePath)
            _uiState.update { it.copy(error = "Audio file not found") }
            return
        }
        
        isToggling = true
        try {
            if (_uiState.value.isPlaying) {
                AppLogger.d(TAG_TRANSCRIPT, "Pausing playback -> position: %d ms", _uiState.value.currentPositionMs)
                audioPlayer.pause()
                positionUpdateJob?.cancel()
                _uiState.update { it.copy(isPlaying = false) }
            } else {
                AppLogger.d(TAG_TRANSCRIPT, "Starting/resuming playback -> file: %s", file.absolutePath)
                if (audioPlayer.isPlaying()) {
                    audioPlayer.resume()
                    AppLogger.d(TAG_TRANSCRIPT, "Resumed existing playback")
                } else {
                    audioPlayer.play(file) {
                        // On completion
                        AppLogger.d(TAG_TRANSCRIPT, "Playback completed, looping: %b", _uiState.value.isLooping)
                        if (!_uiState.value.isLooping) {
                            _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                            positionUpdateJob?.cancel()
                        } else {
                            // Loop: reset position but keep playing
                            audioPlayer.seekTo(0)
                            _uiState.update { it.copy(currentPositionMs = 0L) }
                        }
                    }
                    // Set looping state
                    audioPlayer.setLooping(_uiState.value.isLooping)
                    AppLogger.d(TAG_TRANSCRIPT, "Started new playback")
                }
                startPositionUpdates()
                _uiState.update { it.copy(isPlaying = true) }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG_TRANSCRIPT, "Failed to toggle play/pause", e)
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
                    
                    // Check if finished (only if not looping)
                    if (!_uiState.value.isLooping && position >= (_uiState.value.recording?.durationMs ?: 0)) {
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
    
    fun generateTranscript() {
        val recording = _uiState.value.recording ?: run {
            AppLogger.w(TAG_TRANSCRIPT, "Cannot generate transcript - no recording")
            _uiState.update { it.copy(error = "No recording available") }
            return
        }
        
        // Check if audio file exists
        val audioFile = File(recording.filePath)
        if (!audioFile.exists()) {
            AppLogger.w(TAG_TRANSCRIPT, "Cannot generate transcript - audio file not found: %s", recording.filePath)
            _uiState.update { it.copy(error = "Audio file not found. Cannot generate transcript.") }
            return
        }
        
        if (_uiState.value.isGeneratingTranscript) {
            AppLogger.w(TAG_TRANSCRIPT, "Transcript generation already in progress")
            return
        }
        
        AppLogger.logViewModel(TAG_TRANSCRIPT, "TranscriptViewModel", "generateTranscript", 
            "recordingId=${recording.id}")
        
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        isGeneratingTranscript = true,
                        transcriptProgress = 0,
                        error = null
                    )
                }
                
                val segments = generateTranscript(recording) { progress ->
                    AppLogger.d(TAG_TRANSCRIPT, "Transcript generation progress: %d%%", progress)
                    _uiState.update { it.copy(transcriptProgress = progress) }
                }
                
                AppLogger.d(TAG_TRANSCRIPT, "Transcript generated successfully -> segments: %d", segments.size)
                
                // Reload transcript to get the new segments
                loadRecording(recording.id)
                
                _uiState.update { 
                    it.copy(
                        isGeneratingTranscript = false,
                        transcriptProgress = 100
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_TRANSCRIPT, "Failed to generate transcript", e)
                _uiState.update { 
                    it.copy(
                        isGeneratingTranscript = false,
                        error = e.message ?: "Failed to generate transcript"
                    )
                }
            }
        }
    }
    
    fun addBookmark(note: String = "") {
        val recording = _uiState.value.recording ?: return
        val timestampMs = _uiState.value.currentPositionMs
        
        AppLogger.logViewModel(TAG_TRANSCRIPT, "TranscriptViewModel", "addBookmark", 
            "recordingId=${recording.id}, timestamp=${timestampMs}ms")
        
        viewModelScope.launch {
            try {
                addBookmark(recording.id, timestampMs, note)
                AppLogger.d(TAG_TRANSCRIPT, "Bookmark added -> recordingId: %s, timestamp: %d ms", 
                    recording.id, timestampMs)
            } catch (e: Exception) {
                AppLogger.e(TAG_TRANSCRIPT, "Failed to add bookmark", e)
                _uiState.update { it.copy(error = "Failed to add bookmark: ${e.message}") }
            }
        }
    }
    
    fun searchInTranscript(query: String) {
        val recording = _uiState.value.recording ?: return
        
        AppLogger.logViewModel(TAG_TRANSCRIPT, "TranscriptViewModel", "searchInTranscript", 
            "recordingId=${recording.id}, query=$query")
        
        viewModelScope.launch {
            try {
                val results = searchTranscripts(query, recording.id)
                _uiState.update { 
                    it.copy(
                        searchQuery = query,
                        searchResults = results
                    )
                }
                AppLogger.d(TAG_TRANSCRIPT, "Search completed -> query: %s, results: %d", query, results.size)
            } catch (e: Exception) {
                AppLogger.e(TAG_TRANSCRIPT, "Search failed", e)
                _uiState.update { it.copy(error = "Search failed: ${e.message}") }
            }
        }
    }
    
    fun clearSearch() {
        _uiState.update { 
            it.copy(
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }
    
    fun exportTranscript(format: ExportFormat): String? {
        val recording = _uiState.value.recording ?: run {
            AppLogger.w(TAG_TRANSCRIPT, "Cannot export - no recording")
            _uiState.update { it.copy(error = "No recording available") }
            return null
        }
        val segments = _uiState.value.segments
        if (segments.isEmpty()) {
            AppLogger.w(TAG_TRANSCRIPT, "Cannot export - no transcript segments")
            _uiState.update { it.copy(error = "No transcript available. Please generate transcript first.") }
            return null
        }
        
        return try {
            exportTranscript.export(recording, segments, format)
        } catch (e: Exception) {
            AppLogger.e(TAG_TRANSCRIPT, "Export failed", e)
            _uiState.update { it.copy(error = "Export failed: ${e.message}") }
            null
        }
    }
    
    fun generateFlashcards() {
        val recording = _uiState.value.recording ?: run {
            AppLogger.w(TAG_TRANSCRIPT, "Cannot generate flashcards - no recording")
            _uiState.update { it.copy(error = "No recording available") }
            return
        }
        
        if (_uiState.value.isGeneratingFlashcards) {
            AppLogger.w(TAG_TRANSCRIPT, "Flashcard generation already in progress")
            return
        }
        
        val segments = _uiState.value.segments
        if (segments.isEmpty()) {
            AppLogger.w(TAG_TRANSCRIPT, "Cannot generate flashcards - no transcript segments")
            _uiState.update { it.copy(error = "No transcript available. Please generate transcript first.") }
            return
        }
        
        AppLogger.logViewModel(TAG_TRANSCRIPT, "TranscriptViewModel", "generateFlashcards", 
            "recordingId=${recording.id}, segments=${segments.size}")
        
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        isGeneratingFlashcards = true,
                        error = null
                    )
                }
                
                val flashcards = generateFlashcards(recording.id, segments)
                
                AppLogger.d(TAG_TRANSCRIPT, "Flashcards generated successfully -> count: %d", flashcards.size)
                
                _uiState.update { 
                    it.copy(
                        isGeneratingFlashcards = false,
                        flashcardsGenerated = true
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_TRANSCRIPT, "Failed to generate flashcards", e)
                _uiState.update { 
                    it.copy(
                        isGeneratingFlashcards = false,
                        error = e.message ?: "Failed to generate flashcards"
                    )
                }
            }
        }
    }
    
    fun toggleLoop() {
        val newLoopingState = !_uiState.value.isLooping
        AppLogger.d(TAG_TRANSCRIPT, "Toggling loop: %b", newLoopingState)
        audioPlayer.setLooping(newLoopingState)
        _uiState.update { it.copy(isLooping = newLoopingState) }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    override fun onCleared() {
        super.onCleared()
        positionUpdateJob?.cancel()
        // Don't release AudioPlayer here as it's a singleton shared across ViewModels
        // It will be cleaned up when app is destroyed
        audioPlayer.stop()
    }
}

