package com.example.jago.logic

import android.Manifest
import android.content.pm.PackageManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.jago.service.JagoAccessibilityService
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import com.example.jago.service.JagoAdminReceiver
import android.view.KeyEvent
import android.os.SystemClock
import android.app.NotificationManager
import android.content.ContentUris
import java.net.URLEncoder
import java.util.Locale
import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt

class ActionExecutor(private val context: Context) {

    init {
        JagoTTS.init(context)
    }

    fun execute(command: Command) {
        when (command.type) {
            CommandType.CALL -> {
                command.contactName?.let {
                    val resolver = ContactResolver(context)
                    when (val result = resolver.resolveContact(it)) {
                        is ContactResolver.ResolutionResult.Success -> {
                            speak("Calling ${result.contact.name}")
                            makeCall(result.contact.phoneNumber)
                        }
                        is ContactResolver.ResolutionResult.Ambiguous -> {
                            val names = result.matches.take(3).joinToString(" or ") { c -> c.name }
                            speak("I found multiple contacts: $names. Please be more specific.")
                        }
                        is ContactResolver.ResolutionResult.NoMatch -> {
                            speak("I couldn't find a contact named $it")
                        }
                    }
                }
            }
            CommandType.OPEN_WHATSAPP -> {
                speak("Opening WhatsApp")
                openWhatsApp()
            }
            CommandType.SEND_WHATSAPP_MESSAGE -> {
                val contact = command.contactName
                val message = command.messageBody

                if (contact == null) {
                     speak("Who should I send the message to?")
                     return
                }

                val resolver = ContactResolver(context)
                when (val result = resolver.resolveContact(contact)) {
                    is ContactResolver.ResolutionResult.Success -> {
                        if (message.isNullOrEmpty()) {
                             speak("What should the message say?")
                             // TODO: Implement follow-up state for message capture
                             return
                        }
                        speak("Sending message to ${result.contact.name}")
                        sendDirectWhatsAppMessage(result.contact.phoneNumber, message)
                    }
                    is ContactResolver.ResolutionResult.Ambiguous -> {
                        val names = result.matches.take(3).joinToString(" or ") { c -> c.name }
                        speak("I found multiple contacts: $names. Please say the full name.")
                    }
                    is ContactResolver.ResolutionResult.NoMatch -> {
                        speak("I couldn't find a contact named $contact")
                    }
                }
            }
            CommandType.LOCK_DEVICE -> {
                lockDevice()
            }
            CommandType.OPEN_APP -> {
                command.contactName?.let {
                    speak("Opening $it")
                    launchAppByName(it)
                }
            }
            CommandType.OPEN_SCHEDULE -> {
                 speak("Opening Scheduled Tasks")
                 val intent = Intent(context, com.example.jago.ui.ScheduledTasksActivity::class.java).apply {
                     addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                 }
                 context.startActivity(intent)
            }
            CommandType.SCHEDULED_ACTION -> {
                // This should be handled by WakeWordService, but if it reaches here, execute inner command?
                // Or just speak error.
                if (command.scheduledCommand != null) {
                    speak("Executing scheduled command now.")
                    execute(command.scheduledCommand)
                } else {
                    speak("I cannot execute this scheduled command.")
                }
            }
            CommandType.FLASHLIGHT_ON -> {
                toggleFlashlight(command, true)
            }
            CommandType.FLASHLIGHT_OFF -> {
                toggleFlashlight(command, false)
            }
            CommandType.VOLUME_UP -> {
                adjustVolume(command, AudioManager.ADJUST_RAISE)
            }
            CommandType.VOLUME_DOWN -> {
                adjustVolume(command, AudioManager.ADJUST_LOWER)
            }
            CommandType.VOLUME_MUTE -> {
                mutePhone()
            }
            CommandType.BRIGHTNESS_INCREASE -> {
                adjustBrightness(command, true)
            }
            CommandType.BRIGHTNESS_DECREASE -> {
                adjustBrightness(command, false)
            }
            CommandType.QUERY_BRIGHTNESS -> {
                queryBrightness()
            }
            CommandType.QUERY_VOLUME -> {
                queryVolume()
            }
            CommandType.QUERY_FLASHLIGHT -> {
                queryFlashlight()
            }
            CommandType.BATTERY_CHECK -> {
                checkBattery()
            }
            CommandType.OPEN_WIFI_SETTINGS -> {
                openSettings(Settings.ACTION_WIFI_SETTINGS)
            }
            CommandType.OPEN_BLUETOOTH_SETTINGS -> {
                openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            }
            CommandType.PLAY_MEDIA -> {
                Log.d("ActionExecutor", "Media intent detected: PLAY")
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            }
            CommandType.PAUSE_MEDIA -> {
                Log.d("ActionExecutor", "Media intent detected: PAUSE")
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            }
            CommandType.NEXT_MEDIA -> {
                Log.d("ActionExecutor", "Media intent detected: NEXT")
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            CommandType.PREVIOUS_MEDIA -> {
                Log.d("ActionExecutor", "Media intent detected: PREVIOUS")
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            CommandType.TAKE_SCREENSHOT -> {
                Log.d("ActionExecutor", "Screenshot intent detected")
                val success = JagoAccessibilityService.takeScreenshot()
                if (success) {
                    Log.d("ActionExecutor", "Screenshot triggered")
                } else {
                    speak("I need accessibility permission to take a screenshot. Please enable it in settings.")
                }
            }
            CommandType.SCREENSHOT_AND_WHATSAPP -> {
                val contact = command.contactName
                if (contact != null) {
                    smartCaptureAndShare(contact)
                } else {
                    speak("Who should I send the screenshot to?")
                }
            }
            CommandType.ENABLE_DND -> {
                Log.d("ActionExecutor", "Mode detected: ENABLE_DND")
                setDndState(true)
            }
            CommandType.DISABLE_DND -> {
                Log.d("ActionExecutor", "Mode detected: DISABLE_DND")
                setDndState(false)
            }
            CommandType.SILENT_MODE -> {
                Log.d("ActionExecutor", "Mode detected: SILENT")
                setSilentMode()
            }
            CommandType.FOCUS_MODE -> {
                Log.d("ActionExecutor", "Mode detected: FOCUS")
                applyFocusMode()
            }
            CommandType.CLICK_PHOTO -> {
                Log.d("ActionExecutor", "Camera action detected")
                try {
                    val packageManager = context.packageManager
                    val explicitIntent = packageManager.getLaunchIntentForPackage("com.android.camera")
                    
                    val intent = if (explicitIntent != null) {
                        Log.d("ActionExecutor", "Explicit camera launch")
                        explicitIntent
                    } else {
                        Log.w("ActionExecutor", "com.android.camera not found, falling back to implicit intent")
                        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    }
                    
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    
                    // Delay to allow camera UI to load
                    Handler(Looper.getMainLooper()).postDelayed({
                        JagoAccessibilityService.clickShutter()
                    }, 2500)
                } catch (e: Exception) {
                    Log.e("ActionExecutor", "Failed to launch camera", e)
                    speak("I couldn't open the camera.")
                }
            }
            CommandType.SET_REMINDER -> {
                val message = command.messageBody ?: ""
                val triggerMillis = command.triggerMillis ?: 0L
                val formattedTime = command.formattedTime ?: "the scheduled time"
                
                if (message.isNotEmpty() && triggerMillis > 0L) {
                    ReminderEngine.scheduleReminder(context, ReminderData(message, triggerMillis))
                    speak("Reminder set for $formattedTime")
                } else if (command.missingMessage) {
                    // Logic handled in WakeWordService for follow-up
                } else if (command.missingTime) {
                    // Logic handled in WakeWordService for follow-up
                }
            }
            CommandType.CLOSE_APP -> {
                speak("Closing the app")
                // Simple implementation: go home
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
            CommandType.AI_RESPONSE -> {
                command.aiResponse?.let { speak(it) }
            }
            CommandType.CALCULATE -> {
                val expression = command.messageBody ?: ""
                if (expression.isNotEmpty()) {
                    Log.d("ActionExecutor", "Calculating: $expression")
                    val result = CalculatorEngine.evaluate(expression)
                    Log.d("ActionExecutor", "Result: $result")
                    speak("The answer is $result")
                } else {
                    speak("I couldn't calculate that.")
                }
            }
            CommandType.SEARCH -> {
                val query = command.messageBody ?: ""
                val platform = command.searchPlatform ?: "google"
                
                if (query.isNotEmpty()) {
                    executeSearch(query, platform)
                } else {
                    speak("I didn't catch what to search for.")
                }
            }
            CommandType.SET_ALARM -> {
                if (command.missingTime) {
                    speak("What time should I set the alarm?")
                } else {
                    val hour = command.hour ?: 9
                    val minute = command.minute ?: 0
                    setAlarm(hour, minute, command.messageBody)
                }
            }


            CommandType.SET_ALARM_CUSTOM -> {
                 val hour = command.hour ?: 9
                 val minute = command.minute ?: 0
                 
                 val calendar = java.util.Calendar.getInstance()
                     calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
                     calendar.set(java.util.Calendar.MINUTE, minute)
                     calendar.set(java.util.Calendar.SECOND, 0)
                     calendar.set(java.util.Calendar.MILLISECOND, 0)
                     
                     if (calendar.timeInMillis <= System.currentTimeMillis()) {
                         calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                     }
                     
                     val data = com.example.jago.logic.AlarmData(
                         triggerTimeMillis = calendar.timeInMillis,
                         message = command.messageBody ?: "Jago Alarm"
                     )
                     
                     com.example.jago.service.alarm.AlarmEngine.setAlarm(context, data)
                     speak("Alarm set for $hour ${if(minute < 10) "0$minute" else "$minute"}")
                }
            CommandType.PLAY_SPOTIFY -> {
                val query = command.messageBody ?: ""
                Log.d("ActionExecutor", "Spotify playback requested: $query")
                
                try {
                    // 1. Launch Spotify Search
                    // We revert to ACTION_VIEW because MEDIA_PLAY_FROM_SEARCH can be unreliable for specific songs
                    val uri = Uri.parse("spotify:search:${URLEncoder.encode(query, "UTF-8")}")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    
                    speak(if (query.isNotEmpty()) "Playing $query on Spotify" else "Opening Spotify")

                    // 2. Delayed Accessibility Trigger
                    // Wait for Spotify UI to load, then click the first result.
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d("ActionExecutor", "Triggering Spotify auto-click...")
                        val success = JagoAccessibilityService.clickFirstSpotifyResult()
                        if (!success) {
                             // Retry once more after a short delay
                             Handler(Looper.getMainLooper()).postDelayed({
                                 JagoAccessibilityService.clickFirstSpotifyResult()
                             }, 1000)
                        }
                    }, 2500) // 2.5 seconds delay

                } catch (e: Exception) {
                    Log.e("ActionExecutor", "Spotify launch failed", e)
                    speak("Spotify is not installed or I couldn't open it.")
                }
            }
            CommandType.SEND_RECENT_PHOTO -> {
                shareRecentPhoto(command.contactName, command.searchPlatform)
            }
            CommandType.READ_NOTIFICATIONS -> {
                val notifications = com.example.jago.service.JagoAccessibilityService
                    .readNotifications()
                speak(notifications)
            }
            CommandType.READ_SCREEN -> {
                val screenText = com.example.jago.service.JagoAccessibilityService
                    .readScreen()
                speak(screenText)
            }
            CommandType.UNKNOWN -> {
                speak(command.aiResponse ?: "I didn't understand that command.")
            }
        }
    }
    
    // ... helper functions ...

    private fun makeCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Log.d("ActionExecutor", "Placing direct call: $phoneNumber")
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$phoneNumber")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("ActionExecutor", "Direct call failed, falling back to dialer", e)
                openDialer(phoneNumber)
            }
        } else {
            Log.d("ActionExecutor", "Permission denied for ACTION_CALL, falling back to ACTION_DIAL")
            openDialer(phoneNumber)
        }
    }

    private fun openDialer(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$phoneNumber")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Dialer fallback failed", e)
            speak("Failed to open dialer.")
        }
    }

    private fun openWhatsApp() {
        Log.d("ActionExecutor", "Opening WhatsApp")
        val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            speak("WhatsApp is not installed.")
        }
    }

    companion object {
        fun formatPhoneNumber(phoneNumber: String): String {
            return phoneNumber.replace("[^0-9]".toRegex(), "")
        }
    }

    private fun lockDevice() {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, JagoAdminReceiver::class.java)
        
        if (dpm.isAdminActive(adminComponent)) {
            Log.d("JagoAdmin", "Executing lock command")
            speak("Locking the device")
            dpm.lockNow()
        } else {
            Log.d("JagoAdmin", "Admin not active")
            speak("I need device administrator permission to lock the screen. Please enable it in the app.")
        }
    }
    
    private fun launchAppByName(appName: String) {
        val packageName = findPackageName(appName)
        if (packageName != null) {
            Log.d("ActionExecutor", "Launching package: $packageName for app: $appName")
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                speak("I couldn't launch $appName.")
            }
        } else {
            Log.d("ActionExecutor", "Resolution FAILED for spoken name: $appName")
            speak("$appName is not installed.")
        }
    }

    private fun findPackageName(appName: String): String? {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val targetName = appName.lowercase(Locale.getDefault()).trim()
        
        Log.d("ActionExecutor", "Scanning ${apps.size} installed apps for: $targetName")
        
        // 1. Exact match first
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.getDefault()).trim()
            if (label == targetName) {
                Log.d("ActionExecutor", "EXACT MATCH found! Label: $label -> Package: ${app.packageName}")
                return app.packageName
            }
        }
        
        // 2. Contains match
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.getDefault()).trim()
            if (label.contains(targetName)) {
                Log.d("ActionExecutor", "CONTAINS MATCH found! Label: $label -> Package: ${app.packageName}")
                return app.packageName
            }
        }

        // 3. Fuzzy Match
        var bestPackage: String? = null
        var minDistance = Int.MAX_VALUE
        val threshold = 2

        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.getDefault()).trim()
            val distance = FuzzyMatcher.calculateDistance(targetName, label)
            
            if (distance < minDistance) {
                minDistance = distance
                bestPackage = app.packageName
            }
        }

        return if (minDistance <= threshold) {
            Log.d("ActionExecutor", "FUZZY MATCH found! Distance: $minDistance -> Package: $bestPackage")
            bestPackage
        } else {
            null
        }
    }

    private fun sendDirectWhatsAppMessage(phoneNumber: String, message: String) {
        val cleanNumber = formatPhoneNumber(phoneNumber)
        Log.d("ActionExecutor", "Sending Direct WhatsApp Message to: $cleanNumber")
        
        if (cleanNumber.isEmpty()) {
             speak("The phone number is invalid.")
             return
        }

        try {
            val jid = "$cleanNumber@s.whatsapp.net"
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra("jid", jid) // Direct checkmate to specific chat
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            
            // Prime the Auto-Sender
            // We reuse primeDirectShare because the mechanism is identical:
            // "Wait for WhatsApp to open, then click Send button"
            com.example.jago.service.JagoAccessibilityService.primeDirectShare()
            
        } catch (e: Exception) {
            Log.e("ActionExecutor", "WhatsApp Direct Message failed", e)
            speak("I couldn't send the message. Make sure WhatsApp is installed.")
        }
    }

    private fun smartCaptureAndShare(contactName: String) {
        speak("Taking screenshot for $contactName")
        
        // 1. Hide UI
        com.example.jago.ui.AssistantUIBridge.closeUI()
        
        // 2. Wait and Capture
        Handler(Looper.getMainLooper()).postDelayed({
            val success = JagoAccessibilityService.takeScreenshot()
            if (success) {
                // 3. Wait for Save and Share
                Handler(Looper.getMainLooper()).postDelayed({
                    shareLatestScreenshot(contactName)
                }, 1500)
            } else {
                speak("I couldn't take the screenshot.")
            }
        }, 500)
    }

    private fun shareLatestScreenshot(contactName: String) {
        val uri = getLastScreenshotUri()
        if (uri != null) {
            val resolver = ContactResolver(context)
            when (val result = resolver.resolveContact(contactName)) {
                is ContactResolver.ResolutionResult.Success -> {
                    speak("Sending screenshot to ${result.contact.name}")
                    sendDirectWhatsAppImage(result.contact.phoneNumber, uri)
                }
                is ContactResolver.ResolutionResult.Ambiguous -> {
                     speak("Multiple contacts found for $contactName.")
                }
                is ContactResolver.ResolutionResult.NoMatch -> {
                     speak("Contact $contactName not found.")
                }
            }
        } else {
            speak("I couldn't find the screenshot.")
        }
    }

    private fun getLastScreenshotUri(): Uri? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)
                return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }

    private fun sendDirectWhatsAppImage(phoneNumber: String, imageUri: Uri) {
         val cleanNumber = formatPhoneNumber(phoneNumber)
         if (cleanNumber.isEmpty()) return

         try {
            val jid = "$cleanNumber@s.whatsapp.net"
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra("jid", jid)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(intent)
            com.example.jago.service.JagoAccessibilityService.primeDirectShare()
            
        } catch (e: Exception) {
            Log.e("ActionExecutor", "WhatsApp Image Share failed", e)
            speak("Failed to share image.")
        }
    }
    
    private fun toggleFlashlight(command: Command, enabled: Boolean) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList[0]
            if (!enabled) {
                cameraManager.setTorchMode(cameraId, false)
                speak("Flashlight turned off")
                return
            }

            val numeric = command.numericValue
            if (numeric != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                // Use manual key definition to bypass unresolved reference in some SDK environments
                val STRENGTH_KEY = CameraCharacteristics.Key<Int>("android.flash.info.strengthMaxLevel", Int::class.java)
                val maxLevel = characteristics.get(STRENGTH_KEY) ?: 1
                
                if (command.type == CommandType.QUERY_FLASHLIGHT) {
                    // Logic handled in queryFlashlight, but we'll leave this check for completeness
                    return
                }

                var targetLevel = if (command.isRelative) {
                    val currentLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                         try { cameraManager.getTorchStrengthLevel(cameraId) } catch(e: Exception) { maxLevel / 2 }
                    } else maxLevel / 2
                    
                    val delta = (numeric * maxLevel / 100.0).roundToInt()
                    (currentLevel + delta).coerceIn(1, maxLevel)
                } else {
                    if (numeric <= 100) (numeric * maxLevel / 100.0).roundToInt() else numeric.coerceIn(1, maxLevel)
                }
                
                // Redundancy check
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        val current = cameraManager.getTorchStrengthLevel(cameraId)
                        if (current == targetLevel) {
                            speak("Flashlight is already at that level.")
                            return
                        }
                    } catch(e: Exception) {}
                }

                cameraManager.turnOnTorchWithStrengthLevel(cameraId, targetLevel.coerceAtLeast(1))
                Log.d("ActionExecutor", "Torch set to $targetLevel/$maxLevel")
                speak("Flashlight set to $numeric percent")
            } else {
                cameraManager.setTorchMode(cameraId, true)
                speak("Flashlight turned on")
            }
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Flashlight error", e)
            speak("I couldn't control the flashlight.")
        }
    }

    private fun queryFlashlight() {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList[0]
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val current = cameraManager.getTorchStrengthLevel(cameraId)
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val STRENGTH_KEY = CameraCharacteristics.Key<Int>("android.flash.info.strengthMaxLevel", Int::class.java)
                val maxLevel = characteristics.get(STRENGTH_KEY) ?: 1
                
                if (current == 0) {
                    speak("The flashlight is currently off.")
                } else {
                    val percentage = (current * 100.0 / maxLevel).roundToInt()
                    speak("The flashlight is on at $percentage percent brightness.")
                }
            } else {
                speak("I can't detect the exact flashlight level on this version of Android, but it appears to be on.")
            }
        } catch (e: Exception) {
            speak("I couldn't check the flashlight status.")
        }
    }

    private fun adjustVolume(command: Command, direction: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val numeric = command.numericValue

        if (numeric != null) {
            val targetVolume = if (command.isRelative) {
                val delta = (numeric * maxVolume / 100.0).roundToInt()
                if (direction == AudioManager.ADJUST_RAISE) currentVolume + delta else currentVolume - delta
            } else {
                (numeric * maxVolume / 100.0).roundToInt()
            }
            val finalVolume = targetVolume.coerceIn(0, maxVolume)
            
            if (finalVolume == currentVolume) {
                val percent = (currentVolume * 100.0 / maxVolume).roundToInt()
                speak("Volume is already at $percent percent.")
                return
            }

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, finalVolume, AudioManager.FLAG_SHOW_UI)
            speak("Volume set to $numeric percent")
        } else {
            if (direction == AudioManager.ADJUST_RAISE && currentVolume == maxVolume) {
                speak("Volume is already at maximum.")
                return
            }
            if (direction == AudioManager.ADJUST_LOWER && currentVolume == 0) {
                speak("Volume is already muted.")
                return
            }
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            val newVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val percent = (newVol * 100.0 / maxVolume).roundToInt()
            speak("Volume $percent percent")
        }
    }

    private fun queryVolume() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percentage = if (max > 0) (current * 100.0 / max).roundToInt() else 0
        speak("The volume is currently at $percentage percent.")
    }

    private fun mutePhone() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current == 0) {
            speak("Phone is already muted.")
            return
        }
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        speak("Phone muted")
    }

    private fun adjustBrightness(command: Command, increase: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            speak("I need permission to modify settings. Please enable it.")
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:" + context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }

        try {
            val currentBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            val numeric = command.numericValue
            
            val newBrightness = if (numeric != null) {
                if (command.isRelative) {
                    val delta = (numeric * 255 / 100.0).roundToInt()
                    if (increase) currentBrightness + delta else currentBrightness - delta
                } else {
                    (numeric * 255 / 100.0).roundToInt()
                }
            } else {
                val delta = if (increase) 51 else -51 // ~20% change
                currentBrightness + delta
            }

            val finalBrightness = newBrightness.coerceIn(0, 255)
            
            if (finalBrightness == currentBrightness) {
                val percent = (currentBrightness * 100.0 / 255).roundToInt()
                speak("Brightness is already at $percent percent.")
                return
            }

            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, finalBrightness)
            val finalPercent = (finalBrightness * 100.0 / 255).roundToInt()
            speak("Brightness set to $finalPercent percent")
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Brightness error", e)
            speak("I couldn't adjust the brightness.")
        }
    }

    private fun queryBrightness() {
        try {
            val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            val percentage = (current * 100.0 / 255).roundToInt()
            speak("The screen brightness is currently at $percentage percent.")
        } catch (e: Exception) {
            speak("I couldn't check the brightness.")
        }
    }

    private fun checkBattery() {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        speak("The battery level is $level percent.")
    }

    private fun openSettings(action: String) {
        val intent = Intent(action)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            speak("Opening settings")
        } catch (e: Exception) {
            speak("I couldn't open the settings.")
        }
    }

    private fun speak(text: String) {
        JagoTTS.speak(text)
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventTime = SystemClock.uptimeMillis()

        val downEvent = KeyEvent(
            eventTime, eventTime,
            KeyEvent.ACTION_DOWN, keyCode, 0
        )
        val upEvent = KeyEvent(
            eventTime, eventTime,
            KeyEvent.ACTION_UP, keyCode, 0
        )

        try {
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            Log.d("ActionExecutor", "KeyEvent dispatched: $keyCode")
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Failed to dispatch media key: $keyCode", e)
        }
    }

    private fun setDndState(enabled: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (nm.isNotificationPolicyAccessGranted) {
                val filter = if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL
                nm.setInterruptionFilter(filter)
                Log.d("ActionExecutor", "DND state changed: $enabled")
                speak("Do not disturb turned ${if (enabled) "on" else "off"}")
            } else {
                speak("I need permission to control do not disturb. Please enable it in settings.")
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } else {
            speak("I can't control do not disturb on this version of Android.")
        }
    }

    private fun setSilentMode() {
        Log.d("ActionExecutor", "Silent mode activated")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        speak("Phone silenced")
    }

    private fun applyFocusMode() {
        Log.d("ActionExecutor", "Focus mode applied")
        setDndState(true)
        setSilentMode()
        
        // Safe default brightness reduction
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
            try {
                val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                val target = (current - 51).coerceIn(0, 255)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target)
            } catch (e: Exception) {}
        }
        
        speak("Focus mode enabled. Good luck.")
    }

    private fun executeSearch(query: String, platform: String) {
        val lowerPlatform = platform.lowercase(java.util.Locale.getDefault())

        try {
            when (lowerPlatform) {
                "youtube" -> {
                    Log.d("ActionExecutor", "Searching YouTube for: $query")
                    speak("Searching YouTube for $query")
                    val intent = Intent(Intent.ACTION_SEARCH)
                    intent.setPackage("com.google.android.youtube")
                    intent.putExtra("query", query)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
                "google" -> {
                    Log.d("ActionExecutor", "Searching Google for: $query")
                    speak("Searching Google for $query")
                    val intent = Intent(Intent.ACTION_WEB_SEARCH)
                    intent.putExtra(android.app.SearchManager.QUERY, query)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
                else -> {
                    // Dynamic App Search
                    val packageName = resolvePackageName(lowerPlatform)
                    if (packageName != null) {
                        Log.d("ActionExecutor", "Dynamic search detected. App: $lowerPlatform -> Package: $packageName")
                        speak("Searching $lowerPlatform for $query")
                        
                        // Attempt 1: ACTION_SEARCH targeted at package
                        val intent = Intent(Intent.ACTION_SEARCH)
                        intent.setPackage(packageName)
                        intent.putExtra(android.app.SearchManager.QUERY, query)
                        intent.putExtra("query", query) // Some apps listen to this
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        
                        // Verify if the app handles ACTION_SEARCH
                        val activities = context.packageManager.queryIntentActivities(intent, 0)
                        if (activities.isNotEmpty()) {
                             context.startActivity(intent)
                        } else {
                            // Attempt 2: Launch main activity with extras
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                launchIntent.putExtra(android.app.SearchManager.QUERY, query)
                                launchIntent.putExtra("query", query)
                                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(launchIntent)
                            } else {
                                speak("I couldn't open $lowerPlatform.")
                            }
                        }
                    } else {
                        speak("I couldn't find an app named $lowerPlatform.")
                        // Fallback to Google?
                        // executeSearch(query, "google") // Optional: Fallback
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Search failed for $platform", e)
            speak("I couldn't perform the search on $platform.")
        }
    }

    private fun shareRecentPhoto(contactName: String?, appName: String?) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            speak("I need gallery permission to access your photos.")
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:" + context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }

        val photoUri = getRecentPhotoUri()
        if (photoUri != null) {
            Log.d("ActionExecutor", "Recent photo retrieved: $photoUri")
            
            // Native Share Intent Construction
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, photoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            var directShareSuccess = false

            // Hybrid Automation: Direct Share Attempt
            // If contact is resolvable and app is WhatsApp (or unspecified), try JID injection first.
            if (contactName != null && (appName == null || appName.contains("whatsapp", true))) {
                val resolver = ContactResolver(context)
                val result = resolver.resolveContact(contactName)
                
                if (result is ContactResolver.ResolutionResult.Success) {
                    val rawPhone = result.contact.phoneNumber
                    val cleanPhone = formatPhoneNumber(rawPhone)
                    
                    if (cleanPhone.isNotEmpty()) {
                        Log.d("ActionExecutor", "Attempting Direct Share to: $cleanPhone")
                        try {
                            val directIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, photoUri)
                                putExtra("jid", "$cleanPhone@s.whatsapp.net") // JID Injection
                                setPackage("com.whatsapp")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(directIntent)
                            Log.d("ActionExecutor", "Direct Share launched successfully")
                            
                            // Prime Auto-Send for Direct Share
                            com.example.jago.service.JagoAccessibilityService.primeDirectShare()
                            
                            directShareSuccess = true
                            // Speak confirmation here as we bypass accessibility feedback
                            speak("Opening WhatsApp chat with ${result.contact.name}")
                        } catch (e: Exception) {
                            Log.w("ActionExecutor", "Direct Share failed -> Falling back to Chooser", e)
                            directShareSuccess = false
                            // Fallback proceeds below
                        }
                    }
                }
            }

            if (!directShareSuccess) {
                // PRIMING: Only needed for Chooser flow
                if (appName != null || contactName != null) {
                    com.example.jago.service.JagoAccessibilityService.primeAutomation(appName, contactName)
                    // TTS is deferred to AccessibilityService
                } else {
                    speak("Here is your recent photo.")
                }

                try {
                    // Strict Native Chooser
                    val chooser = Intent.createChooser(shareIntent, "Share Photo")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                    Log.d("ActionExecutor", "Photo sharing intent launched via Chooser")
                } catch (e: Exception) {
                    Log.e("ActionExecutor", "Share intent failed", e)
                    speak("I couldn't share the photo.")
                }
            }
        } else {
            speak("I couldn't find any recent photo.")
        }
    }

    private fun getRecentPhotoUri(): Uri? {
         val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val id = cursor.getLong(idColumn)
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Error querying media store", e)
        }
        return null
    }


    private fun resolvePackageName(appName: String): String? {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(0)
        
        var bestMatch: String? = null
        var bestScore = 0

        for (pkg in packages) {
            val label = pkg.applicationInfo.loadLabel(pm).toString().lowercase(java.util.Locale.getDefault())
            
            // Exact match
            if (label == appName) return pkg.packageName
            
            // Contains match
            if (label.contains(appName)) {
                // Heuristic: Shorter labels that contain the query are likely better matches 
                // (e.g., "Spotify" vs "Spotify: Listen to music")
                val score = 1000 - label.length // Prefer shorter
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = pkg.packageName
                }
            }
        }
        return bestMatch
    }

    private fun setAlarm(hour: Int, minute: Int, message: String? = "Jago Alarm") {
        try {
            Log.d("ActionExecutor", "Setting alarm for $hour:$minute. Message: $message")
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                message?.let { putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, it) }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            speak("Alarm set for $hour ${if(minute < 10) "0$minute" else "$minute"}")
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Failed to set alarm", e)
            speak("I couldn't access the alarm clock.")
        }
    }

    fun stopSpeaking() {
        JagoTTS.stopSpeaking()
    }

    fun shutdown() {
        JagoTTS.shutdown()
    }
}
