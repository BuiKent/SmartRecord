package com.yourname.smartrecorder.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_DATABASE
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val ds = ctx.dataStore
    
    // Onboarding
    val onboardingCompleted: Flow<Boolean> = ds.data.map {
        it[PrefKeys.ONBOARDING_COMPLETED] ?: false
    }
    
    suspend fun setOnboardingCompleted(v: Boolean) {
        AppLogger.logDatabase(TAG_DATABASE, "SET", "settings", "onboarding_completed=$v")
        ds.edit { it[PrefKeys.ONBOARDING_COMPLETED] = v }
    }
    
    // Notifications
    val notificationsEnabled: Flow<Boolean> = ds.data.map {
        it[PrefKeys.NOTIFICATIONS_ENABLED] ?: true // Default enabled
    }
    
    suspend fun setNotificationsEnabled(v: Boolean) {
        AppLogger.logDatabase(TAG_DATABASE, "SET", "settings", "notifications_enabled=$v")
        ds.edit { it[PrefKeys.NOTIFICATIONS_ENABLED] = v }
    }
    
    // Auto-save
    val autoSaveEnabled: Flow<Boolean> = ds.data.map {
        it[PrefKeys.AUTO_SAVE_ENABLED] ?: true // Default enabled
    }
    
    suspend fun setAutoSaveEnabled(v: Boolean) {
        AppLogger.logDatabase(TAG_DATABASE, "SET", "settings", "auto_save_enabled=$v")
        ds.edit { it[PrefKeys.AUTO_SAVE_ENABLED] = v }
    }
    
    // Transcription quality
    val transcriptionQuality: Flow<String> = ds.data.map {
        it[PrefKeys.TRANSCRIPTION_QUALITY] ?: "balanced" // Default balanced
    }
    
    suspend fun setTranscriptionQuality(quality: String) {
        AppLogger.logDatabase(TAG_DATABASE, "SET", "settings", "transcription_quality=$quality")
        ds.edit { it[PrefKeys.TRANSCRIPTION_QUALITY] = quality }
    }
}

