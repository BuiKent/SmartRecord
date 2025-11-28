package com.yourname.smartrecorder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_LIFECYCLE
import com.yourname.smartrecorder.ui.SmartRecorderApp
import com.yourname.smartrecorder.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // StateFlow để truyền notification route từ Activity → Compose
    // Cho phép handle cả onCreate và onNewIntent
    val notificationRouteState = MutableStateFlow<String?>(null)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Handle deep link from notification (onCreate)
        handleNotificationDeepLink(intent)
        
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Truyền StateFlow vào SmartRecorderApp
                    SmartRecorderApp(notificationRouteState = notificationRouteState)
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle deep link từ notification (onNewIntent - khi Activity đã mở)
        handleNotificationDeepLink(intent)
    }
    
    private fun handleNotificationDeepLink(intent: Intent?) {
        val route = intent?.getStringExtra("notification_route") ?: return
        // Clear extra để tránh re-process
        intent.removeExtra("notification_route")
        // Update StateFlow → Compose sẽ nhận route mới
        notificationRouteState.value = route
        AppLogger.d(TAG_LIFECYCLE, "Notification route received", "route=$route")
    }
}

