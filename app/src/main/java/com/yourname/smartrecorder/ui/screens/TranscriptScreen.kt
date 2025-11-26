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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourname.smartrecorder.domain.usecase.ExportFormat
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
    var currentTab by remember { mutableStateOf(TranscriptTab.TRANSCRIPT) }
    var showExportSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(recordingId) {
        viewModel.loadRecording(recordingId)
    }
    
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false }
        ) {
            ExportBottomSheet(
                onDismiss = { showExportSheet = false },
                onExportClick = { format ->
                    val exportedText = viewModel.exportTranscript(format)
                    if (exportedText != null) {
                        // Copy to clipboard
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Transcript", exportedText)
                        clipboard.setPrimaryClip(clip)
                        
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        showExportSheet = false
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
                    Column {
                        Text(
                            text = recording?.title ?: "Transcript",
                            style = MaterialTheme.typography.titleMedium
                        )
                        recording?.let { rec ->
                            Text(
                                text = "${formatDuration(rec.durationMs)} • ${formatDate(rec.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showExportSheet = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
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
                currentPosMs = uiState.currentPositionMs,
                durationMs = recording?.durationMs ?: 0L,
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onSeekTo = { viewModel.seekTo(it.toLong()) }
            )

            // Tabs
            PrimaryTabRow(
                selectedTabIndex = currentTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                TranscriptTab.values().forEachIndexed { index, tab ->
                    Tab(
                        selected = index == currentTab.ordinal,
                        onClick = { currentTab = tab },
                        text = { Text(tab.name) }
                    )
                }
            }

            // Content
            when (currentTab) {
                TranscriptTab.TRANSCRIPT -> TranscriptTabContent(
                    uiState = uiState,
                    onSegmentClick = { segment ->
                        viewModel.seekTo(segment.startTimeMs)
                    },
                    onGenerateTranscript = {
                        viewModel.generateTranscript()
                    }
                )
                TranscriptTab.NOTES -> NotesTabContent(uiState = uiState)
                TranscriptTab.SUMMARY -> SummaryTabContent(uiState = uiState)
            }
        }
    }
}

@Composable
private fun PlayerBar(
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onSeekTo: (Float) -> Unit
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
    onSegmentClick: (com.yourname.smartrecorder.domain.model.TranscriptSegment) -> Unit,
    onGenerateTranscript: () -> Unit = {}
) {
    if (uiState.segments.isEmpty()) {
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
                } else {
                    Text(
                        "No transcript available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onGenerateTranscript) {
                        Text("Generate Transcript")
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.segments) { segment ->
                TranscriptLineItem(
                    segment = segment,
                    isCurrent = segment.id == uiState.currentSegmentId,
                    onClick = { onSegmentClick(segment) }
                )
            }
        }
    }
}

@Composable
private fun TranscriptLineItem(
    segment: com.yourname.smartrecorder.domain.model.TranscriptSegment,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(bgColor)
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Text(
            text = "[${formatDuration(segment.startTimeMs)}]",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = segment.text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun NotesTabContent(
    uiState: com.yourname.smartrecorder.ui.transcript.TranscriptUiState
) {
    if (uiState.notes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No notes yet",
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
            items(uiState.notes) { note ->
                Card(
                    modifier = Modifier.fillMaxWidth()
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

@Composable
private fun SummaryTabContent(
    uiState: com.yourname.smartrecorder.ui.transcript.TranscriptUiState
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
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
                    modifier = Modifier.fillMaxWidth()
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
                    modifier = Modifier.fillMaxWidth()
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
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

