package com.yourname.smartrecorder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourname.smartrecorder.ui.components.AddBookmarkDialog
import com.yourname.smartrecorder.ui.components.WaveformVisualizer

data class RecordUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,  // Track if recording is paused
    val durationMs: Long = 0L,
    val liveText: String = "",  // Live transcript text from ASR
    val partialText: String = "",  // Partial (real-time) text
    val error: String? = null,
    val amplitude: Int = 0,  // For waveform visualization
    val isModelReady: Boolean = false,  // Whisper model ready state (only for transcription features)
    val isLiveTranscribeMode: Boolean = false  // Live transcribe mode (ASR active)
) {
    // Check if there's an active recording session (paused or recording)
    val hasActiveRecording: Boolean
        get() = isRecording || isPaused
}

@Composable
fun RecordScreen(
    uiState: RecordUiState = RecordUiState(),
    onStartRecordClick: () -> Unit,
    onPauseRecordClick: () -> Unit,
    onStopRecordClick: () -> Unit,
    onImportAudioClick: () -> Unit,
    onRealtimeSttClick: () -> Unit,
    onBookmarkClick: (String) -> Unit = { _ -> },
    importState: com.yourname.smartrecorder.ui.importaudio.ImportUiState? = null
) {
    val isRecording = uiState.isRecording
    val isPaused = uiState.isPaused
    val hasActiveRecording = uiState.hasActiveRecording
    val isLiveTranscribe = uiState.isLiveTranscribeMode
    var showBookmarkDialog by remember { mutableStateOf(false) }
    
    // Display text: final + partial
    val displayText = if (uiState.partialText.isNotEmpty()) {
        if (uiState.liveText.isNotEmpty()) {
            "${uiState.liveText} ${uiState.partialText}"
        } else {
            uiState.partialText
        }
    } else {
        uiState.liveText
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isLiveTranscribe) 16.dp else 24.dp, vertical = if (isLiveTranscribe) 8.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title - hide in live transcribe mode
        if (!isLiveTranscribe) {
            Text(
                text = "Smart Recorder",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Record, transcribe & smart notes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(if (isLiveTranscribe) 8.dp else 32.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Waveform visualization - smaller in live transcribe mode
        WaveformVisualizer(
            amplitude = uiState.amplitude,
            isRecording = isRecording || isLiveTranscribe,
            modifier = Modifier.height(if (isLiveTranscribe) 60.dp else 120.dp)
        )

        Spacer(modifier = Modifier.height(if (isLiveTranscribe) 8.dp else 32.dp))

        // Timer display - smaller in live transcribe mode
        if (!isLiveTranscribe) {
            Text(
                text = formatDuration(uiState.durationMs),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Control buttons - compact in live transcribe mode
        if (isLiveTranscribe || hasActiveRecording) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLiveTranscribe) {
                    // Live transcribe mode: Pause and Stop buttons (small, horizontal)
                    IconButton(
                        onClick = onPauseRecordClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onStopRecordClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    // Normal recording mode: Big mic button
                    FloatingActionButton(
                        onClick = when {
                            isRecording -> onPauseRecordClick
                            isPaused -> onPauseRecordClick
                            else -> onStartRecordClick
                        },
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = when {
                                isRecording -> Icons.Default.Pause
                                isPaused -> Icons.Default.PlayArrow
                                else -> Icons.Default.Mic
                            },
                            contentDescription = when {
                                isRecording -> "Pause"
                                isPaused -> "Resume"
                                else -> "Start Record"
                            },
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Bookmark and Stop buttons
                    OutlinedButton(
                        onClick = { showBookmarkDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Add Bookmark",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Bookmark",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                    
                    OutlinedButton(
                        onClick = onStopRecordClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop")
                    }
                }
            }
        } else {
            // Big floating circular mic button when not recording
            FloatingActionButton(
                onClick = onStartRecordClick,
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Start Record",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isLiveTranscribe) 8.dp else 16.dp))

        // Live text box - show in live transcribe mode
        if (isLiveTranscribe) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Live Transcript",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (displayText.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Listening... Speak now",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Show final text + partial text (partial in different color)
                        if (uiState.partialText.isNotEmpty() && uiState.liveText.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = uiState.liveText,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = uiState.partialText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Import/Transcribe progress card - hide in live transcribe mode
        if (!isLiveTranscribe && importState != null && (importState.isImporting || importState.isTranscribing)) {
            val progress = importState.progress / 100f
            // Interpolate color from blue to red based on progress
            val blueColor = Color(0xFF2196F3) // Blue
            val redColor = MaterialTheme.colorScheme.error // Red
            val color = Color(
                red = blueColor.red + (redColor.red - blueColor.red) * progress,
                green = blueColor.green + (redColor.green - blueColor.green) * progress,
                blue = blueColor.blue + (redColor.blue - blueColor.blue) * progress,
                alpha = 1f
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = color
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (importState.isTranscribing) "Transcribing..." else "Uploading...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = " ${importState.progress}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Spacer to push cards to bottom (only when not recording and not live transcribe)
        if (!hasActiveRecording && !isLiveTranscribe) {
            Spacer(modifier = Modifier.weight(1f))
        } else if (!isLiveTranscribe) {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Import / Realtime cards - hide in live transcribe mode
        if (!isLiveTranscribe && importState?.isTranscribing != true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Upload card - compact with elevation and better colors
                Card(
                    onClick = onImportAudioClick,
                    enabled = importState?.isImporting != true,
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Upload audio file",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Convert to transcript",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Live Transcribe card - compact with elevation and better colors
                Card(
                    onClick = onRealtimeSttClick,
                    enabled = uiState.isModelReady,
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Live Transcribe",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Record and transcribe in real-time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Bookmark dialog
    if (showBookmarkDialog) {
        AddBookmarkDialog(
            timestamp = formatDuration(uiState.durationMs),
            onDismiss = { showBookmarkDialog = false },
            onConfirm = { note ->
                onBookmarkClick(note)
                showBookmarkDialog = false
            }
        )
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

