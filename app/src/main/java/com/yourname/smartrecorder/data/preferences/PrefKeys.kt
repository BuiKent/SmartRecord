package com.yourname.smartrecorder.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PrefKeys {
    // Onboarding
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    
    // Notifications
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    
    // Auto-save
    val AUTO_SAVE_ENABLED = booleanPreferencesKey("auto_save_enabled")
    
    // Transcription settings
    val TRANSCRIPTION_QUALITY = stringPreferencesKey("transcription_quality") // "fast", "balanced", "accurate"
}

