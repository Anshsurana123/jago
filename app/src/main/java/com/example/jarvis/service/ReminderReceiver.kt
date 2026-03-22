package com.example.jarvis.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.jarvis.R
import com.example.jarvis.logic.JarvisTTS

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "jarvis_reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("EXTRA_MESSAGE") ?: "You have a reminder"
        val repeatInterval = intent.getLongExtra("EXTRA_REPEAT_INTERVAL", -1L)
        
        Log.d(TAG, "Reminder triggered: $message")

        createNotificationChannel(context)
        showNotification(context, message)
        speakReminder(message)
        
        // Reschedule if this is a repeating reminder
        if (repeatInterval > 0) {
            rescheduleRepeatingReminder(context, message, repeatInterval)
        }
    }
    
    private fun rescheduleRepeatingReminder(context: Context, message: String, intervalMillis: Long) {
        Log.d(TAG, "Rescheduling repeating reminder")
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextTriggerTime = System.currentTimeMillis() + intervalMillis
        
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("EXTRA_MESSAGE", message)
            putExtra("EXTRA_REPEAT_INTERVAL", intervalMillis)
        }
        
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
                nextTriggerTime,
                pendingIntent
            )
            Log.d(TAG, "Repeating reminder rescheduled for $nextTriggerTime (interval: ${intervalMillis}ms)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to reschedule repeating reminder due to missing permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule repeating reminder", e)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Jarvis Reminders"
            val descriptionText = "Notifications for your smart reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Jarvis Reminder")
            .setContentText("Reminder: $message")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun speakReminder(message: String) {
        JarvisTTS.speak("Reminder: $message")
    }
}
