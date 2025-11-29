package com.yourname.smartrecorder.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.audio.AudioPlayer
import com.yourname.smartrecorder.core.service.ForegroundServiceManager
import com.yourname.smartrecorder.data.repository.PlaybackSessionRepository
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.state.PlaybackState
import com.yourname.smartrecorder.domain.usecase.DeleteRecordingUseCase
import com.yourname.smartrecorder.domain.usecase.GetRecordingListUseCase
import com.yourname.smartrecorder.domain.usecase.SearchTranscriptsUseCase
import com.yourname.smartrecorder.domain.usecase.UpdateRecordingTitleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.yourname.smartrecorder.core.utils.VolumeChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject

data class LibraryUiState(
    val recordings: List<Recording> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Recording> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentlyPlayingId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L, // Vị trí hiện tại của audio playback
    val toastMessage: String? = null,
    // Inline editing state
    val editingRecordingId: String? = null,
    val editingTitle: String = ""
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getRecordingList: GetRecordingListUseCase,
    private val searchTranscripts: SearchTranscriptsUseCase,
    private val audioPlayer: AudioPlayer,
    private val updateRecordingTitle: UpdateRecordingTitleUseCase,
    private val deleteRecordingUseCase: DeleteRecordingUseCase,
    private val foregroundServiceManager: ForegroundServiceManager,
    private val playbackSessionRepository: PlaybackSessionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Expose playback state from repository
    val playbackState: StateFlow<PlaybackState> = 
        playbackSessionRepository.state
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PlaybackState.Idle
            )

    // Derive UI state from repository state and other state
    private val _otherUiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = combine(
        playbackState,
        _otherUiState
    ) { playbackState, otherState ->
        LibraryUiState(
            recordings = otherState.recordings,
            searchQuery = otherState.searchQuery,
            searchResults = otherState.searchResults,
            isSearching = otherState.isSearching,
            isLoading = otherState.isLoading,
            error = otherState.error,
            currentlyPlayingId = when (playbackState) {
                is PlaybackState.Playing -> playbackState.recordingId
                is PlaybackState.Paused -> playbackState.recordingId
                else -> null
            },
            isPlaying = playbackState is PlaybackState.Playing,
            currentPositionMs = when (playbackState) {
                is PlaybackState.Playing -> playbackState.positionMs
                is PlaybackState.Paused -> playbackState.positionMs
                else -> 0L
            },
            toastMessage = otherState.toastMessage,
            editingRecordingId = otherState.editingRecordingId,
            editingTitle = otherState.editingTitle
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState()
    )
    
    private var positionUpdateJob: Job? = null

    init {
        AppLogger.logViewModel(TAG_VIEWMODEL, "LibraryViewModel", "Initialized", null)
        loadRecordings()
    }

    fun loadRecordings() {
        AppLogger.logViewModel(TAG_VIEWMODEL, "LibraryViewModel", "loadRecordings", null)
        viewModelScope.launch {
            _otherUiState.update { it.copy(isLoading = true, error = null) }
            getRecordingList()
                .catch { e ->
                    AppLogger.e(TAG_VIEWMODEL, "Failed to load recordings", e)
                    _otherUiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { recordings ->
                    AppLogger.logViewModel(TAG_VIEWMODEL, "LibraryViewModel", "Recordings loaded", 
                        "count=${recordings.size}")
                    _otherUiState.update { 
                        it.copy(
                            recordings = recordings,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        AppLogger.d(TAG_VIEWMODEL, "Search query updated: %s", query)
        _otherUiState.update { it.copy(searchQuery = query) }
        
        // Perform search when query changes
        if (query.isNotEmpty() && query.length >= 2) {
            performSearch(query)
        } else {
            _otherUiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }
    
    private fun performSearch(query: String) {
        viewModelScope.launch {
            try {
                _otherUiState.update { it.copy(isSearching = true) }
                
                // First, do title/id search (synchronous)
                val titleFiltered = _otherUiState.value.recordings.filter { recording ->
                    recording.title.lowercase().contains(query.lowercase()) ||
                    recording.id.lowercase().contains(query.lowercase())
                }
                
                // Then, do FTS search in transcripts (async)
                val ftsResults = try {
                    searchTranscripts.searchRecordings(query)
                } catch (e: Exception) {
                    AppLogger.e(TAG_VIEWMODEL, "FTS search failed", e)
                    emptyList()
                }
                
                // Combine results: title matches first, then FTS results (avoid duplicates)
                val combinedResults = mutableListOf<Recording>()
                val seenIds = mutableSetOf<String>()
                
                // Add title matches first
                titleFiltered.forEach { recording ->
                    if (seenIds.add(recording.id)) {
                        combinedResults.add(recording)
                    }
                }
                
                // Add FTS results that aren't already included
                ftsResults.forEach { recording ->
                    if (seenIds.add(recording.id)) {
                        combinedResults.add(recording)
                    }
                }
                
                AppLogger.d(TAG_VIEWMODEL, "Search completed -> query: %s, titleResults: %d, ftsResults: %d, combined: %d", 
                    query, titleFiltered.size, ftsResults.size, combinedResults.size)
                
                _otherUiState.update { 
                    it.copy(
                        searchResults = combinedResults,
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_VIEWMODEL, "Search failed", e)
                _otherUiState.update { it.copy(isSearching = false, error = e.message) }
            }
        }
    }

    fun getFilteredRecordings(): List<Recording> {
        val query = _otherUiState.value.searchQuery.trim()
        if (query.isEmpty()) {
            return _otherUiState.value.recordings
        }
        
        // Return search results if available
        return if (_otherUiState.value.searchResults.isNotEmpty()) {
            _otherUiState.value.searchResults
        } else {
            // Fallback to simple title search while FTS is loading
            _otherUiState.value.recordings.filter { recording ->
                recording.title.lowercase().contains(query.lowercase()) ||
                recording.id.lowercase().contains(query.lowercase())
            }
        }
    }
    
    fun playRecording(recording: Recording) {
        val currentPlaybackState = playbackState.value
        AppLogger.logViewModel(TAG_VIEWMODEL, "LibraryViewModel", "playRecording", 
            "recordingId=${recording.id}, currentState=${currentPlaybackState.javaClass.simpleName}")
        viewModelScope.launch {
            try {
                val file = File(recording.filePath)
                if (!file.exists()) {
                    AppLogger.e(TAG_VIEWMODEL, "Audio file not found: %s", null, recording.filePath)
                    _otherUiState.update { it.copy(error = "Audio file not found. The recording may have been moved or deleted.") }
                    return@launch
                }
                
                if (!file.canRead()) {
                    AppLogger.e(TAG_VIEWMODEL, "Cannot read audio file: %s", null, recording.filePath)
                    _otherUiState.update { it.copy(error = "Cannot read audio file. Please check file permissions.") }
                    return@launch
                }
                
                when (currentPlaybackState) {
                    is PlaybackState.Playing -> {
                        if (currentPlaybackState.recordingId == recording.id) {
                            // Pause current playback
                            AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Pausing playback -> recordingId: %s", recording.id)
                            // ⚠️ CRITICAL: Service will pause AudioPlayer AND update repository
                            foregroundServiceManager.pausePlaybackService()
                            positionUpdateJob?.cancel()
                            // Service will update repository, UI will react automatically
                        } else {
                            // Stop current and play new
                            audioPlayer.stop()
                            foregroundServiceManager.stopPlaybackService()
                            startNewPlayback(recording)
                        }
                    }
                    is PlaybackState.Paused -> {
                        if (currentPlaybackState.recordingId == recording.id) {
                            // Resume
                            AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Resuming playback -> recordingId: %s", recording.id)
                            // ⚠️ CRITICAL: Service will resume AudioPlayer AND update repository
                            foregroundServiceManager.resumePlaybackService()
                            startPositionUpdates()
                            // Service will update repository, UI will react automatically
                        } else {
                            // Stop current and play new
                            audioPlayer.stop()
                            foregroundServiceManager.stopPlaybackService()
                            startNewPlayback(recording)
                        }
                    }
                    is PlaybackState.Idle -> {
                        // Start new playback
                        startNewPlayback(recording)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_VIEWMODEL, "Failed to play recording", e)
                _otherUiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    private fun startNewPlayback(recording: Recording) {
        val file = File(recording.filePath)
        
        // Check volume before playing
        if (VolumeChecker.isVolumeLow(context)) {
            val volumePercent = VolumeChecker.getVolumePercent(context)
            AppLogger.w(TAG_VIEWMODEL, "[LibraryViewModel] Volume is low: %d%%, showing warning toast", volumePercent)
            _otherUiState.update { 
                it.copy(toastMessage = "Volume too low (${volumePercent}%). Please increase volume for better audio quality.")
            }
        } else {
            _otherUiState.update { it.copy(toastMessage = null) }
        }
        
        AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Starting playback -> recordingId: %s, file: %s, size: %d bytes", 
            recording.id, file.absolutePath, file.length())
        
        // Start foreground service - service will update repository
        foregroundServiceManager.startPlaybackService(
            recording.id,
            recording.title.ifEmpty { "Recording" },
            recording.durationMs
        )
        
        // Start AudioPlayer
        audioPlayer.play(file) {
            // On completion
            AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Playback completed -> recordingId: %s", recording.id)
            positionUpdateJob?.cancel()
            // Stop foreground service - service will update repository to Idle
            foregroundServiceManager.stopPlaybackService()
        }
        
        startPositionUpdates()
        // Service will update repository to Playing, UI will react automatically
    }
    
    fun pausePlayback() {
        val currentPlaybackState = playbackState.value
        AppLogger.logViewModel(TAG_VIEWMODEL, "LibraryViewModel", "pausePlayback", 
            "currentState=${currentPlaybackState.javaClass.simpleName}")
        
        when (currentPlaybackState) {
            is PlaybackState.Playing -> {
                // Pause playback - giữ nguyên trạng thái play
                AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Pausing playback -> recordingId: %s", currentPlaybackState.recordingId)
                foregroundServiceManager.pausePlaybackService()
                positionUpdateJob?.cancel()
                // Service will update repository, UI will react automatically
            }
            is PlaybackState.Paused -> {
                // Resume playback
                AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Resuming playback -> recordingId: %s", currentPlaybackState.recordingId)
                foregroundServiceManager.resumePlaybackService()
                startPositionUpdates()
            }
            else -> {
                AppLogger.logRareCondition(TAG_VIEWMODEL, "Pause called when not playing/paused")
            }
        }
    }
    
    fun stopPlayback() {
        val currentPlaybackState = playbackState.value
        AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] User stopped playback -> currentState=${currentPlaybackState.javaClass.simpleName}")
        viewModelScope.launch {
            try {
                audioPlayer.stop()
                positionUpdateJob?.cancel()
                foregroundServiceManager.stopPlaybackService()
                // Service will update repository to Idle, UI will react automatically
            } catch (e: Exception) {
                AppLogger.e(TAG_VIEWMODEL, "Failed to stop playback", e)
            }
        }
    }
    
    fun seekTo(positionMs: Long) {
        val currentPlaybackState = playbackState.value
        if (currentPlaybackState !is PlaybackState.Playing && currentPlaybackState !is PlaybackState.Paused) {
            AppLogger.logRareCondition(TAG_VIEWMODEL, "Seek called but no recording is playing")
            return
        }
        
        val recordingId = when (currentPlaybackState) {
            is PlaybackState.Playing -> currentPlaybackState.recordingId
            is PlaybackState.Paused -> currentPlaybackState.recordingId
            is PlaybackState.Idle -> return
        }
        
        val durationMs = when (currentPlaybackState) {
            is PlaybackState.Playing -> currentPlaybackState.durationMs
            is PlaybackState.Paused -> currentPlaybackState.durationMs
            is PlaybackState.Idle -> return
        }
        
        val isPaused = currentPlaybackState is PlaybackState.Paused
        
        val recording = _otherUiState.value.recordings.find { it.id == recordingId }
        if (recording == null) {
            AppLogger.logRareCondition(TAG_VIEWMODEL, "Seek rejected - recording not found", 
                "recordingId=$recordingId")
            return
        }
        
        val file = File(recording.filePath)
        if (!file.exists()) {
            AppLogger.logRareCondition(TAG_VIEWMODEL, "Seek rejected - file not found", 
                "path=${file.absolutePath}")
            return
        }
        
        val percentage = (positionMs.toFloat() / recording.durationMs.coerceAtLeast(1)) * 100
        AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] User seeking to position: %d ms (%.2f%%)", 
            positionMs, percentage)
        
        try {
            audioPlayer.seekTo(positionMs.toInt())
            
            // Update service notification (service will update repository)
            foregroundServiceManager.updatePlaybackNotification(
                recordingId,
                positionMs,
                durationMs,
                isPaused = isPaused
            )
            
            AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Seek completed -> position: %d ms", positionMs)
        } catch (e: Exception) {
            AppLogger.e(TAG_VIEWMODEL, "Failed to seek", e)
            _otherUiState.update { it.copy(error = "Failed to seek: ${e.message}") }
        }
    }
    
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val currentPlaybackState = playbackState.value
                if (currentPlaybackState !is PlaybackState.Playing) {
                    break
                }
                
                val position = audioPlayer.getCurrentPosition()
                val recording = _otherUiState.value.recordings.find { 
                    it.id == currentPlaybackState.recordingId 
                }
                
                // Update foreground service notification every second (service will update repository)
                if (recording != null && position % 1000 < 100) {
                    foregroundServiceManager.updatePlaybackNotification(
                        recording.id,
                        position.toLong(),
                        recording.durationMs,
                        isPaused = false
                    )
                }
                
                // Check if finished
                if (recording != null && position >= recording.durationMs) {
                    AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Playback finished -> final position: %d ms", position)
                    audioPlayer.pause()
                    // Stop foreground service - service will update repository to Idle
                    foregroundServiceManager.stopPlaybackService()
                    positionUpdateJob?.cancel()
                    break
                }
            }
        }
    }
    
    fun updateTitle(recording: Recording, newTitle: String) {
        AppLogger.logViewModel(TAG_VIEWMODEL, "LibraryViewModel", "updateTitle", 
            "recordingId=${recording.id}, oldTitle=${recording.title}, newTitle=$newTitle")
        viewModelScope.launch {
            try {
                updateRecordingTitle(recording, newTitle)
                // Don't call loadRecordings() - Flow will automatically update when database changes
                AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Title updated successfully")
            } catch (e: Exception) {
                AppLogger.e(TAG_VIEWMODEL, "Failed to update title", e)
                _otherUiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    // Inline editing methods
    fun startEditing(recordingId: String) {
        val recording = _otherUiState.value.recordings.find { it.id == recordingId }
        if (recording != null) {
            AppLogger.d(TAG_VIEWMODEL, "Starting edit mode for recording -> recordingId: %s", recordingId)
            _otherUiState.update { 
                it.copy(
                    editingRecordingId = recordingId,
                    editingTitle = recording.title.ifBlank { "Untitled Recording" }
                )
            }
        } else {
            AppLogger.w(TAG_VIEWMODEL, "Recording not found for editing -> recordingId: %s", recordingId)
        }
    }
    
    fun updateEditingTitle(title: String) {
        _otherUiState.update { it.copy(editingTitle = title) }
    }
    
    fun saveEditing() {
        val editingRecordingId = _otherUiState.value.editingRecordingId
        val editingTitle = _otherUiState.value.editingTitle.trim()
        
        if (editingRecordingId == null || editingTitle.isEmpty()) {
            AppLogger.w(TAG_VIEWMODEL, "Cannot save: editingRecordingId is null or title is empty")
            cancelEditing()
            return
        }
        
        val recording = _otherUiState.value.recordings.find { it.id == editingRecordingId }
        if (recording == null) {
            AppLogger.w(TAG_VIEWMODEL, "Recording not found for saving -> recordingId: %s", editingRecordingId)
            cancelEditing()
            return
        }
        
        if (recording.title == editingTitle) {
            // No changes, just cancel editing
            AppLogger.d(TAG_VIEWMODEL, "No changes detected, canceling edit")
            cancelEditing()
            return
        }
        
        viewModelScope.launch {
            try {
                AppLogger.d(TAG_VIEWMODEL, "Saving edited title -> recordingId: %s", editingRecordingId)
                updateRecordingTitle(recording, editingTitle)
                AppLogger.d(TAG_VIEWMODEL, "Title saved successfully -> recordingId: %s", editingRecordingId)
                _otherUiState.update { 
                    it.copy(
                        editingRecordingId = null,
                        editingTitle = ""
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_VIEWMODEL, "Error saving title", e)
                _otherUiState.update { 
                    it.copy(
                        error = "Failed to save: ${e.message}",
                        editingRecordingId = null,
                        editingTitle = ""
                    )
                }
            }
        }
    }
    
    fun cancelEditing() {
        AppLogger.d(TAG_VIEWMODEL, "Canceling edit mode")
        _otherUiState.update { 
            it.copy(
                editingRecordingId = null,
                editingTitle = ""
            )
        }
    }
    
    fun deleteRecording(recording: Recording) {
        AppLogger.logViewModel(TAG_VIEWMODEL, "LibraryViewModel", "deleteRecording", 
            "recordingId=${recording.id}, title=${recording.title}")
        viewModelScope.launch {
            try {
                // Stop playback if this recording is currently playing
                if (_otherUiState.value.currentlyPlayingId == recording.id) {
                    AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Stopping playback before deletion")
                    audioPlayer.stop()
                    _otherUiState.update { 
                        it.copy(
                            isPlaying = false,
                            currentlyPlayingId = null
                        ) 
                    }
                }
                
                // Use the use case (renamed to avoid conflict with function name)
                deleteRecordingUseCase(recording)
                
                // Don't call loadRecordings() - Flow will automatically update when database changes
                // Calling loadRecordings() creates multiple Flow collectors causing infinite loop
                
                AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Recording deleted successfully")
            } catch (e: Exception) {
                AppLogger.e(TAG_VIEWMODEL, "Failed to delete recording", e)
                _otherUiState.update { it.copy(error = "Failed to delete recording: ${e.message}") }
            }
        }
    }
    
    fun clearError() {
        AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Error cleared by user")
        _otherUiState.update { it.copy(error = null) }
    }
    
    fun clearToastMessage() {
        _otherUiState.update { it.copy(toastMessage = null) }
    }
}

