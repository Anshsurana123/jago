package com.example.jarvis.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.jarvis.logic.Command
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ScheduledTaskEngine {

    private const val PREFS_NAME = "jarvis_scheduled_tasks"
    private const val TERMS_KEY = "tasks"
    private val gson = Gson()

    fun scheduleTask(context: Context, task: ScheduledTask) {
        val tasks = getAllTasks(context).toMutableList()
        tasks.add(task)
        saveTasks(context, tasks)

        scheduleAlarm(context, task)
        Log.d("ScheduledTaskEngine", "Task scheduled: ${task.id} at ${task.triggerAtMillis}")
    }

    fun removeTask(context: Context, id: Long) {
        val tasks = getAllTasks(context).toMutableList()
        val taskToRemove = tasks.find { it.id == id }
        
        if (taskToRemove != null) {
            tasks.remove(taskToRemove)
            saveTasks(context, tasks)
            cancelAlarm(context, taskToRemove)
            Log.d("ScheduledTaskEngine", "Task removed: $id")
        }
    }

    fun getAllTasks(context: Context): List<ScheduledTask> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(TERMS_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<ScheduledTask>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e("ScheduledTaskEngine", "Error parsing tasks", e)
            emptyList()
        }
    }

    fun getTaskById(context: Context, id: Long): ScheduledTask? {
        return getAllTasks(context).find { it.id == id }
    }

    private fun saveTasks(context: Context, tasks: List<ScheduledTask>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(tasks)
        prefs.edit().putString(TERMS_KEY, json).apply()
    }

    private fun scheduleAlarm(context: Context, task: ScheduledTask) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            putExtra("taskId", task.id)
            // Ensure unique PendingIntent per task
            data = android.net.Uri.parse("timer:${task.id}")
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            task.id.toInt(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAtMillis, pendingIntent)
                } else {
                    // Fallback or request permission - for now, just try typical exact
                    // In a real app we'd prompt user. Here we just log warning.
                    Log.w("ScheduledTaskEngine", "Cannot schedule exact alarm - permission missing")
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, task.triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
             Log.e("ScheduledTaskEngine", "Security exception scheduling alarm", e)
        }
    }

    private fun cancelAlarm(context: Context, task: ScheduledTask) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
             putExtra("taskId", task.id)
             data = android.net.Uri.parse("timer:${task.id}")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            task.id.toInt(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
