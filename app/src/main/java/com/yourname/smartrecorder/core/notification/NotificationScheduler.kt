package com.yourname.smartrecorder.core.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.notification.worker.NotificationWorker
import com.yourname.smartrecorder.data.preferences.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val settingsStore: SettingsStore
) {
    companion object {
        private const val WORK_NAME_DAILY = "notification_daily_app"
    }
    
    suspend fun scheduleDailyNotifications() {
        workManager.cancelUniqueWork(WORK_NAME_DAILY)
        
        val enabled = settingsStore.notificationsEnabled.first()
        if (!enabled) {
            AppLogger.d(TAG_VIEWMODEL, "NotificationScheduler: Notifications disabled - skipping schedule")
            return
        }
        
        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            24, TimeUnit.HOURS,
            1, TimeUnit.HOURS
        )
            .addTag("notification_daily")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME_DAILY,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        
        AppLogger.d(TAG_VIEWMODEL, "NotificationScheduler: Daily notifications scheduled")
    }
    
    fun cancelAllNotifications() {
        workManager.cancelUniqueWork(WORK_NAME_DAILY)
        AppLogger.d(TAG_VIEWMODEL, "NotificationScheduler: All notifications cancelled")
    }
}

