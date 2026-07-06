package com.example.ytpost

import android.content.Context
import java.io.File

object FfmpegManager {
    fun getFfmpegPath(context: Context): String? {
        // Abandoning the 'bin' folder strategy due to W^X restrictions on Android 10+.
        // Direct execution from nativeLibraryDir is the only reliable way.
        val nativeDir = context.applicationInfo.nativeLibraryDir
        
        val ffmpegFile = File(nativeDir, "libffmpeg.so")
        val ffprobeFile = File(nativeDir, "libffprobe.so")

        if (ffmpegFile.exists() && ffprobeFile.exists()) {
            AppLogger.log("Native binaries located at: $nativeDir")
            return nativeDir
        } else {
            AppLogger.log("CRITICAL: libffmpeg.so or libffprobe.so MISSING in $nativeDir")
            return nativeDir
        }
    }
}
