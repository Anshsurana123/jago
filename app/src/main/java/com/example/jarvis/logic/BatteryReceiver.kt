package com.example.jarvis.logic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log

class BatteryReceiver : BroadcastReceiver() {

    companion object {
        private var hasWarnedLowBattery = false
    }

    override fun onReceive(context: Context, intent: Intent) {

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        if (level == -1 || scale == -1) return

        val batteryPct = (level * 100) / scale

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging) {
            hasWarnedLowBattery = false
            return
        }

        if (batteryPct <= 30 && !hasWarnedLowBattery) {
            hasWarnedLowBattery = true

            JarvisTTS.speak(
                "Battery is getting low. You may want to connect a charger."
            )

            Log.d("JarvisBattery", "Low battery warning triggered at $batteryPct%")
        }
    }
}
