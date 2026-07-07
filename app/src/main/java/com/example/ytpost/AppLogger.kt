package com.example.ytpost

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private const val MAX_LOG_LINES = 1000
    private val _logs = MutableStateFlow<String>("--- 🚀 System Started ---\n")
    val logs: StateFlow<String> = _logs

    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val timestamp = sdf.format(Date())
        val currentText = _logs.value
        
        // Prepend new log
        val newLogLine = "[$timestamp] $message"
        val updatedLogs = "$newLogLine\n$currentText"
        
        // Keep lines within limit
        val lines = updatedLogs.lines()
        val finalLogs = if (lines.size > MAX_LOG_LINES) {
            lines.take(MAX_LOG_LINES).joinToString("\n")
        } else {
            updatedLogs
        }

        _logs.value = finalLogs
    }

    fun logInfo(msg: String) = log("ℹ️ $msg")
    fun logSuccess(msg: String) = log("✅ $msg")
    fun logError(msg: String) = log("❌ $msg")
    fun logWarning(msg: String) = log("⚠️ $msg")
    fun logProcess(msg: String) = log("🔄 $msg")

    fun clear() {
        _logs.value = "--- 🗑️ Logs Cleared ---\n"
    }
}
