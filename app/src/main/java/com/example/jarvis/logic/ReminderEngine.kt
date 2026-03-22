package com.example.jarvis.logic

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.jarvis.service.ReminderReceiver

object ReminderEngine {
    private const val TAG = "ReminderEngine"

    fun scheduleReminder(context: Context, reminder: ReminderData) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("EXTRA_MESSAGE", reminder.message)
            putExtra("EXTRA_REPEAT_INTERVAL", reminder.repeatIntervalMillis ?: -1L)
        }

        // Use unique request code to prevent overwriting
        val requestCode = System.currentTimeMillis().toInt()
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAtMillis,
                pendingIntent
            )
            val repeatInfo = if (reminder.repeatIntervalMillis != null) " (repeating every ${reminder.repeatIntervalMillis}ms)" else ""
            Log.d(TAG, "Reminder scheduled for ${reminder.triggerAtMillis}: ${reminder.message}$repeatInfo")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule exact alarm due to missing permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule reminder", e)
        }
    }
}
