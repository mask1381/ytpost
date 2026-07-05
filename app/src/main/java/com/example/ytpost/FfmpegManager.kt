package com.example.ytpost

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object FfmpegManager {
    private const val ASSET_PATH = "ffmpeg/arm64-v8a/ffmpeg"
    private const val BINARY_NAME = "ffmpeg"

    fun getFfmpegPath(context: Context): String? {
        val targetFile = File(context.filesDir, BINARY_NAME)

        if (!targetFile.exists()) {
            try {
                context.assets.open(ASSET_PATH).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.setExecutable(true, false)
                AppLogger.log("FFmpeg binary extracted to ${targetFile.absolutePath}")
            } catch (e: Exception) {
                AppLogger.log("Failed to extract ffmpeg binary: ${e.message}")
                return null
            }
        }

        return if (targetFile.exists() && targetFile.canExecute()) {
            targetFile.absolutePath
        } else {
            null
        }
    }
}
