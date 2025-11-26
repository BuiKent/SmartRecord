package com.yourname.smartrecorder.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yourname.smartrecorder.core.permissions.PermissionHandler
import com.yourname.smartrecorder.ui.importaudio.ImportAudioViewModel
import com.yourname.smartrecorder.ui.navigation.AppRoutes
import com.yourname.smartrecorder.ui.record.RecordViewModel
import com.yourname.smartrecorder.ui.screens.LibraryScreen
import com.yourname.smartrecorder.ui.screens.RecordScreen
import com.yourname.smartrecorder.ui.screens.StudyScreen
import com.yourname.smartrecorder.ui.screens.TranscriptScreen
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
                val context = LocalContext.current
                val viewModel: RecordViewModel = hiltViewModel()
                val importViewModel: ImportAudioViewModel = hiltViewModel()
                val uiState = viewModel.uiState.collectAsState().value
                val navigateToTranscript = viewModel.navigateToTranscript.collectAsState().value
                val importState = importViewModel.uiState.collectAsState().value
                
                // Permission launcher for recording
                val recordPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        viewModel.onStartClick()
                    }
                }
                
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let {
                        val fileName = it.lastPathSegment ?: "audio_file"
                        importViewModel.importAudioFile(it, fileName)
                    }
                }
                
                // Permission launcher for importing audio
                val importPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        filePickerLauncher.launch("audio/*")
                    }
                }
                
                LaunchedEffect(navigateToTranscript) {
                    navigateToTranscript?.let { recordingId ->
                        navController.navigate(AppRoutes.transcriptDetail(recordingId)) {
                            popUpTo(AppRoutes.RECORD) { inclusive = false }
                        }
                        viewModel.onNavigationHandled()
                    }
                }
                
                LaunchedEffect(importState.importedRecordingId) {
                    importState.importedRecordingId?.let { recordingId ->
                        navController.navigate(AppRoutes.transcriptDetail(recordingId)) {
                            popUpTo(AppRoutes.RECORD) { inclusive = false }
                        }
                        importViewModel.onImportHandled()
                    }
                }
                
                RecordScreen(
                    uiState = uiState,
                    onStartRecordClick = {
                        if (PermissionHandler.hasRecordAudioPermission(context)) {
                            viewModel.onStartClick()
                        } else {
                            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onPauseRecordClick = { viewModel.onPauseClick() },
                    onStopRecordClick = { viewModel.onStopClick() },
                    onImportAudioClick = {
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_AUDIO
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        if (PermissionHandler.hasStoragePermission(context)) {
                            filePickerLauncher.launch("audio/*")
                        } else {
                            importPermissionLauncher.launch(permission)
                        }
                    },
                    onRealtimeSttClick = { /* TODO: mở màn realtime STT */ }
                )
            }
            composable(AppRoutes.LIBRARY) {
                LibraryScreen(
                    onRecordingClick = { recordingId ->
                        navController.navigate(AppRoutes.transcriptDetail(recordingId))
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
            composable(
                route = AppRoutes.TRANSCRIPT_DETAIL,
                arguments = listOf(navArgument("recordingId") { type = NavType.StringType })
            ) { backStackEntry ->
                val recordingId = backStackEntry.arguments?.getString("recordingId") ?: return@composable
                TranscriptScreen(
                    recordingId = recordingId,
                    onBackClick = { navController.popBackStack() },
                    onExportClick = { /* TODO: Export */ }
                )
            }
        }
    }
}

