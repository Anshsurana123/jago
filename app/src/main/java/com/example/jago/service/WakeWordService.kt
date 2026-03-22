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
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

class WakeWordService : Service() {

    companion object {
        var isServiceRunning = false
    }

    // 3-model openWakeWord pipeline
    private var melSession: OrtSession? = null  // replace melModel
    private var embeddingModel: Interpreter? = null
    private var wakeWordModel: Interpreter? = null

    // Pipeline state
    private var isDetecting = false
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null

    // Rolling buffers
    private val melFrameBuffer = ArrayDeque<FloatArray>()
    private val embeddingBuffer = ArrayDeque<FloatArray>()

    // Pipeline constants
    private val CHUNK_SIZE = 1280          // 80ms at 16kHz
    private val MEL_FRAMES_NEEDED = 76     // mel frames per embedding
    private val EMBEDDING_FRAMES_NEEDED = 16 // embeddings per wake word decision
    
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
            // Mel → ONNX (this is what openWakeWord actually uses)
            val env = OrtEnvironment.getEnvironment()
            val melBytes = assets.open("melspectrogram.onnx").readBytes()
            melSession = env.createSession(melBytes, OrtSession.SessionOptions())
    
            // Embedding + wake word → TFLite
            embeddingModel = Interpreter(FileUtil.loadMappedFile(this, "embedding_model.tflite"))
            wakeWordModel = Interpreter(FileUtil.loadMappedFile(this, "jagoo.tflite"))
    
            isDetecting = true
            startAudioCapture()
            Log.d("Jago", "openWakeWord pipeline initialized ✓")
        } catch (e: Exception) {
            Log.e("Jago", "Failed to init wake word models", e)
        }
    }

    private fun startAudioCapture() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(CHUNK_SIZE * 2, minBufferSize)
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("Jago", "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
        } catch (e: SecurityException) {
            Log.e("Jago", "Missing MICROPHONE permission", e)
            Handler(Looper.getMainLooper()).post {
                JagoTTS.speak("I need microphone permission to listen for the wake word.")
            }
            return
        }

        audioThread = Thread {
            val chunkBuffer = ShortArray(CHUNK_SIZE)
            var cooldownFrames = 0

            while (isServiceRunning) {
                val read = audioRecord?.read(chunkBuffer, 0, CHUNK_SIZE) ?: break
                if (read <= 0 || !isDetecting) continue

                if (cooldownFrames > 0) {
                    cooldownFrames--
                    continue
                }

                // STEP A — convert raw PCM to float [-1, 1]
                val floatChunk = FloatArray(CHUNK_SIZE) { chunkBuffer[it] / 32768f }

                // STEP B — melspectrogram via ONNX
                val env = OrtEnvironment.getEnvironment()
                val inputTensor = ai.onnxruntime.OnnxTensor.createTensor(
                    env,
                    java.nio.FloatBuffer.wrap(floatChunk),
                    longArrayOf(1, 1280)
                )
                val melResults = melSession?.run(mapOf("input" to inputTensor))
                val melArray = (melResults?.get(0)?.value as? Array<*>)
                    ?.let { it[0] as? Array<*> }
                    ?.let { it[0] as? Array<*> }
                    ?.map { (it as FloatArray) }
                
                melResults?.close()
                inputTensor.close()
                
                if (melArray == null) continue

                // STEP C — normalize and push frames
                for (melFrame in melArray) {
                    val normalized = FloatArray(melFrame.size) { i -> (melFrame[i] / 10f) + 2f }
                    melFrameBuffer.addLast(normalized)
                }
                while (melFrameBuffer.size > MEL_FRAMES_NEEDED) {
                    melFrameBuffer.removeFirst()
                }

                // Need 76 mel frames before we can run embedding model
                if (melFrameBuffer.size < MEL_FRAMES_NEEDED) continue

                // STEP D — embedding model
                // Input:  [1, 76, 32, 1]
                // Output: [1, 1, 1, 96] — single 96-dim embedding vector
                val embInput = Array(1) {
                    Array(MEL_FRAMES_NEEDED) { frameIdx ->
                        Array(32) { binIdx ->
                            FloatArray(1) { melFrameBuffer[frameIdx][binIdx] }
                        }
                    }
                }
                val embOutput = Array(1) { Array(1) { Array(1) { FloatArray(96) } } }
                try {
                    embeddingModel?.run(embInput, embOutput)
                } catch (e: Exception) {
                    Log.e("Jago", "Embedding model error: ${e.message}")
                    continue
                }

                // STEP E — push embedding into rolling buffer
                embeddingBuffer.addLast(embOutput[0][0][0])
                while (embeddingBuffer.size > EMBEDDING_FRAMES_NEEDED) {
                    embeddingBuffer.removeFirst()
                }

                // Need 16 embeddings before we can score wake word
                if (embeddingBuffer.size < EMBEDDING_FRAMES_NEEDED) continue

                // STEP F — wake word model
                // Input:  [1, 16, 96]
                // Output: [1, 1] — score 0.0 to 1.0
                val wwInput = Array(1) {
                    Array(EMBEDDING_FRAMES_NEEDED) { i -> embeddingBuffer[i] }
                }
                val wwOutput = Array(1) { FloatArray(1) }
                try {
                    wakeWordModel?.run(wwInput, wwOutput)
                } catch (e: Exception) {
                    Log.e("Jago", "Wake word model error: ${e.message}")
                    continue
                }

                val score = wwOutput[0][0]
                if (score > 0.1f) Log.d("Jago", "Wake word score: $score")

                if (score > 0.5f) {
                    Log.d("Jago", "JAGO DETECTED! Score: $score")
                    isDetecting = false
                    cooldownFrames = 30
                    melFrameBuffer.clear()
                    embeddingBuffer.clear()
                    Handler(Looper.getMainLooper()).post { showOverlay() }
                }
            }

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }

        audioThread?.start()
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
            delay(1500)
            melFrameBuffer.clear()
            embeddingBuffer.clear()
            isDetecting = true
            Log.d("Jago", "Wake word detection resumed")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isDetecting = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioThread?.interrupt()
        melSession?.close()
        embeddingModel?.close()
        wakeWordModel?.close()
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
