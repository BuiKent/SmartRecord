package com.yourname.smartrecorder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
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
    onBookmarkClick: (String) -> Unit = { _ -> }
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

        // Big mic button (always enabled - recording doesn't need model)
        ElevatedButton(
            onClick = when {
                isRecording -> onPauseRecordClick  // Pause if recording
                isPaused -> onPauseRecordClick     // Resume if paused
                else -> onStartRecordClick         // Start if not started
            },
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.size(96.dp),
            contentPadding = PaddingValues(0.dp)
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
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when {
                isRecording -> "Tap to pause"
                isPaused -> "Tap to resume"
                else -> "Tap to start recording"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Control buttons (show when recording OR paused)
        if (hasActiveRecording) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bookmark button
                OutlinedButton(
                    onClick = { showBookmarkDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Bookmark"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bookmark")
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

        // Import / Realtime buttons
        Text(
            text = "Or choose another option:",
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onImportAudioClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Upload",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            OutlinedButton(
                onClick = onRealtimeSttClick,
                enabled = uiState.isModelReady,  // Only disable transcribe if model not ready
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Transcribe",
                    style = MaterialTheme.typography.labelMedium
                )
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

