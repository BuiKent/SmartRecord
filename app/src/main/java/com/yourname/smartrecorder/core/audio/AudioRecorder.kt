package com.yourname.smartrecorder.core.audio

import java.io.File

interface AudioRecorder {
    suspend fun startRecording(outputFile: File)
    suspend fun stopRecording(): File
    suspend fun pause()
    suspend fun resume()
    fun getAmplitude(): Int
    /**
     * Force reset recording state without saving file.
     * Used when ViewModel is cleared or recording needs to be aborted.
     * This will release MediaRecorder and reset all state.
     */
    suspend fun forceReset()
}

