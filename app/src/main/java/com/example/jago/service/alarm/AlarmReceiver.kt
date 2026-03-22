package com.example.jago.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.jago.ui.AlarmActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm Triggered!")
        val message = intent.getStringExtra("ALARM_MESSAGE") ?: "Alarm"
        
        // 1. Create implicit intent for Activity
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra("ALARM_MESSAGE", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        
        // 2. Wrap in PendingIntent
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        // 3. Create Notification Channel (Required for O+)
        val channelId = "jago_alarm_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Jago Alarm",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for Jago Alarms"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(null, null) // Sound handled by Activity
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // 4. Build Notification with FullScreenIntent
        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Jago Alarm")
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true) // Critical for waking up
            .setAutoCancel(true)
            .setOngoing(true)
            .build()
            
        // 5. Notify
        notificationManager.notify(1337, notification)
        
        // 6. Redundant startActivity (legacy/some OEMs)
        context.startActivity(activityIntent)
    }
}
