package com.yourname.smartrecorder.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yourname.smartrecorder.ui.navigation.AppRoutes
import com.yourname.smartrecorder.ui.record.RecordViewModel
import com.yourname.smartrecorder.ui.screens.LibraryScreen
import com.yourname.smartrecorder.ui.screens.RecordScreen
import com.yourname.smartrecorder.ui.screens.StudyScreen
import com.yourname.smartrecorder.ui.widgets.AppBottomBar

@Composable
fun SmartRecorderApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("/") 
        ?: AppRoutes.RECORD

    Scaffold(
        bottomBar = {
            AppBottomBar(
                currentRoute = currentRoute,
                onLibraryClick = {
                    navController.navigate(AppRoutes.LIBRARY) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onRecordClick = {
                    navController.navigate(AppRoutes.RECORD) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onStudyClick = {
                    navController.navigate(AppRoutes.STUDY) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.RECORD,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppRoutes.RECORD) {
                val viewModel: RecordViewModel = hiltViewModel()
                val uiState = viewModel.uiState.collectAsState().value
                RecordScreen(
                    uiState = uiState,
                    onStartRecordClick = { viewModel.onStartClick() },
                    onPauseRecordClick = { viewModel.onPauseClick() },
                    onStopRecordClick = { viewModel.onStopClick() },
                    onImportAudioClick = { /* TODO: mở file picker */ },
                    onRealtimeSttClick = { /* TODO: mở màn realtime STT */ }
                )
            }
            composable(AppRoutes.LIBRARY) {
                LibraryScreen(
                    onRecordingClick = { recordingId ->
                        // TODO: điều hướng sang TranscriptDetailScreen sau này
                    }
                )
            }
            composable(AppRoutes.STUDY) {
                StudyScreen(
                    onStartPracticeClick = {
                        // TODO: mở màn luyện flashcards
                    }
                )
            }
        }
    }
}

