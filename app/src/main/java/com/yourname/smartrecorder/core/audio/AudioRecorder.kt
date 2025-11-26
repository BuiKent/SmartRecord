package com.yourname.smartrecorder.core.audio

import java.io.File

interface AudioRecorder {
    suspend fun startRecording(outputFile: File)
    suspend fun stopRecording(): File
    suspend fun pause()
    suspend fun resume()
    fun getAmplitude(): Int
}

