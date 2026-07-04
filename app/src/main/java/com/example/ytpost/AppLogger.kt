package com.example.ytpost

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private val _logs = MutableStateFlow<String>("--- System Started ---\n")
    val logs: StateFlow<String> = _logs

    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val timestamp = sdf.format(Date())
        val current = _logs.value
        _logs.value = "[$timestamp] $message\n$current"
    }

    fun clear() {
        _logs.value = "--- Logs Cleared ---\n"
    }
}
