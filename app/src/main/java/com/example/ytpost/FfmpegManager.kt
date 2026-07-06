package com.example.ytpost

import android.content.Context
import java.io.File

object FfmpegManager {
    fun getFfmpegPath(context: Context): String? {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) {
            binDir.mkdirs()
        }

        val ffmpegTarget = File(binDir, "ffmpeg")
        val ffprobeTarget = File(binDir, "ffprobe")

        // Optimization: Only copy if files don't exist
        if (!ffmpegTarget.exists() || !ffprobeTarget.exists()) {
            val nativeDir = context.applicationInfo.nativeLibraryDir
            val ffmpegSrc = File(nativeDir, "libffmpeg.so")
            val ffprobeSrc = File(nativeDir, "libffprobe.so")

            if (ffmpegSrc.exists()) {
                try {
                    ffmpegSrc.inputStream().use { input ->
                        ffmpegTarget.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    ffmpegTarget.setExecutable(true, false)
                    AppLogger.log("FFmpeg binary prepared in bin folder")
                } catch (e: Exception) {
                    AppLogger.log("Failed to copy ffmpeg: ${e.message}")
                }
            }

            if (ffprobeSrc.exists()) {
                try {
                    ffprobeSrc.inputStream().use { input ->
                        ffprobeTarget.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    ffprobeTarget.setExecutable(true, false)
                    AppLogger.log("FFprobe binary prepared in bin folder")
                } catch (e: Exception) {
                    AppLogger.log("Failed to copy ffprobe: ${e.message}")
                }
            }
        }

        val finalPath = binDir.absolutePath
        AppLogger.log("Final ffmpeg_location set to: $finalPath")
        return finalPath
    }
}
