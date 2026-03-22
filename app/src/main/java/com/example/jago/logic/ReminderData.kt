package com.example.jago.logic

data class ReminderData(
    val message: String,
    val triggerAtMillis: Long,
    val repeatIntervalMillis: Long? = null
)
