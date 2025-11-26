package com.yourname.smartrecorder.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourname.smartrecorder.ui.navigation.AppRoutes

@Composable
fun AppBottomBar(
    currentRoute: String,
    onLibraryClick: () -> Unit,
    onRecordClick: () -> Unit,
    onStudyClick: () -> Unit
) {
    NavigationBar {
        // LEFT: Study
        NavigationBarItem(
            selected = currentRoute == AppRoutes.STUDY,
            onClick = onStudyClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "Study"
                )
            },
            label = { Text("Study") },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                indicatorColor = MaterialTheme.colorScheme.primary
            )
        )

        // MIDDLE: Record button
        NavigationBarItem(
            selected = currentRoute == AppRoutes.RECORD,
            onClick = onRecordClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Record",
                    modifier = Modifier.size(32.dp),
                    tint = if (currentRoute == AppRoutes.RECORD) Color.White else MaterialTheme.colorScheme.primary
                )
            },
            label = { Text("Record") },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                indicatorColor = MaterialTheme.colorScheme.primary
            )
        )

        // RIGHT: Library
        NavigationBarItem(
            selected = currentRoute == AppRoutes.LIBRARY,
            onClick = onLibraryClick,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                    contentDescription = "Library"
                )
            },
            label = { Text("Library") },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                indicatorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

