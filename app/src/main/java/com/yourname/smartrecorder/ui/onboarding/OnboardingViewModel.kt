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
    
    fun enableNotifications() {
        viewModelScope.launch {
            AppLogger.logViewModel(TAG_VIEWMODEL, "OnboardingViewModel", "enableNotifications", 
                "Enabling notifications in settings")
            settingsStore.setNotificationsEnabled(true)
        }
    }
}

