package com.yourname.smartrecorder.ui.navigation

object AppRoutes {
    const val RECORD = "record"
    const val LIBRARY = "library"
    const val STUDY = "study"
    const val TRANSCRIPT_DETAIL = "transcript_detail/{recordingId}"
    const val REALTIME_TRANSCRIPT = "realtime_transcript"
    
    fun transcriptDetail(recordingId: String) = "transcript_detail/$recordingId"
}

