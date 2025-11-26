package com.yourname.smartrecorder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.ui.components.ErrorHandler
import com.yourname.smartrecorder.ui.components.RecordingCard
import com.yourname.smartrecorder.ui.library.LibraryViewModel

@Composable
fun LibraryScreen(
    onRecordingClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
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
                    .padding(innerPadding)
            ) {
            // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search recordings...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredRecordings) { recording ->
                    RecordingCard(
                        recording = recording,
                        onClick = { onRecordingClick(recording.id) },
                        isPlaying = uiState.currentlyPlayingId == recording.id && uiState.isPlaying,
                        onPlayClick = { viewModel.playRecording(recording) },
                        onPauseClick = { viewModel.playRecording(recording) }, // Toggle pause
                        onStopClick = { viewModel.stopPlayback() },
                        isEditing = recording.id == uiState.editingRecordingId,
                        editingTitle = if (recording.id == uiState.editingRecordingId) uiState.editingTitle else recording.title.ifBlank { "Untitled Recording" },
                        onEditClick = { viewModel.startEditing(recording.id) },
                        onTitleChange = { viewModel.updateEditingTitle(it) },
                        onSaveClick = { viewModel.saveEditing() },
                        onCancelClick = { viewModel.cancelEditing() },
                        onDeleteClick = { recordingToDelete -> viewModel.deleteRecording(recordingToDelete) }
                    )
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

