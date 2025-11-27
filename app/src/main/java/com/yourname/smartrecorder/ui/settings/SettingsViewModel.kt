package com.yourname.smartrecorder.ui.settings

import android.content.Context
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_VIEWMODEL
import com.yourname.smartrecorder.core.notification.NotificationScheduler
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
    private val notificationPermissionManager: NotificationPermissionManager,
    private val notificationScheduler: NotificationScheduler
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // System notification state as single source of truth (theo Onboarding.md pattern)
    // UI hiển thị dựa trên system state, không phải DataStore
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
     * 
     * Theo đúng pattern Onboarding.md:
     * - Dùng NotificationManagerCompat.areNotificationsEnabled() (system state as source of truth)
     */
    fun initializeState(context: Context) {
        val enabled = notificationPermissionManager.areNotificationsEnabled(context)
        _systemNotificationAllowed.value = enabled
    }
    
    /**
     * Refresh state asynchronously (called from LaunchedEffect)
     * 
     * Theo đúng pattern Onboarding.md (Pattern 1):
     * - Chỉ refresh system state, không sync SettingsStore
     * - Retry logic: repeat(3) với delay(180ms) để handle Samsung/Xiaomi delay
     */
    fun refreshState(context: Context) {
        viewModelScope.launch {
            val firstCheck = notificationPermissionManager.areNotificationsEnabled(context)
            _systemNotificationAllowed.value = firstCheck
            
            // ✅ Retry if system hasn't updated yet (handle Samsung/Xiaomi delay)
            if (!firstCheck) {
                repeat(3) { attempt ->
                    kotlinx.coroutines.delay(180)
                    val retryState = notificationPermissionManager.areNotificationsEnabled(context)
                    if (retryState != firstCheck) {
                        _systemNotificationAllowed.value = retryState
                        return@launch
                    }
                }
            }
        }
    }
    
    /**
     * Handle notification toggle change
     * 
     * Theo đúng pattern Onboarding.md:
     * - Toggle ON → Request permission dialog
     * - Toggle OFF → Cancel notifications, sync SettingsStore, open system settings
     */
    fun onNotificationToggleChanged(wantsToEnable: Boolean) {
        viewModelScope.launch {
            val currentValue = _uiState.value.notificationsEnabled
            
            if (wantsToEnable && !currentValue) {
                // ✅ Toggle ON → Request permission dialog
                _eventFlow.emit(SettingsEvent.RequestNotificationPermission)
            } else if (!wantsToEnable && currentValue) {
                // ✅ Toggle OFF → Cancel notifications, sync SettingsStore, open system settings
                settingsStore.setNotificationsEnabled(false)
                notificationScheduler.cancelAllNotifications()
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
    
    /**
     * Schedule daily notifications (called after permission granted)
     * 
     * Theo đúng pattern mẫu:
     * - Update DataStore preference
     * - Schedule daily notifications
     */
    fun scheduleNotifications() {
        viewModelScope.launch {
            AppLogger.logViewModel(TAG_VIEWMODEL, "SettingsViewModel", "scheduleNotifications", 
                "Scheduling daily notifications")
            settingsStore.setNotificationsEnabled(true) // Update DataStore preference
            notificationScheduler.scheduleDailyNotifications()
        }
    }
    
}

