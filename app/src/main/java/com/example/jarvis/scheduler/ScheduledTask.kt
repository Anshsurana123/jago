package com.example.jarvis.scheduler

import com.example.jarvis.logic.Command

data class ScheduledTask(
    val id: Long,
    val command: Command,
    val triggerAtMillis: Long
)
