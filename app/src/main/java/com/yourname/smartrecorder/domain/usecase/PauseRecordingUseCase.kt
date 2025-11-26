package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.audio.AudioRecorder
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_USECASE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PauseRecordingUseCase @Inject constructor(
    private val audioRecorder: AudioRecorder
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        AppLogger.logUseCase(TAG_USECASE, "PauseRecordingUseCase", "Pausing", null)
        audioRecorder.pause()
    }
}

class ResumeRecordingUseCase @Inject constructor(
    private val audioRecorder: AudioRecorder
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        AppLogger.logUseCase(TAG_USECASE, "ResumeRecordingUseCase", "Resuming", null)
        audioRecorder.resume()
    }
}

