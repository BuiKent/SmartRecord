package com.yourname.smartrecorder.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.data.preferences.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val settingsStore: SettingsStore
) : ViewModel() {
    
    fun completeOnboarding() {
        viewModelScope.launch {
            AppLogger.logViewModel(TAG_VIEWMODEL, "OnboardingViewModel", "completeOnboarding", 
                "Saving onboarding completed state")
            settingsStore.setOnboardingCompleted(true)
        }
    }
    
    /**
     * Enable notifications in SettingsStore (user preference)
     * 
     * Đồng bộ theo Onboarding.md pattern:
     * - System state là single source of truth cho UI display
     * - SettingsStore chỉ lưu user preference (không phải system state)
     * - Khi permission granted → update SettingsStore để sync với SettingsScreen
     */
    fun enableNotifications() {
        viewModelScope.launch {
            AppLogger.logViewModel(TAG_VIEWMODEL, "OnboardingViewModel", "enableNotifications", 
                "Enabling notifications in SettingsStore (user preference)")
            settingsStore.setNotificationsEnabled(true)
        }
    }
}

