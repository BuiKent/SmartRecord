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
    val liveText: String = "",
    val error: String? = null,
    val amplitude: Int = 0,  // For waveform visualization
    val isModelReady: Boolean = false  // Whisper model ready state (only for transcription features)
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
    var showBookmarkDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
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

        Spacer(modifier = Modifier.height(32.dp))

        // Waveform visualization
        WaveformVisualizer(
            amplitude = uiState.amplitude,
            isRecording = isRecording
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Timer display
        Text(
            text = formatDuration(uiState.durationMs),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Big floating circular mic button
        FloatingActionButton(
            onClick = when {
                isRecording -> onPauseRecordClick  // Pause if recording
                isPaused -> onPauseRecordClick     // Resume if paused
                else -> onStartRecordClick         // Start if not started
            },
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = when {
                    isRecording -> Icons.Default.Pause
                    isPaused -> Icons.Default.PlayArrow  // Show Play icon when paused (click to resume)
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

        Spacer(modifier = Modifier.height(16.dp))

        // Control buttons (show when recording OR paused)
        if (hasActiveRecording) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bookmark button - icon only to avoid text cutoff
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
                
                // Stop button
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

        Spacer(modifier = Modifier.height(32.dp))

        // Import/Transcribe progress card - simplified with color fill (blue to red)
        if (importState != null && (importState.isImporting || importState.isTranscribing)) {
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

        // Spacer to push cards to bottom (only when not recording)
        if (!hasActiveRecording) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Import / Realtime cards - vertical layout, hide when transcribing
        if (importState?.isTranscribing != true) {
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

