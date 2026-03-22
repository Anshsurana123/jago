package com.example.jarvis.service.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.jarvis.logic.AlarmData

object AlarmEngine {
    
    fun setAlarm(context: Context, data: AlarmData) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Toast.makeText(context, "Please grant Alarm permission", Toast.LENGTH_LONG).show()
                return
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_MESSAGE", data.message)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            data.triggerTimeMillis.toInt(), // Use time as ID for simplicity
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            data.triggerTimeMillis,
            pendingIntent
        )
        
        Log.d("AlarmEngine", "Alarm set for: ${data.triggerTimeMillis}")
    }

    fun snoozeAlarm(context: Context) {
        val snoozeTime = System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes
        val data = AlarmData(snoozeTime, "Snoozed Alarm")
        setAlarm(context, data)
        Toast.makeText(context, "Alarm snoozed for 5 minutes", Toast.LENGTH_SHORT).show()
    }
}
