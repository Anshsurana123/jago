package com.example.jago.scheduler

import com.example.jago.logic.Command

data class ScheduledTask(
    val id: Long,
    val command: Command,
    val triggerAtMillis: Long
)
