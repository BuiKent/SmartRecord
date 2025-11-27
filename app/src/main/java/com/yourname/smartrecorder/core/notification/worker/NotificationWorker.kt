package com.yourname.smartrecorder.core.notification.worker

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.notification.AppNotificationManager
import com.yourname.smartrecorder.core.notification.NotificationContent
import com.yourname.smartrecorder.core.notification.NotificationFrequencyCap
import com.yourname.smartrecorder.data.preferences.SettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar

@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationManager: AppNotificationManager,
    private val settingsStore: SettingsStore,
    private val frequencyCap: NotificationFrequencyCap
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            AppLogger.d(TAG_VIEWMODEL, "NotificationWorker: Starting work")
            
            if (!shouldShowNotification()) {
                AppLogger.d(TAG_VIEWMODEL, "NotificationWorker: Should not show - skipping")
                return Result.success()
            }
            
            if (!frequencyCap.canShowNotification()) {
                AppLogger.d(TAG_VIEWMODEL, "NotificationWorker: Frequency cap - skipping")
                return Result.success()
            }
            
            val message = NotificationContent.getRandomMessage()
            
            notificationManager.showAppContentNotification(
                title = message.title,
                content = message.content,
                deepLink = message.deepLink
            )
            
            frequencyCap.recordNotificationShown()
            
            AppLogger.d(TAG_VIEWMODEL, "NotificationWorker: Success - shown '${message.title}'")
            Result.success()
        } catch (e: Exception) {
            AppLogger.e(TAG_VIEWMODEL, "NotificationWorker: Error", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    private suspend fun shouldShowNotification(): Boolean {
        val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            AppLogger.d(TAG_VIEWMODEL, "NotificationWorker: System notifications disabled")
            return false
        }
        
        val enabled = settingsStore.notificationsEnabled.first()
        if (!enabled) {
            AppLogger.d(TAG_VIEWMODEL, "NotificationWorker: User disabled notifications")
            return false
        }
        
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 22 || hour < 7) {
            AppLogger.d(TAG_VIEWMODEL, "NotificationWorker: Night time - skipping (hour: $hour)")
            return false
        }
        
        return true
    }
}

