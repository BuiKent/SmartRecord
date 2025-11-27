package com.yourname.smartrecorder.core.notification

import com.yourname.smartrecorder.ui.navigation.AppRoutes
import kotlin.random.Random

object NotificationContent {
    private val random = Random(System.currentTimeMillis())
    
    data class NotificationMessage(
        val title: String,
        val content: String,
        val deepLink: String? = null
    )
    
    private val appContentMessages = listOf(
        NotificationMessage(
            title = "🎙️ Smart Recording",
            content = "Have an important meeting? Record and convert to transcript automatically with AI!",
            deepLink = AppRoutes.RECORD
        ),
        NotificationMessage(
            title = "📝 Live Transcribe",
            content = "Use Live Transcribe to see real-time transcript while recording. Discover now!",
            deepLink = AppRoutes.REALTIME_TRANSCRIPT
        ),
        NotificationMessage(
            title = "📚 Create Flashcards",
            content = "Create flashcards from questions in transcript for effective studying!",
            deepLink = AppRoutes.STUDY
        ),
        NotificationMessage(
            title = "💾 Flexible Export",
            content = "Export transcript to multiple formats: TXT, Markdown, SRT. Fits all your needs!",
            deepLink = AppRoutes.LIBRARY
        ),
        NotificationMessage(
            title = "🔍 Smart Search",
            content = "Search in recording history by keywords or content. Fast and accurate!",
            deepLink = AppRoutes.LIBRARY
        ),
        NotificationMessage(
            title = "📖 Review Transcripts",
            content = "You have recordings to review. Check them out to not miss important information!",
            deepLink = AppRoutes.LIBRARY
        ),
        NotificationMessage(
            title = "✨ New Features",
            content = "Discover new features in Smart Recorder: Live Transcribe, Flashcards, Export templates!",
            deepLink = AppRoutes.RECORD
        )
    )
    
    fun getRandomMessage(): NotificationMessage {
        return appContentMessages[random.nextInt(appContentMessages.size)]
    }
    
    fun getAllMessages(): List<NotificationMessage> = appContentMessages
}

