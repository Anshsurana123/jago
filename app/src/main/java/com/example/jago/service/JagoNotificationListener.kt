package com.example.jago.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class JagoNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName ?: return

            // Skip our own notifications and system ones
            val skipPackages = listOf(
                "com.example.jago",
                "android",
                "com.android.systemui",
                "com.google.android.gms"
            )
            if (skipPackages.any { pkg.startsWith(it) }) return

            val extras = sbn.notification?.extras ?: return
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

            // Use bigText if available (contains full message)
            val content = if (bigText.isNotEmpty()) bigText else text
            if (content.isEmpty()) return

            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (e: Exception) { pkg }

            // Parse sender from title
            // WhatsApp format: "Mummy" or "Mummy: message" or "Group Name"
            val sender: String?
            val messageContent: String

            if (title.isNotEmpty() && content != title) {
                sender = title
                messageContent = content
            } else if (content.contains(": ")) {
                val parts = content.split(": ", limit = 2)
                sender = parts[0]
                messageContent = parts[1]
            } else {
                sender = null
                messageContent = content
            }

            val item = JagoAccessibilityService.Companion.NotificationItem(
                appName = appName,
                sender = sender,
                content = messageContent,
                raw = "$title $content"
            )

            JagoAccessibilityService.addNotification(item)
            Log.d("JagoNotification", "Captured: $appName | $sender | $messageContent")

        } catch (e: Exception) {
            Log.e("JagoNotification", "Error processing notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not needed for now
    }
}
