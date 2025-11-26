package com.yourname.smartrecorder.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.domain.usecase.GetRecordingListUseCase
import com.yourname.smartrecorder.domain.usecase.SearchTranscriptsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val recordings: List<Recording> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getRecordingList: GetRecordingListUseCase,
    private val searchTranscripts: SearchTranscriptsUseCase
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
    }

    fun getFilteredRecordings(): List<Recording> {
        val query = _uiState.value.searchQuery.lowercase().trim()
        if (query.isEmpty()) {
            return _uiState.value.recordings
        }
        
        // Simple title/id search
        val titleFiltered = _uiState.value.recordings.filter { recording ->
            recording.title.lowercase().contains(query) ||
            recording.id.lowercase().contains(query)
        }
        
        // If no results from title search, try FTS search in transcripts
        if (titleFiltered.isEmpty() && query.length > 2) {
            viewModelScope.launch {
                try {
                    val ftsResults = searchTranscripts.searchRecordings(query)
                    if (ftsResults.isNotEmpty()) {
                        _uiState.update { it.copy(recordings = ftsResults) }
                        AppLogger.d(TAG_VIEWMODEL, "FTS search found %d recordings", ftsResults.size)
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG_VIEWMODEL, "FTS search failed", e)
                }
            }
        }
        
        AppLogger.d(TAG_VIEWMODEL, "Filtered recordings -> query: %s, results: %d", query, titleFiltered.size)
        return titleFiltered
    }
}

