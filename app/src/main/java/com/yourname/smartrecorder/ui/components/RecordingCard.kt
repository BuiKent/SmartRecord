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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.utils.TimeFormatter
import com.yourname.smartrecorder.domain.model.Recording
import com.yourname.smartrecorder.ui.player.RecordingPlayerBar
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RecordingCard(
    recording: Recording,
    onClick: () -> Unit,
    isPlaying: Boolean = false,
    positionMs: Long = 0L,
    onPlayClick: () -> Unit = {},
    onPauseClick: () -> Unit = {},
    onStopClick: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    isEditing: Boolean = false,
    editingTitle: String = "",
    onEditClick: () -> Unit = {},
    onTitleChange: (String) -> Unit = {},
    onSaveClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onDeleteClick: (Recording) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    
    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title row (clickable to open transcript, or editable)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Edit mode: show TextField
                    if (isEditing) {
                        OutlinedTextField(
                            value = editingTitle,
                            onValueChange = onTitleChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.titleMedium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { onSaveClick() }
                            ),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        )
                    } else {
                        // Normal mode: show text with click to open transcript
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { 
                                    AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User clicked recording -> id: %s, title: %s", 
                                        recording.id, recording.title)
                                    onClick() 
                                })
                        ) {
                            Text(
                                text = recording.title.ifBlank { "Untitled Recording" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row {
                        Text(
                            text = TimeFormatter.formatTime(recording.durationMs),
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
                
                // Edit/Save button
                if (isEditing) {
                    IconButton(
                        onClick = { 
                            AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User clicked save button -> recordingId: %s", recording.id)
                            onSaveClick() 
                        },
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
                        onClick = { 
                            AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User clicked edit button -> recordingId: %s", recording.id)
                            onEditClick() 
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Title",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
            
            // Hiển thị RecordingPlayerBar khi đang play, nếu không thì hiển thị Play button và Transcript button
            if (isPlaying) {
                // RecordingPlayerBar với kích thước compact (nhỏ hơn) cho History card
                RecordingPlayerBar(
                    title = recording.title.ifBlank { "Untitled Recording" },
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = recording.durationMs,
                    onPlayPauseClick = {
                        AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User clicked pause in player bar -> recordingId: %s", recording.id)
                        onPauseClick()
                    },
                    onSeekTo = { newPosition ->
                        AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User seeked to %d ms -> recordingId: %s", newPosition, recording.id)
                        onSeekTo(newPosition)
                    },
                    isCompact = true, // Compact mode cho History card
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Play/Pause/Stop controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play button - bo tròn, màu cam để thống nhất
                    IconButton(
                        onClick = { 
                            AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User clicked play -> recordingId: %s", recording.id)
                            onPlayClick() 
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White, // Icon trắng trên nền cam
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Transcript button
                    OutlinedButton(
                        onClick = { 
                            AppLogger.d(TAG_VIEWMODEL, "[RecordingCard] User clicked transcript button -> recordingId: %s", recording.id)
                            onClick() 
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
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

// formatDuration đã được thay thế bằng TimeFormatter.formatTime()
// Giữ lại comment này để reference

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

