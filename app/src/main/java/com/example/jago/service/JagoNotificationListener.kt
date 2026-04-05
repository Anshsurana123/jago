package com.example.jago.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.jago.logic.NotificationStore

class JagoNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("JagoNotification", "Notification Listener CONNECTED ✓")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("JagoNotification", "Notification Listener DISCONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName ?: return

            val skipPackages = listOf(
                "com.example.jago",
                "android",
                "com.android.systemui",
                "com.google.android.gms"
            )
            if (skipPackages.any { pkg.startsWith(it) }) return

            // Skip group summaries
            if ((sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return

            val extras = sbn.notification?.extras ?: return
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

            val content = if (bigText.isNotEmpty()) bigText else text
            if (content.isEmpty()) return

            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (e: Exception) { pkg }

            val sender: String?
            val messageContent: String

            when {
                title.isNotEmpty() && content != title -> {
                    sender = title
                    messageContent = content
                }
                content.contains(": ") -> {
                    val parts = content.split(": ", limit = 2)
                    sender = parts[0]
                    messageContent = parts[1]
                }
                else -> {
                    sender = null
                    messageContent = content
                }
            }

            val item = NotificationStore.NotificationItem(
                appName = appName,
                sender = sender,
                content = messageContent,
                raw = "$title $content"
            )

            NotificationStore.add(item, applicationContext)

        } catch (e: Exception) {
            Log.e("JagoNotification", "Error", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}
