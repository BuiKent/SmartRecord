package com.yourname.smartrecorder.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import android.widget.Toast
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
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.permissions.PermissionHandler
import com.yourname.smartrecorder.ui.importaudio.ImportAudioViewModel
import com.yourname.smartrecorder.ui.navigation.AppRoutes
import com.yourname.smartrecorder.ui.record.RecordViewModel
import com.yourname.smartrecorder.ui.screens.LibraryScreen
import com.yourname.smartrecorder.ui.screens.RecordScreen
import com.yourname.smartrecorder.ui.screens.RealtimeTranscriptScreen
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
                    AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] Record audio permission result -> granted: %b", isGranted)
                    if (isGranted) {
                        viewModel.onStartClick()
                    } else {
                        AppLogger.w(TAG_VIEWMODEL, "[SmartRecorderApp] Record audio permission denied")
                    }
                }
                
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        val fileName = uri.lastPathSegment ?: "audio_file"
                        AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] User selected audio file -> uri: %s, fileName: %s", 
                            uri.toString(), fileName)
                        importViewModel.importAudioFile(uri, fileName)
                    } else {
                        AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] User cancelled file picker")
                    }
                }
                
                // Permission launcher for importing audio
                val importPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] Storage permission result -> granted: %b", isGranted)
                    if (isGranted) {
                        filePickerLauncher.launch("audio/*")
                    } else {
                        AppLogger.w(TAG_VIEWMODEL, "[SmartRecorderApp] Storage permission denied")
                    }
                }
                
                LaunchedEffect(navigateToTranscript) {
                    navigateToTranscript?.let { recordingId ->
                        AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] Navigating to transcript -> recordingId: %s", recordingId)
                        navController.navigate(AppRoutes.transcriptDetail(recordingId)) {
                            popUpTo(AppRoutes.RECORD) { inclusive = false }
                        }
                        viewModel.onNavigationHandled()
                    }
                }
                
                LaunchedEffect(importState.importedRecordingId) {
                    importState.importedRecordingId?.let { recordingId ->
                        AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] Navigating to imported recording transcript -> recordingId: %s", recordingId)
                        navController.navigate(AppRoutes.transcriptDetail(recordingId)) {
                            popUpTo(AppRoutes.RECORD) { inclusive = false }
                        }
                        importViewModel.onImportHandled()
                    }
                }
                
                RecordScreen(
                    uiState = uiState,
                    onStartRecordClick = {
                        AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] User clicked start record button")
                        if (PermissionHandler.hasRecordAudioPermission(context)) {
                            viewModel.onStartClick()
                        } else {
                            AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] Requesting record audio permission")
                            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onPauseRecordClick = { 
                        AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] User clicked pause/resume record button")
                        viewModel.onPauseClick() 
                    },
                    onStopRecordClick = { 
                        AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] User clicked stop record button")
                        viewModel.onStopClick() 
                    },
                    onImportAudioClick = {
                        AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] User clicked import audio button")
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_AUDIO
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        if (PermissionHandler.hasStoragePermission(context)) {
                            filePickerLauncher.launch("audio/*")
                        } else {
                            AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] Requesting storage permission")
                            importPermissionLauncher.launch(permission)
                        }
                    },
                    onRealtimeSttClick = {
                        AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] User clicked realtime STT button")
                        navController.navigate(AppRoutes.REALTIME_TRANSCRIPT)
                    },
                    onBookmarkClick = { note ->
                        AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] User clicked bookmark button -> note: %s", note.take(50))
                        viewModel.onBookmarkClick(note)
                    },
                    importState = importState
                )
                
                // Error handling
                com.yourname.smartrecorder.ui.components.ErrorHandler(
                    error = uiState.error,
                    onErrorShown = { viewModel.clearError() }
                )
                
                // Show toast when bookmark is added
                LaunchedEffect(viewModel.bookmarkAdded.collectAsState().value) {
                    if (viewModel.bookmarkAdded.value) {
                        AppLogger.d(TAG_VIEWMODEL, "[SmartRecorderApp] Bookmark added successfully, showing toast")
                        Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
                        viewModel.onBookmarkAddedHandled()
                    }
                }
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
                        // Already handled in StudyScreen
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
                    onExportClick = { /* Export handled by TranscriptScreen internally */ }
                )
            }
            composable(AppRoutes.REALTIME_TRANSCRIPT) {
                RealtimeTranscriptScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}

