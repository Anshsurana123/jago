package com.example.jago

import android.Manifest
import android.net.Uri
import android.provider.Settings
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.jago.logic.JagoTTS
import com.example.jago.service.JagoAdminReceiver
import com.example.jago.service.WakeWordService
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var adminButton: Button
    private lateinit var overlayButton: Button
    private lateinit var brightnessButton: Button
    private lateinit var uploadRingtoneButton: Button

    private val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val pickAudioLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            saveCustomRingtone(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        actionButton = findViewById(R.id.actionButton)
        adminButton = findViewById(R.id.adminButton)
        overlayButton = findViewById(R.id.overlayButton)
        brightnessButton = findViewById(R.id.brightnessButton)
        uploadRingtoneButton = findViewById(R.id.uploadRingtoneButton)

        adminButton.setOnClickListener {
            requestDeviceAdminExemption()
        }

        overlayButton.setOnClickListener {
            requestOverlayPermission()
        }

        brightnessButton.setOnClickListener {
            requestWriteSettingsPermission()
        }

        uploadRingtoneButton.setOnClickListener {
            pickAudioLauncher.launch("audio/*")
        }

        actionButton.setOnClickListener {
            if (WakeWordService.isServiceRunning) {
                stopService()
            } else {
                startService()
            }
        }

        checkPermissions()
        JagoTTS.init(this)

        // Check if notification listener permission is granted
        if (!isNotificationListenerEnabled()) {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                JagoTTS.stopSpeaking()
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    private fun saveCustomRingtone(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = java.io.File(filesDir, "custom_alarm.mp3")
            val outputStream = java.io.FileOutputStream(file)
            
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(this, "Ringtone uploaded successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to save ringtone", e)
            Toast.makeText(this, "Failed to upload ringtone", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        JagoTTS.stopSpeaking()
        super.onPause()
    }

    private fun checkPermissions() {
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, permissions, 100)
        }
    }

    private fun hasPermissions(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted
            } else {
                // Permissions denied
                statusText.text = "Permissions required for Jago to work."
            }
        }
    }

    private fun startService() {
        val serviceIntent = Intent(this, WakeWordService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        updateUI(true)
    }

    private fun stopService() {
        val serviceIntent = Intent(this, WakeWordService::class.java)
        stopService(serviceIntent)
        updateUI(false)
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Brightness control permission already granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestDeviceAdminExemption() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, JagoAdminReceiver::class.java)
        
        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Jago needs this to lock your screen via voice.")
            startActivity(intent)
        } else {
            Toast.makeText(this, "Device Admin is already active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        return flat != null && flat.contains(packageName)
    }

    private fun updateUI(isRunning: Boolean) {
        if (isRunning) {
            statusText.text = getString(R.string.status_listening)
            actionButton.text = getString(R.string.action_stop)
        } else {
            statusText.text = getString(R.string.status_idle)
            actionButton.text = getString(R.string.action_start)
        }
    }
}
