package com.yourname.smartrecorder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.ui.components.ErrorHandler
import com.yourname.smartrecorder.ui.components.RecordingCard
import com.yourname.smartrecorder.ui.components.SimplePlaybackBar
import com.yourname.smartrecorder.ui.library.LibraryViewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import com.yourname.smartrecorder.domain.state.PlaybackState

@Composable
fun LibraryScreen(
    onRecordingClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val filteredRecordings = viewModel.getFilteredRecordings()
    val context = LocalContext.current

    // Show toast message for volume warning
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            // Clear toast message after showing
            kotlinx.coroutines.delay(3500) // Show for 3.5 seconds
            viewModel.clearToastMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    IconButton(
                        onClick = {
                            AppLogger.d(TAG_VIEWMODEL, "[LibraryScreen] User clicked settings icon")
                            onSettingsClick()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                        top = innerPadding.calculateTopPadding(),
                        end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                        bottom = 0.dp  // Bỏ bottom padding để tránh che content
                    )
            ) {
            // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("Search recordings...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true
        )

        // Recording list
        if (uiState.isLoading && filteredRecordings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredRecordings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    Text(
                        text = if (uiState.searchQuery.isNotEmpty()) {
                            "No recordings found"
                        } else {
                            "No recordings yet"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Calculate bottom padding for spacer (only shows when scrolled to bottom)
            // Pattern from Numerology: chỉ tính navBarsBottom + spacing, bỏ bottomBarHeight
            val navBarsBottom = WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding()
            val spacing = 8.dp
            val bottomSpacing = navBarsBottom + spacing  // Bỏ bottomBarHeight - không cần padding cho bottom bar
            
            // LazyListState để quản lý scroll position và auto-scroll đến item đang play
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            
            // Auto-scroll đến item đang play khi quay lại màn hình hoặc khi playback state thay đổi
            LaunchedEffect(playbackState, filteredRecordings.size) {
                val currentState = playbackState  // Lưu vào biến local để smart cast
                when (currentState) {
                    is PlaybackState.Playing -> {
                        val playingRecordingId = currentState.recordingId
                        val playingIndex = filteredRecordings.indexOfFirst { it.id == playingRecordingId }
                        if (playingIndex >= 0) {
                            // Scroll đến item đang play với animation mượt
                            coroutineScope.launch {
                                listState.animateScrollToItem(
                                    index = playingIndex,
                                    scrollOffset = 0
                                )
                                AppLogger.d(TAG_VIEWMODEL, "[LibraryScreen] Auto-scrolled to playing item -> index: %d, recordingId: %s", 
                                    playingIndex, playingRecordingId)
                            }
                        }
                    }
                    is PlaybackState.Paused -> {
                        val playingRecordingId = currentState.recordingId
                        val playingIndex = filteredRecordings.indexOfFirst { it.id == playingRecordingId }
                        if (playingIndex >= 0) {
                            // Scroll đến item đang pause với animation mượt
                            coroutineScope.launch {
                                listState.animateScrollToItem(
                                    index = playingIndex,
                                    scrollOffset = 0
                                )
                                AppLogger.d(TAG_VIEWMODEL, "[LibraryScreen] Auto-scrolled to paused item -> index: %d, recordingId: %s", 
                                    playingIndex, playingRecordingId)
                            }
                        }
                    }
                    is PlaybackState.Idle -> {
                        // Không scroll khi idle
                    }
                }
            }
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 0.dp  // No bottom padding - only spacer at end
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredRecordings,
                    key = { recording -> recording.id }  // Key để có thể scroll đến item cụ thể
                ) { recording ->
                    val currentPlaybackState = playbackState
                    val isCurrentlyPlaying = when (currentPlaybackState) {
                        is PlaybackState.Playing -> currentPlaybackState.recordingId == recording.id
                        is PlaybackState.Paused -> currentPlaybackState.recordingId == recording.id
                        is PlaybackState.Idle -> false
                    }
                    
                    val positionMs = if (isCurrentlyPlaying) {
                        when (val state = currentPlaybackState) {
                            is PlaybackState.Playing -> state.positionMs
                            is PlaybackState.Paused -> state.positionMs
                            is PlaybackState.Idle -> 0L
                        }
                    } else {
                        0L
                    }
                    
                    val isPlaying = currentPlaybackState is PlaybackState.Playing && 
                        currentPlaybackState.recordingId == recording.id
                    
                    // Luôn hiển thị RecordingCard, SimplePlaybackBar sẽ hiển thị bên trong card
                    RecordingCard(
                        recording = recording,
                        onClick = { onRecordingClick(recording.id) },
                        isActive = isCurrentlyPlaying,  // true khi Playing hoặc Paused (để hiển thị SimplePlaybackBar)
                        isPlaying = isPlaying,  // true chỉ khi Playing (để hiển thị icon đúng)
                        positionMs = positionMs,
                        onPlayClick = { viewModel.playRecording(recording) },
                        onPauseClick = { viewModel.pausePlayback() },
                        onStopClick = { viewModel.stopPlayback() },
                        onSeekTo = { newPosition -> viewModel.seekTo(newPosition) },
                        isEditing = recording.id == uiState.editingRecordingId,
                        editingTitle = if (recording.id == uiState.editingRecordingId) uiState.editingTitle else recording.title.ifBlank { "Untitled Recording" },
                        onEditClick = { viewModel.startEditing(recording.id) },
                        onTitleChange = { viewModel.updateEditingTitle(it) },
                        onSaveClick = { viewModel.saveEditing() },
                        onCancelClick = { viewModel.cancelEditing() },
                        onDeleteClick = { recordingToDelete -> viewModel.deleteRecording(recordingToDelete) }
                    )
                }
                
                // Spacer at the end - only visible when scrolled to bottom
                // Pattern from Numerology: padding chỉ hiển thị khi scroll đến cuối
                item {
                    Spacer(modifier = Modifier.height(bottomSpacing))
                }
            }
        }
        }
        
        // Error handling
        ErrorHandler(
            error = uiState.error,
            onErrorShown = { viewModel.clearError() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        }
    }
}

