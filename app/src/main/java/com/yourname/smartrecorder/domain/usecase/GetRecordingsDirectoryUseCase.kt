package com.yourname.smartrecorder.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class GetRecordingsDirectoryUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(): File {
        val recordingsDir = File(context.filesDir, "recordings")
        recordingsDir.mkdirs()
        return recordingsDir
    }
}

