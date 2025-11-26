package com.yourname.smartrecorder.ui.transcript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.audio.AudioPlayer
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import com.yourname.smartrecorder.core.service.ForegroundServiceManager
import com.yourname.smartrecorder.domain.model.Note
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.model.TranscriptSegment
import com.yourname.smartrecorder.domain.repository.NoteRepository
import com.yourname.smartrecorder.domain.model.Bookmark
import com.yourname.smartrecorder.domain.usecase.AddBookmarkUseCase
import com.yourname.smartrecorder.domain.usecase.DeleteRecordingUseCase
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
    private val deleteRecording: DeleteRecordingUseCase,
    private val noteRepository: NoteRepository,
    private val audioPlayer: AudioPlayer,
    private val foregroundServiceManager: ForegroundServiceManager
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
        val recording = _uiState.value.recording ?: run {
            AppLogger.logRareCondition(TAG_TRANSCRIPT, "Seek called but no recording loaded")
            return
        }
        
        val file = File(recording.filePath)
        if (!file.exists()) {
            AppLogger.logRareCondition(TAG_TRANSCRIPT, "Seek rejected - file not found", 
                "path=${file.absolutePath}")
            _uiState.update { it.copy(error = "Audio file not found") }
            return
        }
        
        val percentage = (positionMs.toFloat() / recording.durationMs.coerceAtLeast(1)) * 100
        AppLogger.logMain(TAG_TRANSCRIPT, "User seeking to position: %d ms (%.2f%%)", 
            positionMs, percentage)
        
        try {
            audioPlayer.seekTo(positionMs.toInt())
            _uiState.update { it.copy(currentPositionMs = positionMs) }
            updateCurrentSegment(positionMs)
            
            // Update foreground service notification if playing
            if (_uiState.value.isPlaying) {
                foregroundServiceManager.updatePlaybackNotification(
                    positionMs,
                    recording.durationMs,
                    isPaused = false
                )
            }
            
            AppLogger.logBackground(TAG_TRANSCRIPT, "Seek completed -> position: %d ms", positionMs)
        } catch (e: Exception) {
            AppLogger.e(TAG_TRANSCRIPT, "Failed to seek", e)
            _uiState.update { it.copy(error = "Failed to seek: ${e.message}") }
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
                AppLogger.logMain(TAG_TRANSCRIPT, "Pausing playback -> position: %d ms", _uiState.value.currentPositionMs)
                audioPlayer.pause()
                positionUpdateJob?.cancel()
                _uiState.update { it.copy(isPlaying = false) }
                
                // Update foreground service notification
                foregroundServiceManager.updatePlaybackNotification(
                    _uiState.value.currentPositionMs,
                    recording.durationMs,
                    isPaused = true
                )
            } else {
                AppLogger.logMain(TAG_TRANSCRIPT, "Starting/resuming playback -> file: %s, size: %d bytes", 
                    file.absolutePath, file.length())
                
                if (audioPlayer.isPlaying()) {
                    audioPlayer.resume()
                    AppLogger.logMain(TAG_TRANSCRIPT, "Resumed existing playback -> position: %d ms", 
                        _uiState.value.currentPositionMs)
                } else {
                    // Start foreground service before playing
                    foregroundServiceManager.startPlaybackService(
                        recording.title.ifEmpty { "Recording" },
                        recording.durationMs
                    )
                    
                    audioPlayer.play(file) {
                        // On completion
                        AppLogger.logMain(TAG_TRANSCRIPT, "Playback completed, looping: %b", _uiState.value.isLooping)
                        if (!_uiState.value.isLooping) {
                            _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                            positionUpdateJob?.cancel()
                            // Stop foreground service
                            foregroundServiceManager.stopPlaybackService()
                        } else {
                            // Loop: reset position but keep playing
                            audioPlayer.seekTo(0)
                            _uiState.update { it.copy(currentPositionMs = 0L) }
                        }
                    }
                    // Set looping state
                    audioPlayer.setLooping(_uiState.value.isLooping)
                    AppLogger.logMain(TAG_TRANSCRIPT, "Started new playback -> duration: %d ms, looping: %b", 
                        recording.durationMs, _uiState.value.isLooping)
                }
                startPositionUpdates()
                _uiState.update { it.copy(isPlaying = true) }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG_TRANSCRIPT, "Failed to toggle play/pause", e)
            _uiState.update { it.copy(error = e.message, isPlaying = false) }
            // Stop service on error
            foregroundServiceManager.stopPlaybackService()
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
                    val recording = _uiState.value.recording
                    
                    _uiState.update { it.copy(currentPositionMs = position.toLong()) }
                    updateCurrentSegment(position.toLong())
                    
                    // Update foreground service notification every second
                    if (recording != null && position % 1000 < 100) {
                        foregroundServiceManager.updatePlaybackNotification(
                            position.toLong(),
                            recording.durationMs,
                            isPaused = false
                        )
                    }
                    
                    // Check if finished (only if not looping)
                    if (!_uiState.value.isLooping && recording != null && position >= recording.durationMs) {
                        AppLogger.logMain(TAG_TRANSCRIPT, "Playback finished -> final position: %d ms", position)
                        audioPlayer.pause()
                        _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                        foregroundServiceManager.stopPlaybackService()
                        break
                    }
                } else {
                    break
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
        AppLogger.d(TAG_TRANSCRIPT, "[TranscriptViewModel] User cleared search")
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
        
        AppLogger.logViewModel(TAG_TRANSCRIPT, "TranscriptViewModel", "exportTranscript", 
            "recordingId=${recording.id}, format=$format, segments=${segments.size}")
        
        return try {
            val startTime = System.currentTimeMillis()
            val exportedText = exportTranscript.export(recording, segments, format)
            val duration = System.currentTimeMillis() - startTime
            val textLength = exportedText?.length ?: 0
            AppLogger.logPerformance(TAG_TRANSCRIPT, "Export transcript", duration, 
                "format=$format, textLength=$textLength")
            AppLogger.d(TAG_TRANSCRIPT, "Export successful -> format: %s, textLength: %d chars", format, textLength)
            exportedText
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
        AppLogger.d(TAG_TRANSCRIPT, "[TranscriptViewModel] User toggled loop: %b -> %b", 
            _uiState.value.isLooping, newLoopingState)
        audioPlayer.setLooping(newLoopingState)
        _uiState.update { it.copy(isLooping = newLoopingState) }
    }
    
    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()
    
    fun onNavigationHandled() {
        _navigateBack.value = false
    }
    
    fun deleteRecording() {
        val recording = _uiState.value.recording ?: run {
            AppLogger.w(TAG_TRANSCRIPT, "Cannot delete - no recording available")
            _uiState.update { it.copy(error = "No recording available") }
            return
        }
        
        AppLogger.logViewModel(TAG_TRANSCRIPT, "TranscriptViewModel", "deleteRecording", 
            "recordingId=${recording.id}, title=${recording.title}")
        
        viewModelScope.launch {
            try {
                // Stop playback if playing
                if (_uiState.value.isPlaying) {
                    AppLogger.d(TAG_TRANSCRIPT, "[TranscriptViewModel] Stopping playback before deletion")
                    audioPlayer.stop()
                    foregroundServiceManager.stopPlaybackService()
                }
                
                deleteRecording(recording)
                
                // Navigate back after deletion
                _navigateBack.value = true
                
                AppLogger.d(TAG_TRANSCRIPT, "[TranscriptViewModel] Recording deleted successfully, navigating back")
            } catch (e: Exception) {
                AppLogger.e(TAG_TRANSCRIPT, "Failed to delete recording", e)
                _uiState.update { it.copy(error = "Failed to delete recording: ${e.message}") }
            }
        }
    }
    
    fun clearError() {
        AppLogger.d(TAG_TRANSCRIPT, "[TranscriptViewModel] Error cleared by user")
        _uiState.update { it.copy(error = null) }
    }
    
    override fun onCleared() {
        super.onCleared()
        AppLogger.logLifecycle(TAG_TRANSCRIPT, "TranscriptViewModel", "onCleared")
        
        // Stop position updates
        positionUpdateJob?.cancel()
        
        // Stop playback and foreground service if active
        if (_uiState.value.isPlaying) {
            AppLogger.logRareCondition(TAG_TRANSCRIPT, 
                "ViewModel cleared while playback active", 
                "position=${_uiState.value.currentPositionMs}ms")
            try {
                audioPlayer.stop()
                foregroundServiceManager.stopPlaybackService()
                AppLogger.logMain(TAG_TRANSCRIPT, "Playback stopped during cleanup")
            } catch (e: Exception) {
                AppLogger.e(TAG_TRANSCRIPT, "Error stopping playback during cleanup", e)
            }
        }
    }
}

