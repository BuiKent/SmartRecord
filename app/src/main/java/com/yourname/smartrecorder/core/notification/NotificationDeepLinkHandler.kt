package com.yourname.smartrecorder.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.yourname.smartrecorder.MainActivity
import com.yourname.smartrecorder.ui.navigation.AppRoutes

@Singleton
class NotificationDeepLinkHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Create PendingIntent for deep link navigation
     * Route format: "record", "library", "transcript_detail/{recordingId}", etc.
     */
    fun createPendingIntent(route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_route", route)
        }
        
        return PendingIntent.getActivity(
            context,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

