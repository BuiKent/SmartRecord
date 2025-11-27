package com.yourname.smartrecorder.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourname.smartrecorder.domain.usecase.ExportFormat
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import kotlinx.coroutines.delay
import com.yourname.smartrecorder.ui.components.AddBookmarkDialog
import com.yourname.smartrecorder.ui.components.DeleteConfirmDialog
import com.yourname.smartrecorder.ui.components.ErrorHandler
import com.yourname.smartrecorder.ui.components.ExportBottomSheet
import com.yourname.smartrecorder.ui.transcript.TranscriptTab
import com.yourname.smartrecorder.ui.transcript.TranscriptViewModel

@Composable
fun TranscriptScreen(
    recordingId: String,
    onBackClick: () -> Unit,
    onExportClick: () -> Unit,
    viewModel: TranscriptViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigateBack by viewModel.navigateBack.collectAsState()
    var currentTab by remember { mutableStateOf(TranscriptTab.TRANSCRIPT) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Handle navigation back after deletion
    LaunchedEffect(navigateBack) {
        if (navigateBack) {
            viewModel.onNavigationHandled()
            onBackClick()
        }
    }

    LaunchedEffect(recordingId) {
        AppLogger.d(TAG_VIEWMODEL, "[TranscriptScreen] Screen loaded -> recordingId: %s", recordingId)
        viewModel.loadRecording(recordingId)
    }
    
    // Log tab changes
    LaunchedEffect(currentTab) {
        AppLogger.d(TAG_VIEWMODEL, "[TranscriptScreen] User switched tab -> tab: %s", currentTab.name)
    }
    
    // Show toast message for volume warning
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            // Clear toast message after showing
            delay(3500) // Show for 3.5 seconds
            viewModel.clearToastMessage()
        }
    }
    
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                AppLogger.d(TAG_VIEWMODEL, "[TranscriptScreen] Export sheet dismissed")
                showExportSheet = false 
            }
        ) {
            ExportBottomSheet(
                onDismiss = { 
                    AppLogger.d(TAG_VIEWMODEL, "[TranscriptScreen] Export sheet dismissed")
                    showExportSheet = false 
                },
                onExportClick = { format ->
                    AppLogger.d(TAG_VIEWMODEL, "[TranscriptScreen] User requested export -> format: %s", format)
                    val exportedText = viewModel.exportTranscript(format)
                    if (exportedText != null) {
                        // Copy to clipboard
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Transcript", exportedText)
                        clipboard.setPrimaryClip(clip)
                        
                        AppLogger.d(TAG_VIEWMODEL, "[TranscriptScreen] Export copied to clipboard -> format: %s, length: %d chars", 
                            format, exportedText.length)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        showExportSheet = false
                    } else {
                        AppLogger.w(TAG_VIEWMODEL, "[TranscriptScreen] Export failed -> format: %s", format)
                    }
                }
            )
        }
    }

    val recording = uiState.recording
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = recording?.title ?: "Transcript",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { showBookmarkDialog = true }) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Add Bookmark")
                    }
                    IconButton(onClick = { showExportSheet = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Recording",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        snackbarHost = {
            ErrorHandler(
                error = uiState.error,
                onErrorShown = { viewModel.clearError() }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Player bar
            PlayerBar(
                isPlaying = uiState.isPlaying,
                isLooping = uiState.isLooping,
                currentPosMs = uiState.currentPositionMs,
                durationMs = recording?.durationMs ?: 0L,
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onSeekTo = { viewModel.seekTo(it.toLong()) },
                onToggleLoop = { viewModel.toggleLoop() }
            )
            
            // Search bar
            if (showSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.searchInTranscript(it) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("Search in transcript...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true
                    )
                }
            }

            // Tabs
            PrimaryTabRow(
                selectedTabIndex = currentTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                TranscriptTab.values().forEachIndexed { index, tab ->
                    Tab(
                        selected = index == currentTab.ordinal,
                        onClick = { currentTab = tab },
                        text = { 
                            Text(
                                text = tab.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            // Content with floating buttons overlay
            Box(modifier = Modifier.fillMaxSize()) {
                when (currentTab) {
                    TranscriptTab.TRANSCRIPT -> {
                        var showSpeakerMode by remember { mutableStateOf(false) }
                        TranscriptTabContent(
                            uiState = uiState,
                            showSpeakerMode = showSpeakerMode,
                            onToggleSpeakerMode = { showSpeakerMode = !showSpeakerMode },
                            onSegmentClick = { segment ->
                                // Click on segment → Save if editing
                                if (uiState.editingSegmentId != null) {
                                    AppLogger.d(TAG_VIEWMODEL, "[TranscriptScreen] Click on segment while editing -> saving")
                                    viewModel.saveEditing()
                                }
                                viewModel.seekTo(segment.startTimeMs)
                            },
                            onGenerateTranscript = {
                                // Click generate → Save if editing
                                if (uiState.editingSegmentId != null) {
                                    AppLogger.d(TAG_VIEWMODEL, "[TranscriptScreen] Click generate while editing -> saving")
                                    viewModel.saveEditing()
                                }
                                AppLogger.d(TAG_VIEWMODEL, "[TranscriptScreen] User clicked Generate Transcript button")
                                viewModel.generateTranscript()
                            },
                            onEditClick = { segmentId -> 
                                // Click edit → Save if editing another segment
                                if (uiState.editingSegmentId != null && uiState.editingSegmentId != segmentId) {
                                    AppLogger.d(TAG_VIEWMODEL, "[TranscriptScreen] Click edit on different segment while editing -> saving")
                                    viewModel.saveEditing()
                                }
                                viewModel.startEditing(segmentId) 
                            },
                            onTextChange = { text -> viewModel.updateEditingText(text) },
                            onSaveClick = { viewModel.saveEditing() },
                            onCancelClick = { viewModel.cancelEditing() }
                        )
                        
                        // Floating action buttons at bottom right
                        FloatingActionButtons(
                            showSpeakerMode = showSpeakerMode,
                            onToggleSpeakerMode = { showSpeakerMode = !showSpeakerMode },
                            onCopyClick = {
                                val exportedText = if (showSpeakerMode) {
                                    // Copy TXT format (like Share → TXT)
                                    viewModel.exportTranscript(ExportFormat.TXT)
                                } else {
                                    // Copy Subtitle format (like Share → Subtitle)
                                    viewModel.exportTranscript(ExportFormat.SRT)
                                }
                                if (exportedText != null) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Transcript", exportedText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    AppLogger.logMain(TAG_VIEWMODEL, "[TranscriptScreen] Copied to clipboard -> mode: %s, length: %d", 
                                        if (showSpeakerMode) "TXT" else "SRT", exportedText.length)
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                    TranscriptTab.NOTES -> NotesTabContent(
                        uiState = uiState,
                        bookmarks = uiState.bookmarks,
                        onBookmarkClick = { bookmark ->
                            viewModel.seekTo(bookmark.timestampMs)
                        }
                    )
                    TranscriptTab.SUMMARY -> SummaryTabContent(
                        uiState = uiState,
                        onGenerateFlashcards = {
                            viewModel.generateFlashcards()
                        }
                    )
                }
            }
        }
    }
    
    // Bookmark dialog
    if (showBookmarkDialog) {
        AddBookmarkDialog(
            timestamp = formatDuration(uiState.currentPositionMs),
            onDismiss = { showBookmarkDialog = false },
            onConfirm = { note ->
                viewModel.addBookmark(note)
                showBookmarkDialog = false
            }
        )
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog && uiState.recording != null) {
        DeleteConfirmDialog(
            title = "Delete Recording?",
            message = "This action cannot be undone. The recording and all related data (transcript, notes, bookmarks) will be permanently deleted.",
            itemName = uiState.recording?.title?.takeIf { it.isNotBlank() },
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deleteRecording()
                showDeleteDialog = false
            }
        )
    }
}

@Composable
fun PlayerBar(
    isPlaying: Boolean,
    isLooping: Boolean = false,
    currentPosMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onToggleLoop: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause"
                )
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Slider(
                    value = currentPosMs.toFloat(),
                    onValueChange = onSeekTo,
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDuration(currentPosMs),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        formatDuration(durationMs),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptTabContent(
    uiState: com.yourname.smartrecorder.ui.transcript.TranscriptUiState,
    showSpeakerMode: Boolean = false,
    onToggleSpeakerMode: () -> Unit = {},
    onSegmentClick: (com.yourname.smartrecorder.domain.model.TranscriptSegment) -> Unit,
    onGenerateTranscript: () -> Unit = {},
    onEditClick: (Long) -> Unit = {},
    onTextChange: (String) -> Unit = {},
    onSaveClick: () -> Unit = {},
    onCancelClick: () -> Unit = {}
) {
    val segmentsToShow = if (uiState.searchQuery.isNotEmpty() && uiState.searchResults.isNotEmpty()) {
        uiState.searchResults
    } else {
        uiState.segments
    }
    
    // Log for debugging
    LaunchedEffect(segmentsToShow.size, uiState.isGeneratingTranscript, uiState.isLoading) {
        AppLogger.d(TAG_VIEWMODEL, "[TranscriptTabContent] State -> segmentsCount: %d, isGenerating: %b, isLoading: %b, searchQuery: %s", 
            segmentsToShow.size, uiState.isGeneratingTranscript, uiState.isLoading, uiState.searchQuery)
    }
    
    if (segmentsToShow.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                if (uiState.isGeneratingTranscript) {
                    CircularProgressIndicator()
                    Text(
                        "Generating transcript... ${uiState.transcriptProgress}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (uiState.isLoading) {
                    CircularProgressIndicator()
                    Text(
                        "Loading transcript...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "No transcript available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Hide Generate Transcript button if auto-transcription on upload is enabled
                    // User should upload file to auto-transcribe, not manually generate
                    // Button(onClick = onGenerateTranscript) {
                    //     Text("Generate Transcript")
                    // }
                    Text(
                        "Transcript will be generated automatically when you upload an audio file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Removed old icon button - now using floating buttons
            if (uiState.searchQuery.isNotEmpty() && uiState.searchResults.isNotEmpty()) {
                item {
                    Text(
                        "Search results (${uiState.searchResults.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(segmentsToShow) { segment ->
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TranscriptLineItem(
                        segment = segment,
                        isCurrent = segment.id == uiState.currentSegmentId,
                        isHighlighted = uiState.searchQuery.isNotEmpty() && 
                            segment.text.lowercase().contains(uiState.searchQuery.lowercase()),
                        searchQuery = uiState.searchQuery,
                        showSpeaker = showSpeakerMode,
                        isEditing = segment.id == uiState.editingSegmentId,
                        editingText = if (segment.id == uiState.editingSegmentId) uiState.editingText else segment.text,
                    onClick = { onSegmentClick(segment) },
                    onEditClick = { onEditClick(segment.id) },
                    onTextChange = onTextChange,
                    onSaveClick = onSaveClick,
                    onCancelClick = onCancelClick
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptLineItem(
    segment: com.yourname.smartrecorder.domain.model.TranscriptSegment,
    isCurrent: Boolean,
    isHighlighted: Boolean = false,
    searchQuery: String = "",
    showSpeaker: Boolean = false,
    isEditing: Boolean = false,
    editingText: String = "",
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onTextChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val bgColor = when {
        isEditing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        isHighlighted -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
        else -> Color.Transparent
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Show speaker label if showSpeaker is true, otherwise show timeline
            if (showSpeaker) {
                // Show speaker label (even if null, show "Unknown Speaker")
                Text(
                    text = if (segment.speaker != null) {
                        "Speaker ${segment.speaker}:"
                    } else {
                        "Unknown Speaker:"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            } else {
                // Show timeline
                Text(
                    text = "[${formatDuration(segment.startTimeMs)}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            
            // Edit mode: show TextField
            if (isEditing) {
                var hasFocus by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = editingText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            val wasFocused = hasFocus
                            hasFocus = focusState.isFocused
                            // Focus lost → Save if was previously focused
                            if (wasFocused && !focusState.isFocused) {
                                AppLogger.d(TAG_VIEWMODEL, "[TranscriptLineItem] TextField lost focus -> saving")
                                onSaveClick()
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onSaveClick() }
                    ),
                    singleLine = false,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            } else {
                // Normal mode: show text with click to seek
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                ) {
                    // Highlight search query in text
                    if (isHighlighted && searchQuery.isNotEmpty()) {
                        HighlightedText(
                            text = segment.text,
                            query = searchQuery
                        )
                    } else {
                        Text(
                            text = segment.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        
        // Edit/Check icon
        Spacer(modifier = Modifier.width(8.dp))
        if (isEditing) {
            IconButton(
                onClick = onSaveClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HighlightedText(text: String, query: String) {
    val queryLower = query.lowercase()
    val textLower = text.lowercase()
    val parts = mutableListOf<Pair<String, Boolean>>()
    var lastIndex = 0
    
    var index = textLower.indexOf(queryLower, lastIndex)
    while (index != -1) {
        if (index > lastIndex) {
            parts.add(Pair(text.substring(lastIndex, index), false))
        }
        parts.add(Pair(text.substring(index, index + query.length), true))
        lastIndex = index + query.length
        index = textLower.indexOf(queryLower, lastIndex)
    }
    if (lastIndex < text.length) {
        parts.add(Pair(text.substring(lastIndex), false))
    }
    
    Text(
        buildAnnotatedString {
            parts.forEach { (part, isHighlighted) ->
                withStyle(
                    style = SpanStyle(
                        background = if (isHighlighted) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        } else {
                            Color.Transparent
                        }
                    )
                ) {
                    append(part)
                }
            }
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun NotesTabContent(
    uiState: com.yourname.smartrecorder.ui.transcript.TranscriptUiState,
    bookmarks: List<com.yourname.smartrecorder.domain.model.Bookmark> = emptyList(),
    onBookmarkClick: (com.yourname.smartrecorder.domain.model.Bookmark) -> Unit = {}
) {
    if (uiState.notes.isEmpty() && bookmarks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No notes or bookmarks yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Bookmarks section
            if (bookmarks.isNotEmpty()) {
                item {
                    Text(
                        "Bookmarks (${bookmarks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(bookmarks) { bookmark ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBookmarkClick(bookmark) },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDuration(bookmark.timestampMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (bookmark.note.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = bookmark.note,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
            
            // Notes section
            if (uiState.notes.isNotEmpty()) {
                item {
                    Text(
                        "Notes (${uiState.notes.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(uiState.notes) { note ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = note.type,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.content,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTabContent(
    uiState: com.yourname.smartrecorder.ui.transcript.TranscriptUiState,
    onGenerateFlashcards: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Summary",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.summary.ifBlank { "No summary available" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Keywords
        if (uiState.keywords.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Keywords",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.keywords.forEach { keyword ->
                                AssistChip(
                                    onClick = { },
                                    label = { Text(keyword) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Questions
        if (uiState.questions.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Questions (${uiState.questions.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        uiState.questions.forEach { question ->
                            Text(
                                text = "• ${question.text}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // Generate Flashcards button
        if (uiState.segments.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Study with Flashcards",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Generate flashcards from this transcript to practice and review",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (uiState.isGeneratingFlashcards) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(
                                text = "Generating flashcards...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else if (uiState.flashcardsGenerated) {
                            Text(
                                text = "✓ Flashcards generated! Go to Study tab to practice",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Button(
                                onClick = onGenerateFlashcards,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Generate Flashcards")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingActionButtons(
    showSpeakerMode: Boolean,
    onToggleSpeakerMode: () -> Unit,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Copy button
        FloatingActionButton(
            onClick = onCopyClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = if (showSpeakerMode) "Copy TXT" else "Copy Subtitle",
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Subtitle/Timeline button (when not in speaker mode) or People button (when in speaker mode)
        FloatingActionButton(
            onClick = onToggleSpeakerMode,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            containerColor = if (showSpeakerMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (showSpeakerMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                imageVector = if (showSpeakerMode) Icons.Default.Person else Icons.Default.Subtitles,
                contentDescription = if (showSpeakerMode) "Show timeline" else "Show speakers",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

