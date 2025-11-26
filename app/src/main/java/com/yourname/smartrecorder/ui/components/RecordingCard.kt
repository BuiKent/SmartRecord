package com.yourname.smartrecorder.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.domain.model.Recording
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RecordingCard(
    recording: Recording,
    onClick: () -> Unit,
    isPlaying: Boolean = false,
    onPlayClick: () -> Unit = {},
    onPauseClick: () -> Unit = {},
    onStopClick: () -> Unit = {},
    onEditTitleClick: (String) -> Unit = {},
    onDeleteClick: (Recording) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title row (clickable to open transcript)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { 
                        AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User clicked recording -> id: %s, title: %s", 
                            recording.id, recording.title)
                        onClick() 
                    }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = recording.title.ifBlank { "Untitled Recording" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row {
                        Text(
                            text = formatDuration(recording.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDate(recording.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Edit button
                IconButton(
                    onClick = { 
                        AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User clicked edit button -> recordingId: %s", recording.id)
                        showEditDialog = true 
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Delete button
                IconButton(
                    onClick = { 
                        AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User clicked delete button -> recordingId: %s", recording.id)
                        showDeleteDialog = true 
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Recording",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Play/Pause/Stop controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/Pause button
                IconButton(
                    onClick = { 
                        AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User clicked %s -> recordingId: %s", 
                            if (isPlaying) "pause" else "play", recording.id)
                        if (isPlaying) onPauseClick() else onPlayClick() 
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Stop button
                IconButton(
                    onClick = { 
                        AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User clicked stop -> recordingId: %s", recording.id)
                        onStopClick() 
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                
                // Transcript button
                OutlinedButton(
                    onClick = { 
                        AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User clicked transcript button -> recordingId: %s", recording.id)
                        onClick() 
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Transcript",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
    
    // Edit title dialog
    if (showEditDialog) {
        EditTitleDialog(
            currentTitle = recording.title.ifBlank { "Untitled Recording" },
            onDismiss = { showEditDialog = false },
            onConfirm = { newTitle ->
                AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User confirmed title edit -> recordingId: %s, oldTitle: %s, newTitle: %s", 
                    recording.id, recording.title, newTitle)
                onEditTitleClick(newTitle)
                showEditDialog = false
            }
        )
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        DeleteConfirmDialog(
            title = "Delete Recording?",
            message = "This action cannot be undone. The recording and all related data (transcript, notes, bookmarks) will be permanently deleted.",
            itemName = recording.title.takeIf { it.isNotBlank() },
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User confirmed deletion -> recordingId: %s", recording.id)
                onDeleteClick(recording)
                showDeleteDialog = false
            }
        )
    }
}

@Composable
fun EditTitleDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var titleText by remember { mutableStateOf(currentTitle) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Title") },
        text = {
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text("Recording Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(titleText) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { 
                AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User cancelled title edit")
                onDismiss() 
            }) {
                Text("Cancel")
            }
        }
    )
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

