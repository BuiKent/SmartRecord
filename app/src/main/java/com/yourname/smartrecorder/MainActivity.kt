package com.yourname.smartrecorder

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.yourname.smartrecorder.core.permissions.PermissionHandler
import com.yourname.smartrecorder.ui.SmartRecorderApp
import com.yourname.smartrecorder.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions granted or denied
        // App will handle permission checks in each feature
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request permissions on startup
        requestPermissions()
        
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartRecorderApp()
                }
            }
        }
    }
    
    private fun requestPermissions() {
        val permissions = PermissionHandler.getRequiredPermissions()
        val permissionsToRequest = permissions.filter { permission ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (permission == Manifest.permission.RECORD_AUDIO) {
                    !PermissionHandler.hasRecordAudioPermission(this)
                } else {
                    !PermissionHandler.hasStoragePermission(this)
                }
            } else {
                !PermissionHandler.hasRecordAudioPermission(this) ||
                !PermissionHandler.hasStoragePermission(this)
            }
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }
}

