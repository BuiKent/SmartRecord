package com.yourname.smartrecorder.core.notification

import android.content.Context
import android.content.SharedPreferences
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Calendar

@Singleton
class NotificationFrequencyCap @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "notification_frequency"
        private const val KEY_LAST_SHOWN = "last_shown_timestamp"
        private const val KEY_TODAY_COUNT = "today_count"
        private const val KEY_LAST_DATE = "last_date"
        
        private const val MAX_PER_DAY = 3
        private const val MIN_INTERVAL_HOURS = 4L
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun canShowNotification(): Boolean {
        val now = System.currentTimeMillis()
        val today = getTodayDateString()
        
        // Check daily count
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        val todayCount = if (lastDate == today) {
            prefs.getInt(KEY_TODAY_COUNT, 0)
        } else {
            prefs.edit().putInt(KEY_TODAY_COUNT, 0).apply()
            0
        }
        
        if (todayCount >= MAX_PER_DAY) {
            AppLogger.d(TAG_VIEWMODEL, "FrequencyCap: Daily limit reached ($todayCount/$MAX_PER_DAY)")
            return false
        }
        
        // Check minimum interval
        val lastShown = prefs.getLong(KEY_LAST_SHOWN, 0)
        if (lastShown > 0) {
            val hoursSinceLastShown = (now - lastShown) / (1000 * 60 * 60)
            if (hoursSinceLastShown < MIN_INTERVAL_HOURS) {
                AppLogger.d(TAG_VIEWMODEL, "FrequencyCap: Minimum interval not reached (${hoursSinceLastShown}h/${MIN_INTERVAL_HOURS}h)")
                return false
            }
        }
        
        return true
    }
    
    fun recordNotificationShown() {
        val now = System.currentTimeMillis()
        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        
        val editor = prefs.edit()
        editor.putLong(KEY_LAST_SHOWN, now)
        
        if (lastDate == today) {
            val count = prefs.getInt(KEY_TODAY_COUNT, 0) + 1
            editor.putInt(KEY_TODAY_COUNT, count)
        } else {
            editor.putInt(KEY_TODAY_COUNT, 1)
            editor.putString(KEY_LAST_DATE, today)
        }
        
        editor.apply()
        AppLogger.d(TAG_VIEWMODEL, "FrequencyCap: Recorded notification shown (today: ${prefs.getInt(KEY_TODAY_COUNT, 0)})")
    }
    
    private fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}-${calendar.get(Calendar.DAY_OF_MONTH)}"
    }
    
    fun reset() {
        prefs.edit().clear().apply()
        AppLogger.d(TAG_VIEWMODEL, "FrequencyCap: Reset")
    }
}

