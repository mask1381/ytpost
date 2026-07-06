package com.example.ytpost

import android.content.Context
import java.io.File

object FfmpegManager {
    fun getFfmpegPath(context: Context): String? {
        // In Android 10+ (targetSdk 29+), we cannot execute binaries from filesDir.
        // We must place them in jniLibs and they will be extracted to nativeLibraryDir.
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val ffmpegFile = File(nativeDir, "libffmpeg.so")
        
        return if (ffmpegFile.exists()) {
            AppLogger.log("FFmpeg found in native library path: $nativeDir")
            nativeDir
        } else {
            AppLogger.log("FFmpeg NOT found in native library path: $nativeDir")
            // Fallback check in case extraction hasn't happened yet or path is different
            null
        }
    }
}
