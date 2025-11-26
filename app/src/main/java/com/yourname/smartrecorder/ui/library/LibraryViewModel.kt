package com.yourname.smartrecorder.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.audio.AudioPlayer
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.usecase.DeleteRecordingUseCase
import com.yourname.smartrecorder.domain.usecase.GetRecordingListUseCase
import com.yourname.smartrecorder.domain.usecase.SearchTranscriptsUseCase
import com.yourname.smartrecorder.domain.usecase.UpdateRecordingTitleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val isPlaying: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getRecordingList: GetRecordingListUseCase,
    private val searchTranscripts: SearchTranscriptsUseCase,
    private val audioPlayer: AudioPlayer,
    private val updateRecordingTitle: UpdateRecordingTitleUseCase,
    private val deleteRecordingUseCase: DeleteRecordingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        AppLogger.logViewModel(TAG_VIEWMODEL, "LibraryViewModel", "Initialized", null)
        loadRecordings()
    }

    fun loadRecordings() {
        AppLogger.logViewModel(TAG_VIEWMODEL, "LibraryViewModel", "loadRecordings", null)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getRecordingList()
                .catch { e ->
                    AppLogger.e(TAG_VIEWMODEL, "Failed to load recordings", e)
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { recordings ->
                    AppLogger.logViewModel(TAG_VIEWMODEL, "LibraryViewModel", "Recordings loaded", 
                        "count=${recordings.size}")
                    _uiState.update { 
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
        _uiState.update { it.copy(searchQuery = query) }
        
        // Perform search when query changes
        if (query.isNotEmpty() && query.length >= 2) {
            performSearch(query)
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }
    
    private fun performSearch(query: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSearching = true) }
                
                // First, do title/id search (synchronous)
                val titleFiltered = _uiState.value.recordings.filter { recording ->
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
                
                _uiState.update { 
                    it.copy(
                        searchResults = combinedResults,
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_VIEWMODEL, "Search failed", e)
                _uiState.update { it.copy(isSearching = false, error = e.message) }
            }
        }
    }

    fun getFilteredRecordings(): List<Recording> {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) {
            return _uiState.value.recordings
        }
        
        // Return search results if available
        return if (_uiState.value.searchResults.isNotEmpty()) {
            _uiState.value.searchResults
        } else {
            // Fallback to simple title search while FTS is loading
            _uiState.value.recordings.filter { recording ->
                recording.title.lowercase().contains(query.lowercase()) ||
                recording.id.lowercase().contains(query.lowercase())
            }
        }
    }
    
    fun playRecording(recording: Recording) {
        AppLogger.logViewModel(TAG_VIEWMODEL, "LibraryViewModel", "playRecording", 
            "recordingId=${recording.id}, currentlyPlayingId=${_uiState.value.currentlyPlayingId}, isPlaying=${_uiState.value.isPlaying}")
        viewModelScope.launch {
            try {
                val file = File(recording.filePath)
                if (!file.exists()) {
                    AppLogger.e(TAG_VIEWMODEL, "Audio file not found: %s", null, recording.filePath)
                    _uiState.update { it.copy(error = "Audio file not found. The recording may have been moved or deleted.") }
                    return@launch
                }
                
                if (!file.canRead()) {
                    AppLogger.e(TAG_VIEWMODEL, "Cannot read audio file: %s", null, recording.filePath)
                    _uiState.update { it.copy(error = "Cannot read audio file. Please check file permissions.") }
                    return@launch
                }
                
                // Stop current playback if different recording
                if (_uiState.value.currentlyPlayingId != null && 
                    _uiState.value.currentlyPlayingId != recording.id) {
                    AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Stopping previous playback -> previousId: %s", 
                        _uiState.value.currentlyPlayingId)
                    audioPlayer.stop()
                }
                
                if (_uiState.value.isPlaying && _uiState.value.currentlyPlayingId == recording.id) {
                    // Already playing this recording, pause it
                    AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Pausing playback -> recordingId: %s", recording.id)
                    audioPlayer.pause()
                    _uiState.update { it.copy(isPlaying = false) }
                } else {
                    // Play or resume
                    if (audioPlayer.isPlaying() && _uiState.value.currentlyPlayingId == recording.id) {
                        AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Resuming playback -> recordingId: %s", recording.id)
                        audioPlayer.resume()
                    } else {
                        AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Starting playback -> recordingId: %s, file: %s, size: %d bytes", 
                            recording.id, file.absolutePath, file.length())
                        audioPlayer.play(file) {
                            // On completion
                            AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Playback completed -> recordingId: %s", recording.id)
                            _uiState.update { 
                                it.copy(
                                    isPlaying = false, 
                                    currentlyPlayingId = null
                                ) 
                            }
                        }
                    }
                    _uiState.update { 
                        it.copy(
                            isPlaying = true,
                            currentlyPlayingId = recording.id
                        ) 
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_VIEWMODEL, "Failed to play recording", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun stopPlayback() {
        AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] User stopped playback -> currentlyPlayingId: %s", 
            _uiState.value.currentlyPlayingId)
        viewModelScope.launch {
            try {
                audioPlayer.stop()
                _uiState.update { 
                    it.copy(
                        isPlaying = false,
                        currentlyPlayingId = null
                    ) 
                }
            } catch (e: Exception) {
                AppLogger.e(TAG_VIEWMODEL, "Failed to stop playback", e)
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
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun deleteRecording(recording: Recording) {
        AppLogger.logViewModel(TAG_VIEWMODEL, "LibraryViewModel", "deleteRecording", 
            "recordingId=${recording.id}, title=${recording.title}")
        viewModelScope.launch {
            try {
                // Stop playback if this recording is currently playing
                if (_uiState.value.currentlyPlayingId == recording.id) {
                    AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Stopping playback before deletion")
                    audioPlayer.stop()
                    _uiState.update { 
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
                _uiState.update { it.copy(error = "Failed to delete recording: ${e.message}") }
            }
        }
    }
    
    fun clearError() {
        AppLogger.d(TAG_VIEWMODEL, "[LibraryViewModel] Error cleared by user")
        _uiState.update { it.copy(error = null) }
    }
}

