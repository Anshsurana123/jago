package com.example.jago.ui

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.jago.R
import com.example.jago.service.alarm.AlarmEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeScreen()
        setContentView(R.layout.activity_alarm)

        val message = intent.getStringExtra("ALARM_MESSAGE") ?: "Alarm"
        val triggerTime = System.currentTimeMillis() // Approximate

        findViewById<TextView>(R.id.tvAlarmMessage).text = message
        findViewById<TextView>(R.id.tvAlarmTime).text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(triggerTime))

        startAlarm()

        findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            stopAlarm()
            finish()
        }

        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            stopAlarm()
            AlarmEngine.snoozeAlarm(this)
            finish()
        }
    }

    private fun wakeScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun startAlarm() {
        try {
            // Maximize Alarm Volume (Safely)
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0)
            } catch (e: Exception) {
                e.printStackTrace() // Log but don't crash if permission missing
            }

            val customFile = java.io.File(filesDir, "custom_alarm.mp3")
            val notification = if (customFile.exists() && customFile.length() > 0) {
                android.net.Uri.fromFile(customFile)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, notification)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 1000), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 1000), 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: If custom file fails, try default
            if (mediaPlayer == null) {
                 try {
                     val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                     mediaPlayer = MediaPlayer().apply {
                        setDataSource(applicationContext, defaultUri)
                        prepare()
                        start()
                     }
                 } catch (ex: Exception) {
                     ex.printStackTrace()
                 }
            }
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
}
