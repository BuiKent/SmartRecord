package com.yourname.smartrecorder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

data class RecordUiState(
    val isRecording: Boolean = false,
    val durationMs: Long = 0L,
    val liveText: String = "",
    val error: String? = null
)

@Composable
fun RecordScreen(
    uiState: RecordUiState = RecordUiState(),
    onStartRecordClick: () -> Unit,
    onPauseRecordClick: () -> Unit,
    onStopRecordClick: () -> Unit,
    onImportAudioClick: () -> Unit,
    onRealtimeSttClick: () -> Unit
) {
    val isRecording = uiState.isRecording

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

        // Waveform placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRecording) "Recording..." else "Ready to record",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Timer display
        Text(
            text = formatDuration(uiState.durationMs),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Big mic button
        ElevatedButton(
            onClick = if (isRecording) onPauseRecordClick else onStartRecordClick,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.size(96.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Pause else Icons.Default.Mic,
                contentDescription = if (isRecording) "Pause" else "Start Record",
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isRecording) "Tap to pause" else "Tap to start recording",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Stop button (only show when recording)
        if (isRecording) {
            OutlinedButton(
                onClick = onStopRecordClick
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop and save")
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
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

