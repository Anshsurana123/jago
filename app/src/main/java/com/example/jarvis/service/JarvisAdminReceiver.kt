package com.example.jarvis.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class JarvisAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("JarvisAdmin", "Device Admin Enabled")
        Toast.makeText(context, "Jarvis Device Admin Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("JarvisAdmin", "Device Admin Disabled")
        Toast.makeText(context, "Jarvis Device Admin Disabled", Toast.LENGTH_SHORT).show()
    }
}
