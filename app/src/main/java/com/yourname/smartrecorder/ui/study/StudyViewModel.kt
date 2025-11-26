package com.yourname.smartrecorder.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.domain.model.Flashcard
import com.yourname.smartrecorder.domain.usecase.GenerateFlashcardsUseCase
import com.yourname.smartrecorder.domain.usecase.GetFlashcardsUseCase
import com.yourname.smartrecorder.domain.usecase.GetRecordingDetailUseCase
import com.yourname.smartrecorder.domain.usecase.GetTranscriptUseCase
import com.yourname.smartrecorder.domain.usecase.UpdateFlashcardDifficultyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudyUiState(
    val flashcards: List<Flashcard> = emptyList(),
    val currentFlashcardIndex: Int = -1,
    val showAnswer: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StudyViewModel @Inject constructor(
    private val getFlashcards: GetFlashcardsUseCase,
    private val updateFlashcardDifficulty: UpdateFlashcardDifficultyUseCase,
    private val generateFlashcards: GenerateFlashcardsUseCase,
    private val getRecordingDetail: GetRecordingDetailUseCase,
    private val getTranscript: GetTranscriptUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()
    
    fun loadFlashcardsForReview() {
        AppLogger.logViewModel(TAG_VIEWMODEL, "StudyViewModel", "loadFlashcardsForReview", null)
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val flashcards = getFlashcards.getForReview(limit = 20)
                _uiState.update { 
                    it.copy(
                        flashcards = flashcards,
                        currentFlashcardIndex = if (flashcards.isNotEmpty()) 0 else -1,
                        showAnswer = false,
                        isLoading = false
                    )
                }
                AppLogger.logViewModel(TAG_VIEWMODEL, "StudyViewModel", "Flashcards loaded", 
                    "count=${flashcards.size}")
            } catch (e: Exception) {
                AppLogger.e(TAG_VIEWMODEL, "Failed to load flashcards", e)
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
    
    fun generateFlashcardsForRecording(recordingId: String) {
        AppLogger.logViewModel(TAG_VIEWMODEL, "StudyViewModel", "generateFlashcardsForRecording", 
            "recordingId=$recordingId")
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                val recording = getRecordingDetail(recordingId) ?: run {
                    _uiState.update { it.copy(error = "Recording not found", isLoading = false) }
                    return@launch
                }
                
                val segments = getTranscript.invokeSync(recordingId)
                val flashcards = generateFlashcards(recordingId, segments)
                
                _uiState.update { 
                    it.copy(
                        flashcards = flashcards,
                        currentFlashcardIndex = if (flashcards.isNotEmpty()) 0 else -1,
                        showAnswer = false,
                        isLoading = false
                    )
                }
                AppLogger.logViewModel(TAG_VIEWMODEL, "StudyViewModel", "Flashcards generated", 
                    "count=${flashcards.size}")
            } catch (e: Exception) {
                AppLogger.e(TAG_VIEWMODEL, "Failed to generate flashcards", e)
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
    
    fun revealAnswer() {
        _uiState.update { it.copy(showAnswer = true) }
    }
    
    fun nextFlashcard() {
        val currentIndex = _uiState.value.currentFlashcardIndex
        val flashcards = _uiState.value.flashcards
        if (currentIndex < flashcards.size - 1) {
            _uiState.update { 
                it.copy(
                    currentFlashcardIndex = currentIndex + 1,
                    showAnswer = false
                )
            }
        }
    }
    
    fun previousFlashcard() {
        val currentIndex = _uiState.value.currentFlashcardIndex
        if (currentIndex > 0) {
            _uiState.update { 
                it.copy(
                    currentFlashcardIndex = currentIndex - 1,
                    showAnswer = false
                )
            }
        }
    }
    
    fun rateFlashcard(difficulty: Int) {
        val currentIndex = _uiState.value.currentFlashcardIndex
        val flashcards = _uiState.value.flashcards
        if (currentIndex < 0 || currentIndex >= flashcards.size) return
        
        val flashcard = flashcards[currentIndex]
        AppLogger.logViewModel(TAG_VIEWMODEL, "StudyViewModel", "rateFlashcard", 
            "flashcardId=${flashcard.id}, difficulty=$difficulty")
        
        viewModelScope.launch {
            try {
                updateFlashcardDifficulty(flashcard, difficulty, isCorrect = difficulty <= 2)
                nextFlashcard()
            } catch (e: Exception) {
                AppLogger.e(TAG_VIEWMODEL, "Failed to update flashcard difficulty", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun getCurrentFlashcard(): Flashcard? {
        val index = _uiState.value.currentFlashcardIndex
        val flashcards = _uiState.value.flashcards
        return if (index >= 0 && index < flashcards.size) flashcards[index] else null
    }
}

