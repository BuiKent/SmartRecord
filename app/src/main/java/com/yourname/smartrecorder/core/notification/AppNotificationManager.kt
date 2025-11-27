package com.yourname.smartrecorder.core.notification

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.data.preferences.SettingsStore
import com.yourname.smartrecorder.ui.navigation.AppRoutes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelManager: NotificationChannelManager,
    private val settingsStore: SettingsStore,
    private val deepLinkHandler: NotificationDeepLinkHandler
) {
    private val notificationManager = NotificationManagerCompat.from(context)
    
    companion object {
        private const val NOTIFICATION_ID_APP_CONTENT = 1001
    }
    
    init {
        channelManager.createChannels()
    }
    
    /**
     * Show app content notification
     */
    suspend fun showAppContentNotification(title: String, content: String, deepLink: String? = null) {
        // 1. Check permission
        if (!notificationManager.areNotificationsEnabled()) {
            AppLogger.d(TAG_VIEWMODEL, "AppNotificationManager: System permission disabled")
            return
        }
        
        // 2. Check Android 13+ permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                AppLogger.d(TAG_VIEWMODEL, "AppNotificationManager: POST_NOTIFICATIONS not granted")
                return
            }
        }
        
        // 3. Check user preference
        val enabled = settingsStore.notificationsEnabled.first()
        if (!enabled) {
            AppLogger.d(TAG_VIEWMODEL, "AppNotificationManager: Disabled by user preference")
            return
        }
        
        // 4. Build PendingIntent
        val pendingIntent = deepLink?.let { link ->
            deepLinkHandler.createPendingIntent(link)
        } ?: deepLinkHandler.createPendingIntent(AppRoutes.RECORD)
        
        // 5. Build notification
        val notification = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_APP_CONTENT)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Custom icon
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .build()
        
        // 6. Show notification
        try {
            notificationManager.notify(NOTIFICATION_ID_APP_CONTENT, notification)
            AppLogger.d(TAG_VIEWMODEL, "AppNotificationManager: Notification shown: $title")
        } catch (e: SecurityException) {
            AppLogger.e(TAG_VIEWMODEL, "AppNotificationManager: Permission denied", e)
        } catch (e: Exception) {
            AppLogger.e(TAG_VIEWMODEL, "AppNotificationManager: Failed to show notification", e)
        }
    }
}

