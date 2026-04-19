// Required Notice: Copyright 2026 Ansh. (https://github.com/Anshsurana123/jago)
package com.example.jago.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class JagoAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("JagoAdmin", "Device Admin Enabled")
        Toast.makeText(context, "Jago Device Admin Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("JagoAdmin", "Device Admin Disabled")
        Toast.makeText(context, "Jago Device Admin Disabled", Toast.LENGTH_SHORT).show()
    }
}
