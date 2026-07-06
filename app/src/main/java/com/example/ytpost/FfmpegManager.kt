package com.example.ytpost

import android.content.Context
import java.io.File

object FfmpegManager {
    fun getFfmpegPath(context: Context): String? {
        // On Android 10+ (API 29+), we MUST execute from nativeLibraryDir.
        // Files must be in jniLibs and named lib*.so
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val ffmpegFile = File(nativeDir, "libffmpeg.so")
        
        return if (ffmpegFile.exists()) {
            AppLogger.log("FFmpeg found in native library path: $nativeDir")
            nativeDir
        } else {
            AppLogger.log("CRITICAL: libffmpeg.so NOT found in $nativeDir")
            // Try to list files in nativeDir for debugging
            try {
                val files = File(nativeDir).list()?.joinToString(", ")
                AppLogger.log("Native dir contents: $files")
            } catch (e: Exception) {}
            nativeDir // Return it anyway and let Python handle diagnostics
        }
    }
}
