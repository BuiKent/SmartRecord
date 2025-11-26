package com.yourname.smartrecorder.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.permissions.NotificationPermissionManager
import com.yourname.smartrecorder.data.preferences.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SettingsEvent {
    object RequestNotificationPermission : SettingsEvent()
    object OpenSystemSettings : SettingsEvent()
}

data class SettingsUiState(
    val notificationsEnabled: Boolean = true,
    val transcriptionQuality: String = "balanced"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val notificationPermissionManager: NotificationPermissionManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // System notification state as single source of truth
    private val _systemNotificationAllowed = MutableStateFlow(false)
    val systemNotificationAllowed = _systemNotificationAllowed.asStateFlow()
    
    // Events
    private val _eventFlow = MutableSharedFlow<SettingsEvent>()
    val eventFlow: SharedFlow<SettingsEvent> = _eventFlow.asSharedFlow()
    
    // Combine settings flows
    val settings = combine(
        settingsStore.transcriptionQuality,
        systemNotificationAllowed
    ) { quality, notifications ->
        SettingsUiState(
            notificationsEnabled = notifications,
            transcriptionQuality = quality
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )
    
    init {
        viewModelScope.launch {
            settings.collect { settings ->
                _uiState.value = settings
            }
        }
    }
    
    /**
     * Initialize state synchronously (called from DisposableEffect)
     */
    fun initializeState(context: Context) {
        val enabled = notificationPermissionManager.areNotificationsEnabled(context)
        _systemNotificationAllowed.value = enabled
        AppLogger.logViewModel(TAG_VIEWMODEL, "SettingsViewModel", "initializeState", 
            "notificationsEnabled=$enabled")
    }
    
    /**
     * Refresh state asynchronously (called from LaunchedEffect)
     */
    fun refreshState(context: Context) {
        viewModelScope.launch {
            val firstCheck = notificationPermissionManager.areNotificationsEnabled(context)
            _systemNotificationAllowed.value = firstCheck
            
            // Retry logic for Samsung/Xiaomi delay
            if (!firstCheck) {
                repeat(3) { attempt ->
                    kotlinx.coroutines.delay(180)
                    val retryState = notificationPermissionManager.areNotificationsEnabled(context)
                    if (retryState != firstCheck) {
                        _systemNotificationAllowed.value = retryState
                        AppLogger.d(TAG_VIEWMODEL, "Notification state changed after retry: $retryState")
                        return@launch
                    }
                }
            }
        }
    }
    
    /**
     * Handle notification toggle change
     */
    fun onNotificationToggleChanged(wantsToEnable: Boolean) {
        viewModelScope.launch {
            val currentValue = _uiState.value.notificationsEnabled
            
            if (wantsToEnable && !currentValue) {
                // Toggle ON → Request permission dialog
                AppLogger.logViewModel(TAG_VIEWMODEL, "SettingsViewModel", "onNotificationToggleChanged", 
                    "Requesting notification permission")
                _eventFlow.emit(SettingsEvent.RequestNotificationPermission)
            } else if (!wantsToEnable && currentValue) {
                // Toggle OFF → Open system settings (permission dialog cannot disable)
                AppLogger.logViewModel(TAG_VIEWMODEL, "SettingsViewModel", "onNotificationToggleChanged", 
                    "Opening system settings")
                _eventFlow.emit(SettingsEvent.OpenSystemSettings)
            }
        }
    }
    
    /**
     * Handle auto-save toggle change
     */
    fun onAutoSaveToggleChanged(enabled: Boolean) {
        viewModelScope.launch {
            AppLogger.logViewModel(TAG_VIEWMODEL, "SettingsViewModel", "onAutoSaveToggleChanged", 
                "enabled=$enabled")
            settingsStore.setAutoSaveEnabled(enabled)
        }
    }
    
    /**
     * Open system settings (called from event handler)
     */
    fun openSystemSettings(context: Context) {
        notificationPermissionManager.openSystemSettings(context)
    }
}

