package com.yourname.smartrecorder.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Handles error display using Snackbar.
 * Call showError() to display error messages to users.
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
            val result = snackbarHostState.showSnackbar(
                message = error,
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            if (result == SnackbarResult.Dismissed) {
                onErrorShown()
            }
        }
    }
    
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
    )
}

