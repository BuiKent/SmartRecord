package com.yourname.smartrecorder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.domain.usecase.ExportFormat

@Composable
fun ExportBottomSheet(
    onDismiss: () -> Unit,
    onExportClick: (ExportFormat) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text(
            text = "Export & Share",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        ExportOption(
            title = "Plain Text (.txt)",
            description = "Text with speaker labels, no timestamps",
            onClick = { 
                AppLogger.d(TAG_VIEWMODEL, "[ExportBottomSheet] User selected export format: TXT")
                onExportClick(ExportFormat.TXT) 
            }
        )
        
        ExportOption(
            title = "Subtitle (.srt)",
            description = "Subtitle file with timestamps, no speaker labels",
            onClick = { 
                AppLogger.d(TAG_VIEWMODEL, "[ExportBottomSheet] User selected export format: SRT")
                onExportClick(ExportFormat.SRT) 
            }
        )
        
        ExportOption(
            title = "Lecture Template",
            description = "Formatted for lectures with key points",
            onClick = { 
                AppLogger.d(TAG_VIEWMODEL, "[ExportBottomSheet] User selected export format: LECTURE")
                onExportClick(ExportFormat.LECTURE) 
            }
        )
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

