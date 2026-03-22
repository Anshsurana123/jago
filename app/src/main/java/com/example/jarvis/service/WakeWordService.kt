package com.example.jarvis.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.view.Gravity
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineException
import com.example.jarvis.BuildConfig
import com.example.jarvis.MainActivity
import com.example.jarvis.R
import com.example.jarvis.logic.ActionExecutor
import com.example.jarvis.logic.Command
import com.example.jarvis.logic.CommandParser
import com.example.jarvis.logic.CommandType
import com.example.jarvis.logic.JarvisTTS
import com.example.jarvis.service.speech.AndroidSTTAdapter
import com.example.jarvis.service.speech.SpeechAdapter
import com.example.jarvis.logic.CerebrasClient
import kotlinx.coroutines.*

class WakeWordService : Service() {

    companion object {
        var isServiceRunning = false
    }

    private var porcupineManager: PorcupineManager? = null
    private var isProcessing = false
    
    private var speechAdapter: SpeechAdapter? = null
    private var actionExecutor: ActionExecutor? = null
    private val commandParser = CommandParser()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Battery Receiver
    private val batteryReceiver = com.example.jarvis.logic.BatteryReceiver()

    // Reminder state
    private var isWaitingForReminderTime = false
    private var isWaitingForReminderMessage = false
    private var pendingReminderMessage: String? = null
    private var pendingTriggerMillis: Long? = null
    private var pendingFormattedTime: String? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        startForeground(1, createNotification())
        
        actionExecutor = ActionExecutor(this)
        speechAdapter = AndroidSTTAdapter(this)
        
        // Auto-close overlay when speech ends
        JarvisTTS.onSpeechStateChange = { isSpeaking ->
            if (!isSpeaking) {
                 hideOverlayWithDelay()
            }
        }
        
        // Register Battery Receiver dynamically
        registerReceiver(batteryReceiver, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        Log.d("WakeWordService", "BatteryReceiver registered")
        
        initPorcupine()
    }

    private fun initPorcupine() {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(BuildConfig.PORCUPINE_ACCESS_KEY)
                .setKeywords(arrayOf(ai.picovoice.porcupine.Porcupine.BuiltInKeyword.JARVIS))
                .setSensitivity(0.5f)
                .setErrorCallback { exception ->
                    Log.e("Jarvis", "Porcupine error", exception)
                }
                .build(applicationContext) { _ ->
                    // Wake word detected
                    Log.d("Jarvis", "Wake word detected! Showing System Overlay")
                    try {
                        porcupineManager?.stop()
                    } catch (e: Exception) {}
                    showOverlay()
                }
            
            porcupineManager?.start()
        } catch (e: PorcupineException) {
            Log.e("Jarvis", "Porcupine init failed", e)
        }
    }

    private fun showOverlay() {
        actionExecutor?.stopSpeaking()
        
        // Reset Bridge state
        com.example.jarvis.ui.AssistantUIBridge.updateStatus("Listening...")
        com.example.jarvis.ui.AssistantUIBridge.updatePartial("")
        
        // Launch Activity
        val intent = Intent(this, com.example.jarvis.ui.AssistantActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        
        startListening()
    }

    private fun startListening() {
        com.example.jarvis.ui.AssistantUIBridge.updateStatus("Listening...")
        speechAdapter?.startListening(object : SpeechAdapter.Callback {
            override fun onResult(text: String) {
                Log.d("Jarvis", "Speech result: $text")
                com.example.jarvis.ui.AssistantUIBridge.updateStatus("Processing...")
                processCommand(text)
            }

            override fun onError(error: String) {
                Log.e("Jarvis", "Speech error: $error")
                com.example.jarvis.ui.AssistantUIBridge.updateStatus("Error: $error")
                hideOverlayWithDelay()
            }

            override fun onPartialResult(text: String) {
                com.example.jarvis.ui.AssistantUIBridge.updatePartial(text)
            }
        })
    }

    private var isWaitingForAlarmTime = false

    private fun processCommand(text: String) {
        // 1. Handle follow-up states
        if (isWaitingForReminderTime) {
            handleReminderTimeFollowUp(text)
            return
        }
        
        if (isWaitingForReminderMessage) {
            handleReminderMessageFollowUp(text)
            return
        }

        if (isWaitingForAlarmTime) {
            handleAlarmTimeFollowUp(text)
            return
        }

        // 2. Normal parsing
        val commands = commandParser.parse(text)
        val validCommands = commands.filter { it.type != CommandType.UNKNOWN }

        if (validCommands.isNotEmpty()) {
            val command = validCommands.first()
            Log.d("WakeWordService", "Executing local command: ${command.type}")
            
            when (command.type) {
                CommandType.SET_REMINDER -> handleNewReminderCommand(command)
                CommandType.SET_ALARM_CUSTOM, CommandType.SET_ALARM -> handleNewAlarmCommand(command)
                CommandType.SCHEDULED_ACTION -> handleScheduledCommand(command)
                else -> {
                    validCommands.forEach { cmd ->
                         actionExecutor?.execute(cmd)
                    }
                }
            }
        } else {
            Log.d("WakeWordService", "No local command matched → Routing to Cerebras AI")
            callCerebrasAsync(text)
        }
    }

    private fun handleNewAlarmCommand(command: Command) {
        if (command.missingTime) {
             Log.d("WakeWordService", "Alarm missing time")
             isWaitingForAlarmTime = true
             speechAdapter?.isFollowUpListening = true
             JarvisTTS.speakWithCallback("What time should I set the alarm?") {
                 startListening()
             }
        } else {
            actionExecutor?.execute(command)
            hideOverlayWithDelay()
        }
    }

    private fun handleAlarmTimeFollowUp(text: String) {
        // Parse just the time, assuming user says "7 am" or "after 5 mins"
        // We reuse the updated parseAlarm logic by prepending "wake me " to give context if needed,
        // or just rely on the regex finding the time in the raw text.
        // Let's force a "wake me" prefix to ensure parseAlarm triggers if the user just says "7 am"
        val tempCommand = commandParser.parse("wake me $text") 
        val alarmCmd = tempCommand.find { it.type == CommandType.SET_ALARM_CUSTOM }

        if (alarmCmd != null && !alarmCmd.missingTime) {
            actionExecutor?.execute(alarmCmd)
            isWaitingForAlarmTime = false
            speechAdapter?.isFollowUpListening = false
            hideOverlayWithDelay()
        } else {
            // Try explicit numeric fallback if regex failed? 
            // For now, ask again.
            speechAdapter?.isFollowUpListening = true
            JarvisTTS.speakWithCallback("I didn't catch the time. Please say something like '7 am' or 'in 10 minutes'.") {
                startListening()
            }
        }
    }

    private fun handleNewReminderCommand(command: Command) {
        when {
            command.missingMessage -> {
                Log.d("WakeWordService", "Reminder missing message")
                isWaitingForReminderMessage = true
                pendingTriggerMillis = command.triggerMillis
                pendingFormattedTime = command.formattedTime
                speechAdapter?.isFollowUpListening = true
                JarvisTTS.speakWithCallback("What should I remind you about?") {
                    startListening()
                }
            }
            command.missingTime -> {
                Log.d("WakeWordService", "Reminder missing time")
                isWaitingForReminderTime = true
                pendingReminderMessage = command.messageBody
                speechAdapter?.isFollowUpListening = true
                JarvisTTS.speakWithCallback("When should I remind you?") {
                    startListening()
                }
            }
            command.missingTimeUnit -> {
                Log.d("WakeWordService", "Reminder missing time unit")
                isWaitingForReminderTime = true
                pendingReminderMessage = command.messageBody
                speechAdapter?.isFollowUpListening = true
                JarvisTTS.speakWithCallback("Minutes or hours?") {
                    startListening()
                }
            }
            else -> {
                actionExecutor?.execute(command)
                hideOverlayWithDelay()
            }
        }
    }

    private fun handleReminderTimeFollowUp(text: String) {
        // Attempt to parse just the time from the response
        val tempCommand = commandParser.parse("remind me at $text")
        val reminderCmd = tempCommand.find { it.type == CommandType.SET_REMINDER }
        
        if (reminderCmd != null && !reminderCmd.missingTime) {
            val finalCommand = Command(
                type = CommandType.SET_REMINDER,
                messageBody = pendingReminderMessage,
                triggerMillis = reminderCmd.triggerMillis,
                formattedTime = reminderCmd.formattedTime
            )
            actionExecutor?.execute(finalCommand)
            resetReminderState()
            hideOverlayWithDelay()
        } else {
            speechAdapter?.isFollowUpListening = true
            JarvisTTS.speakWithCallback("I need a time for the reminder.") {
                startListening()
            }
        }
    }

    private fun handleReminderMessageFollowUp(text: String) {
        pendingReminderMessage = text
        Log.d("Reminder", "Stored reminder message: $pendingReminderMessage")
        isWaitingForReminderMessage = false
        
        if (pendingTriggerMillis == null) {
            isWaitingForReminderTime = true
            speechAdapter?.isFollowUpListening = true
            JarvisTTS.speakWithCallback("When should I remind you?") {
                startListening()
            }
        } else {
            val finalCommand = Command(
                type = CommandType.SET_REMINDER,
                messageBody = pendingReminderMessage,
                triggerMillis = pendingTriggerMillis,
                formattedTime = pendingFormattedTime
            )
            actionExecutor?.execute(finalCommand)
            resetReminderState()
            hideOverlayWithDelay()
        }
    }

    private fun resetReminderState() {
        speechAdapter?.isFollowUpListening = false
        isWaitingForReminderTime = false
        isWaitingForReminderMessage = false
        pendingReminderMessage = null
        pendingTriggerMillis = null
        pendingFormattedTime = null
        pendingFormattedTime = null
    }

    private fun handleScheduledCommand(command: Command) {
        val innerCommand = command.scheduledCommand
        val triggerTime = command.triggerAtMillis
        
        if (innerCommand != null && triggerTime != null) {
            val task = com.example.jarvis.scheduler.ScheduledTask(
                id = System.currentTimeMillis(),
                command = innerCommand,
                triggerAtMillis = triggerTime
            )
            com.example.jarvis.scheduler.ScheduledTaskEngine.scheduleTask(this, task)
            
            // Speak confirmation
            val formattedTime = command.formattedTime ?: "later"
            com.example.jarvis.logic.JarvisTTS.speak("I've scheduled that command for $formattedTime")
            hideOverlayWithDelay()
        } else {
             com.example.jarvis.logic.JarvisTTS.speak("I couldn't schedule that command due to missing details.")
             hideOverlayWithDelay()
        }
    }

    private fun callCerebrasAsync(text: String) {
        serviceScope.launch {
            val response = CerebrasClient.askAI(text, useSmartModel = false)
            if (!response.isNullOrEmpty()) {
                Log.d("WakeWordService", "Cerebras response: $response")
                actionExecutor?.execute(Command(CommandType.AI_RESPONSE, aiResponse = response))
            } else {
                actionExecutor?.execute(Command(CommandType.UNKNOWN, aiResponse = "I’m having trouble connecting right now."))
            }
        }
    }

    private fun hideOverlayWithDelay() {
        serviceScope.launch {
            delay(300)
            removeOverlay()
            resumeWakeWord()
        }
    }

    private fun removeOverlay() {
        com.example.jarvis.ui.AssistantUIBridge.closeUI()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun resumeWakeWord() {
        try {
            porcupineManager?.start()
        } catch (e: Exception) {
            Log.e("Jarvis", "Failed to resume porcupine", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        removeOverlay()
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: Exception) {}
        speechAdapter?.destroy()
        actionExecutor?.shutdown()
        serviceScope.cancel()
        try {
            unregisterReceiver(batteryReceiver)
            Log.d("WakeWordService", "BatteryReceiver unregistered")
        } catch (e: Exception) {
            Log.e("WakeWordService", "Failed to unregister BatteryReceiver", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "JarvisServiceChannel")
            .setContentTitle("Jarvis is Listening")
            .setContentText("Say 'Hey Jarvis'...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }
}
