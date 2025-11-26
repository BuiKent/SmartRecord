package com.yourname.smartrecorder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourname.smartrecorder.domain.usecase.ExportFormat

@Composable
fun ExportBottomSheet(
    onDismiss: () -> Unit,
    onExportClick: (ExportFormat) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Export & Share",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ExportOption(
            title = "Plain Text (.txt)",
            description = "Simple text format",
            onClick = { onExportClick(ExportFormat.TXT) }
        )
        
        ExportOption(
            title = "Markdown (.md)",
            description = "Formatted markdown document",
            onClick = { onExportClick(ExportFormat.MARKDOWN) }
        )
        
        ExportOption(
            title = "Subtitle (.srt)",
            description = "Subtitle file with timestamps",
            onClick = { onExportClick(ExportFormat.SRT) }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        Divider()
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Templates",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        ExportOption(
            title = "Meeting Template",
            description = "Formatted for meetings with action items",
            onClick = { onExportClick(ExportFormat.MEETING) }
        )
        
        ExportOption(
            title = "Lecture Template",
            description = "Formatted for lectures with key points",
            onClick = { onExportClick(ExportFormat.LECTURE) }
        )
        
        ExportOption(
            title = "Interview Template",
            description = "Formatted for interviews with speaker labels",
            onClick = { onExportClick(ExportFormat.INTERVIEW) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ExportOption(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

