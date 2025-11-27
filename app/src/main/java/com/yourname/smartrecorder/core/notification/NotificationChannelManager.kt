package com.yourname.smartrecorder.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationChannelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_RECORDING = "recording_channel"
        const val CHANNEL_PLAYBACK = "playback_channel"
        const val CHANNEL_APP_CONTENT = "app_content_channel"
    }
    
    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Recording channel
            if (manager.getNotificationChannel(CHANNEL_RECORDING) == null) {
                val recordingChannel = NotificationChannel(
                    CHANNEL_RECORDING,
                    "Recording",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Ongoing recording notification with controls"
                    enableVibration(false)
                    enableLights(true)
                    lockscreenVisibility = 1 // NotificationManager.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(recordingChannel)
            }
            
            // Playback channel
            if (manager.getNotificationChannel(CHANNEL_PLAYBACK) == null) {
                val playbackChannel = NotificationChannel(
                    CHANNEL_PLAYBACK,
                    "Audio Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Audio playback notification with media controls"
                    enableVibration(false)
                    enableLights(false)
                    lockscreenVisibility = 1 // NotificationManager.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(playbackChannel)
            }
            
            // App content channel
            if (manager.getNotificationChannel(CHANNEL_APP_CONTENT) == null) {
                val appContentChannel = NotificationChannel(
                    CHANNEL_APP_CONTENT,
                    "Smart Recorder Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Tips and encouragement notifications"
                    enableVibration(true)
                    enableLights(true)
                    lockscreenVisibility = 1 // NotificationManager.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(appContentChannel)
            }
        }
    }
}

