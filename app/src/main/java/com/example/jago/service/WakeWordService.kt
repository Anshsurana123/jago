package com.example.jago.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.jago.MainActivity
import com.example.jago.R
import com.example.jago.logic.ActionExecutor
import com.example.jago.logic.Command
import com.example.jago.logic.CommandParser
import com.example.jago.logic.CommandType
import com.example.jago.logic.JagoTTS
import com.example.jago.service.speech.AndroidSTTAdapter
import com.example.jago.service.speech.SpeechAdapter
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

class WakeWordService : Service() {

    companion object {
        var isServiceRunning = false
    }

    private var tflite: Interpreter? = null
    private var isDetecting = false
    private var audioBuffer = ShortArray(1280) // will be dynamically resized
    
    private var speechAdapter: SpeechAdapter? = null
    private var actionExecutor: ActionExecutor? = null
    private val commandParser = CommandParser()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Battery Receiver
    private val batteryReceiver = com.example.jago.logic.BatteryReceiver()

    // Follow-up state
    private var isWaitingForReminderTime = false
    private var isWaitingForReminderMessage = false
    private var pendingReminderMessage: String? = null
    private var pendingTriggerMillis: Long? = null
    private var pendingFormattedTime: String? = null
    private var isWaitingForAlarmTime = false

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        startForeground(1, createNotification())
        
        actionExecutor = ActionExecutor(this)
        speechAdapter = AndroidSTTAdapter(this)
        
        JagoTTS.onSpeechStateChange = { isSpeaking ->
            if (!isSpeaking) {
                 hideOverlayWithDelay()
            }
        }
        
        registerReceiver(batteryReceiver, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        Log.d("WakeWordService", "BatteryReceiver registered")
        
        initWakeWord()
    }

    private fun initWakeWord() {
        try {
            val model = FileUtil.loadMappedFile(this, "jagoo.tflite")
            tflite = Interpreter(model)
            
            val numElements = tflite!!.getInputTensor(0).shape().fold(1) { acc, i -> acc * i }
            if (numElements > 0) {
                audioBuffer = ShortArray(numElements)
            }
            
            isDetecting = true
            startAudioCapture()
            Log.d("Jago", "TFLite WakeWord initialized with buffer size $numElements")
        } catch (e: Exception) {
            Log.e("Jago", "Failed to init TFLite model", e)
        }
    }

    private fun startAudioCapture() {
        val sampleRate = 16000
        val chunkSize = 1280  // 80ms per chunk
        val windowSize = audioBuffer.size  // full model input size
        
        // How many chunks to wait between inferences (~every 400ms)
        val inferenceEveryNChunks = 5
        var chunkCount = 0

        // Cooldown after detection to prevent double triggers
        var cooldownFrames = 0
        val cooldownAfterDetection = 20 // ~1.6 seconds

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(chunkSize * 2, minBufferSize)
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("Jago", "AudioRecord init failed")
                return
            }

            recorder.startRecording()

            Thread {
                val chunkBuffer = ShortArray(chunkSize)

                while (isServiceRunning) {
                    val read = recorder.read(chunkBuffer, 0, chunkSize)
                    if (read <= 0 || !isDetecting) continue

                    // Slide window
                    System.arraycopy(audioBuffer, read, audioBuffer, 0, windowSize - read)
                    System.arraycopy(chunkBuffer, 0, audioBuffer, windowSize - read, read)

                    chunkCount++

                    // Cooldown — skip inference right after a detection
                    if (cooldownFrames > 0) {
                        cooldownFrames--
                        continue
                    }

                    // Only run inference every 5 chunks (~400ms)
                    if (chunkCount % inferenceEveryNChunks != 0) continue

                    val scores = runInference(audioBuffer)
                    if (scores.isEmpty()) continue

                    val score = if (scores.size > 1) scores[1] else scores[0]

                    if (score > 0.2f) {
                        Log.d("Jago", "Score: $score")
                    }

                    if (score > 0.85f) {
                        Log.d("Jago", "Wake word DETECTED! Score: $score")
                        isDetecting = false
                        cooldownFrames = cooldownAfterDetection
                        audioBuffer.fill(0)
                        Handler(Looper.getMainLooper()).post { showOverlay() }
                    }
                }

                recorder.stop()
                recorder.release()
            }.start()
        } catch (e: SecurityException) {
            Log.e("Jago", "Missing MICROPHONE permission", e)
            Handler(Looper.getMainLooper()).post {
                JagoTTS.speak("I need microphone permission to listen for the wake word.")
            }
        } catch (e: Exception) {
            Log.e("Jago", "Audio capture error", e)
        }
    }

    private fun runInference(audio: ShortArray): FloatArray {
        val inputBuffer = java.nio.ByteBuffer.allocateDirect(audio.size * 4)
        inputBuffer.order(java.nio.ByteOrder.nativeOrder())
        val floatInput = inputBuffer.asFloatBuffer()
        for (i in audio.indices) {
            floatInput.put(audio[i] / 32768f)
        }
        inputBuffer.rewind()
        
        val outputTensor = tflite?.getOutputTensor(0) ?: return FloatArray(0)
        val outShape = outputTensor.shape()
        val numElements = outShape.fold(1) { acc, i -> acc * i }.coerceAtLeast(1)
        
        val outputBuffer = java.nio.ByteBuffer.allocateDirect(numElements * 4)
        outputBuffer.order(java.nio.ByteOrder.nativeOrder())
        
        try {
            tflite?.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()
            
            val floatOutput = outputBuffer.asFloatBuffer()
            val result = FloatArray(numElements)
            for (i in 0 until numElements) {
                result[i] = floatOutput.get(i)
            }
            return result
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown"
            Log.e("Jago", "Inference crashed: $msg", e)
            isDetecting = false
            Handler(Looper.getMainLooper()).post {
                val shapeErr = if (msg.contains("shape") || msg.contains("size")) "shape mismatch" else "type mismatch"
                JagoTTS.speak("Inference error due to $shapeErr")
            }
        }
        return FloatArray(0)
    }

    private fun showOverlay() {
        actionExecutor?.stopSpeaking()
        com.example.jago.ui.AssistantUIBridge.updateStatus("Listening...")
        com.example.jago.ui.AssistantUIBridge.updatePartial("")
        
        val intent = Intent(this, com.example.jago.ui.AssistantActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        
        startListening()
    }

    private fun startListening() {
        com.example.jago.ui.AssistantUIBridge.updateStatus("Listening...")
        speechAdapter?.startListening(object : SpeechAdapter.Callback {
            override fun onResult(text: String) {
                Log.d("Jago", "Speech result: $text")
                com.example.jago.ui.AssistantUIBridge.updateStatus("Processing...")
                processCommand(text)
            }

            override fun onError(error: String) {
                Log.e("Jago", "Speech error: $error")
                com.example.jago.ui.AssistantUIBridge.updateStatus("Error: $error")
                hideOverlayWithDelay()
            }

            override fun onPartialResult(text: String) {
                com.example.jago.ui.AssistantUIBridge.updatePartial(text)
            }
        })
    }

    private fun processCommand(text: String) {
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
             isWaitingForAlarmTime = true
             speechAdapter?.isFollowUpListening = true
             JagoTTS.speakWithCallback("What time should I set the alarm?") {
                 startListening()
             }
        } else {
            actionExecutor?.execute(command)
            hideOverlayWithDelay()
        }
    }

    private fun handleAlarmTimeFollowUp(text: String) {
        val tempCommand = commandParser.parse("wake me $text") 
        val alarmCmd = tempCommand.find { it.type == CommandType.SET_ALARM_CUSTOM }

        if (alarmCmd != null && !alarmCmd.missingTime) {
            actionExecutor?.execute(alarmCmd)
            isWaitingForAlarmTime = false
            speechAdapter?.isFollowUpListening = false
            hideOverlayWithDelay()
        } else {
            speechAdapter?.isFollowUpListening = true
            JagoTTS.speakWithCallback("I didn't catch the time. Please say something like '7 am'.") {
                startListening()
            }
        }
    }

    private fun handleNewReminderCommand(command: Command) {
        when {
            command.missingMessage -> {
                isWaitingForReminderMessage = true
                pendingTriggerMillis = command.triggerMillis
                pendingFormattedTime = command.formattedTime
                speechAdapter?.isFollowUpListening = true
                JagoTTS.speakWithCallback("What should I remind you about?") {
                    startListening()
                }
            }
            command.missingTime -> {
                isWaitingForReminderTime = true
                pendingReminderMessage = command.messageBody
                speechAdapter?.isFollowUpListening = true
                JagoTTS.speakWithCallback("When should I remind you?") {
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
            JagoTTS.speakWithCallback("I need a time for the reminder.") {
                startListening()
            }
        }
    }

    private fun handleReminderMessageFollowUp(text: String) {
        pendingReminderMessage = text
        isWaitingForReminderMessage = false
        
        if (pendingTriggerMillis == null) {
            isWaitingForReminderTime = true
            speechAdapter?.isFollowUpListening = true
            JagoTTS.speakWithCallback("When should I remind you?") {
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
    }

    private fun handleScheduledCommand(command: Command) {
        val innerCommand = command.scheduledCommand
        val triggerTime = command.triggerAtMillis
        
        if (innerCommand != null && triggerTime != null) {
            val task = com.example.jago.scheduler.ScheduledTask(
                id = System.currentTimeMillis(),
                command = innerCommand,
                triggerAtMillis = triggerTime
            )
            com.example.jago.scheduler.ScheduledTaskEngine.scheduleTask(this, task)
            val formattedTime = command.formattedTime ?: "later"
            JagoTTS.speak("I've scheduled that command for $formattedTime")
            hideOverlayWithDelay()
        } else {
             JagoTTS.speak("I couldn't schedule that command.")
             hideOverlayWithDelay()
        }
    }

    private fun callCerebrasAsync(text: String) {
        serviceScope.launch {
            val response = com.example.jago.logic.CerebrasClient.askAI(text, useSmartModel = false)
            if (!response.isNullOrEmpty()) {
                actionExecutor?.execute(Command(CommandType.AI_RESPONSE, aiResponse = response))
            } else {
                actionExecutor?.execute(Command(CommandType.UNKNOWN, aiResponse = "I’m having trouble connecting."))
            }
        }
    }

    private fun hideOverlayWithDelay() {
        serviceScope.launch {
            delay(300)
            com.example.jago.ui.AssistantUIBridge.closeUI()
            resumeWakeWord()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun resumeWakeWord() {
        serviceScope.launch {
            delay(1500) // wait 1.5s before listening again
            audioBuffer.fill(0)
            isDetecting = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isDetecting = false
        tflite?.close()
        speechAdapter?.destroy()
        actionExecutor?.shutdown()
        serviceScope.cancel()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "JagoServiceChannel")
            .setContentTitle("Jago is Listening")
            .setContentText("Say 'Jago'...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }
}
