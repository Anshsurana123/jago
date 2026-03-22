package com.example.jarvis.logic

data class AlarmData(
    val triggerTimeMillis: Long,
    val message: String = "Alarm",
    val isRecurring: Boolean = false,
    val ringtoneUri: String? = null // Future proofing
)
