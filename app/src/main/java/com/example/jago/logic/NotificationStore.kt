package com.example.jago.logic

import android.util.Log

/**
 * Standalone singleton for storing notifications.
 * Lives independently of any Service so both
 * JagoNotificationListener and JagoAccessibilityService
 * can safely share it.
 */
object NotificationStore {

    data class NotificationItem(
        val appName: String,
        val sender: String?,
        val content: String,
        val raw: String
    )

    private val notifications = mutableListOf<NotificationItem>()
    private const val MAX = 20
    private const val TAG = "NotificationStore"

    fun add(item: NotificationItem) {
        synchronized(notifications) {
            if (notifications.lastOrNull()?.raw != item.raw) {
                notifications.add(item)
                if (notifications.size > MAX) notifications.removeAt(0)
                Log.d(TAG, "Stored: ${item.appName} | ${item.sender} | ${item.content}")
            }
        }
    }

    fun getAndClear(): List<NotificationItem> {
        synchronized(notifications) {
            if (notifications.isEmpty()) return emptyList()
            val result = notifications.takeLast(5).reversed().toList()
            notifications.clear()
            Log.d(TAG, "Reading ${result.size} notifications")
            return result
        }
    }

    fun hasAny(): Boolean {
        synchronized(notifications) {
            return notifications.isNotEmpty()
        }
    }

    fun clear() {
        synchronized(notifications) {
            notifications.clear()
        }
    }
}
