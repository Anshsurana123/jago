// Required Notice: Copyright 2026 Ansh. (https://github.com/Anshsurana123/jago)
package com.example.jago.logic

data class ReminderData(
    val message: String,
    val triggerAtMillis: Long,
    val repeatIntervalMillis: Long? = null
)
