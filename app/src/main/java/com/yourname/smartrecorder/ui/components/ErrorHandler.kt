package com.yourname.smartrecorder.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL

/**
 * Handles error display using Snackbar.
 * Automatically shows error messages and clears them when dismissed.
 */
@Composable
fun ErrorHandler(
    error: String?,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(error) {
        if (error != null && error.isNotBlank()) {
            AppLogger.d(TAG_VIEWMODEL, "[ErrorHandler] Showing error to user: %s", error)
            val result = snackbarHostState.showSnackbar(
                message = error,
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            if (result == SnackbarResult.Dismissed) {
                AppLogger.d(TAG_VIEWMODEL, "[ErrorHandler] Error dismissed by user")
                onErrorShown()
            }
        }
    }
    
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
    )
}

