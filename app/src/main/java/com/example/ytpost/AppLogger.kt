package com.example.ytpost

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private const val MAX_LOG_LINES = 500
    private val _logs = MutableStateFlow<String>("--- System Started ---\n")
    val logs: StateFlow<String> = _logs

    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val timestamp = sdf.format(Date())
        val currentLogs = _logs.value.lines()
        
        // Keep only the last MAX_LOG_LINES
        val trimmedLogs = if (currentLogs.size > MAX_LOG_LINES) {
            currentLogs.take(MAX_LOG_LINES).joinToString("\n")
        } else {
            _logs.value
        }

        _logs.value = "[$timestamp] $message\n$trimmedLogs"
    }

    fun clear() {
        _logs.value = "--- Logs Cleared ---\n"
    }
}
