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
            .padding(24.dp)
    ) {
        Text(
            text = "Export & Share",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
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
        
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Templates",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        ExportOption(
            title = "Lecture Template",
            description = "Formatted for lectures with key points",
            onClick = { 
                AppLogger.d(TAG_VIEWMODEL, "[ExportBottomSheet] User selected export format: LECTURE")
                onExportClick(ExportFormat.LECTURE) 
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(
            onClick = { 
                AppLogger.d(TAG_VIEWMODEL, "[ExportBottomSheet] User cancelled export")
                onDismiss() 
            },
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

