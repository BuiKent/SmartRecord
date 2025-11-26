package com.yourname.smartrecorder.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Dialog to confirm deletion of recording or other items.
 */
@Composable
fun DeleteConfirmDialog(
    title: String = "Delete Recording?",
    message: String = "This action cannot be undone. The recording and all related data will be permanently deleted.",
    itemName: String? = null, // Optional: show item name in message
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val finalMessage = if (itemName != null) {
        "$message\n\n$itemName"
    } else {
        message
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = finalMessage,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}

