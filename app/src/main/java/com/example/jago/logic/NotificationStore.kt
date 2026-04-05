package com.example.jago.logic

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

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
    private const val PREFS_NAME = "jago_notifications"
    private const val KEY_NOTIFICATIONS = "pending_notifications"

    // Call once from JagoApp.onCreate() or WakeWordService.onCreate()
    fun init(context: Context) {
        synchronized(notifications) {
            if (notifications.isEmpty()) {
                loadFromPrefs(context)
            }
        }
    }

    fun add(item: NotificationItem, context: Context? = null) {
        synchronized(notifications) {
            if (notifications.lastOrNull()?.raw != item.raw) {
                notifications.add(item)
                if (notifications.size > MAX) notifications.removeAt(0)
                Log.d(TAG, "Stored: ${item.appName} | ${item.sender} | ${item.content}")
                context?.let { saveToPrefs(it) }
            }
        }
    }

    fun getAndClear(context: Context? = null): List<NotificationItem> {
        synchronized(notifications) {
            if (notifications.isEmpty()) return emptyList()
            val result = notifications.takeLast(5).reversed().toList()
            notifications.clear()
            context?.let { clearPrefs(it) }
            Log.d(TAG, "Reading ${result.size} notifications")
            return result
        }
    }

    fun hasAny(): Boolean {
        synchronized(notifications) {
            return notifications.isNotEmpty()
        }
    }

    fun clear(context: Context? = null) {
        synchronized(notifications) {
            notifications.clear()
            context?.let { clearPrefs(it) }
        }
    }

    private fun saveToPrefs(context: Context) {
        try {
            val arr = JSONArray()
            notifications.forEach { item ->
                arr.put(JSONObject().apply {
                    put("appName", item.appName)
                    put("sender", item.sender ?: "")
                    put("content", item.content)
                    put("raw", item.raw)
                })
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_NOTIFICATIONS, arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save notifications", e)
        }
    }

    private fun loadFromPrefs(context: Context) {
        try {
            val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_NOTIFICATIONS, null) ?: return
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                notifications.add(NotificationItem(
                    appName = obj.getString("appName"),
                    sender = obj.getString("sender").ifEmpty { null },
                    content = obj.getString("content"),
                    raw = obj.getString("raw")
                ))
            }
            Log.d(TAG, "Loaded ${notifications.size} notifications from prefs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load notifications", e)
        }
    }

    private fun clearPrefs(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_NOTIFICATIONS).apply()
    }
}
