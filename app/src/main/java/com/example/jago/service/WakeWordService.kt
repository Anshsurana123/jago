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
import com.example.jago.logic.TranslationClient
import com.example.jago.service.speech.AndroidSTTAdapter
import com.example.jago.service.speech.SpeechAdapter
import kotlinx.coroutines.*
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession


class WakeWordService : Service() {

    companion object {
        var isServiceRunning = false
    }

    // 3-model openWakeWord pipeline
    private var melSession: OrtSession? = null
    private var embeddingModel: OrtSession? = null
    private var wakeWordModel: OrtSession? = null
    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    // Pipeline state
    @Volatile private var isDetecting = false
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

    // Notification reading follow-up state
    private var pendingNotifications = listOf<com.example.jago.logic.NotificationStore.NotificationItem>()
    private var currentNotificationIndex = 0
    private var isWaitingForNotificationResponse = false
    
    // Memory for WhatsApp direct reply
    private var isWaitingForWhatsAppMessage = false
    private var pendingWhatsAppContact: String? = null

    // True when Jago is mid-flow and should not auto-close on TTS end
    @Volatile private var isMidFlow = false

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        startForeground(1, createNotification())
        
        actionExecutor = ActionExecutor(this)
        com.example.jago.logic.NotificationStore.init(this)
        speechAdapter = AndroidSTTAdapter(this)
        
        JagoTTS.onSpeechStateChange = { isSpeaking ->
            if (!isSpeaking && !isMidFlow) {
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
            val melBytes = assets.open("melspectrogram.onnx").readBytes()
            melSession = ortEnv.createSession(melBytes, OrtSession.SessionOptions())
    
            // Embedding + wake word → TFLite
            val embBytes = assets.open("embedding_model.onnx").readBytes()
            embeddingSession = ortEnv.createSession(embBytes, OrtSession.SessionOptions())
            val wwBytes = assets.open("jaag_ruut.onnx").readBytes()
            wakeWordSession = ortEnv.createSession(wwBytes, OrtSession.SessionOptions())
    
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

                // STEP B — melspectrogram via ONNX (properly isolated try/finally)
                val melArray: List<FloatArray>?
                val inputTensor = ai.onnxruntime.OnnxTensor.createTensor(
                    ortEnv,
                    java.nio.FloatBuffer.wrap(floatChunk),
                    longArrayOf(1, 1280)
                )
                try {
                    val melResults = melSession?.run(mapOf("input" to inputTensor))
                    melArray = (melResults?.get(0)?.value as? Array<*>)
                        ?.let { it[0] as? Array<*> }
                        ?.let { it[0] as? Array<*> }
                        ?.map { (it as FloatArray) }
                    melResults?.close()
                } catch (e: Exception) {
                    Log.e("Jago", "Mel model error: ${e.message}")
                    inputTensor.close()
                    continue
                }
                inputTensor.close() // always close after try block

                if (melArray == null) continue

                // STEP C — normalize and push mel frames into rolling buffer
                for (melFrame in melArray) {
                    val normalized = FloatArray(melFrame.size) { i -> (melFrame[i] / 10f) + 2f }
                    melFrameBuffer.addLast(normalized)
                }
                while (melFrameBuffer.size > MEL_FRAMES_NEEDED) melFrameBuffer.removeFirst()
                if (melFrameBuffer.size < MEL_FRAMES_NEEDED) continue

                // STEP D — embedding model
                val embInput = Array(1) {
                    Array(MEL_FRAMES_NEEDED) { frameIdx ->
                        Array(32) { binIdx ->
                            FloatArray(1) { melFrameBuffer[frameIdx][binIdx] }
                        }
                    }
               val embFlat = FloatArray(1 * 76 * 32) { i ->
               val frame = i / 32
                 val bin = i % 32
               melFrameBuffer[frame][bin]
                                        }
              val embTensor = ai.onnxruntime.OnnxTensor.createTensor(ortEnv,
                  java.nio.FloatBuffer.wrap(embFlat), longArrayOf(1, 76, 32, 1))
             val embResults = try {
                                   embeddingSession?.run(mapOf("input_1" to embTensor))
                                    } catch (e: Exception) {
            Log.e("Jago", "Embedding model error: ${e.message}")
           embTensor.close()
          continue
}
val embVals = (embResults?.get(0)?.value as? Array<*>)?.get(0) as? FloatArray
embTensor.close()
embResults?.close()
if (embVals == null) continue

                // STEP E — push embedding into rolling buffer
                embeddingBuffer.addLast(embVals)
                while (embeddingBuffer.size > EMBEDDING_FRAMES_NEEDED) embeddingBuffer.removeFirst()
                if (embeddingBuffer.size < EMBEDDING_FRAMES_NEEDED) continue

                // STEP F — wake word model
                val wwFlat = FloatArray(EMBEDDING_FRAMES_NEEDED * 96) { i ->
    embeddingBuffer[i / 96][i % 96]
}
val wwTensor = ai.onnxruntime.OnnxTensor.createTensor(ortEnv,
    java.nio.FloatBuffer.wrap(wwFlat), longArrayOf(1, EMBEDDING_FRAMES_NEEDED.toLong(), 96))
val wwResults = try {
    wakeWordSession?.run(mapOf("input" to wwTensor))
} catch (e: Exception) {
    Log.e("Jago", "Wake word model error: ${e.message}")
    wwTensor.close()
    continue
}
val score = ((wwResults?.get(0)?.value as? Array<*>)?.get(0) as? FloatArray)?.get(0) ?: 0f
wwTensor.close()
wwResults?.close()
                if (score > 0.1f) Log.d("Jago", "Wake word score: $score")

                if (score > 0.5f) {
                    Log.d("Jago", "JAGO DETECTED! Score: $score")
                    isDetecting = false
                    cooldownFrames = 10
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
        // Stop wake word mic capture FIRST before STT grabs it
        isDetecting = false
        audioRecord?.stop()
        
        actionExecutor?.stopSpeaking()
        com.example.jago.ui.AssistantUIBridge.updateStatus("Listening...")
        com.example.jago.ui.AssistantUIBridge.updatePartial("")
        
        val intent = Intent(this, com.example.jago.ui.AssistantActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        
        // Small delay to let AudioRecord fully release the mic
        serviceScope.launch {
            delay(300)
            startListening()
        }
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
        // Translate Hindi/Hinglish to English before parsing
        val translatedText = com.example.jago.logic.HindiTranslator.translate(text)
        if (translatedText != text) {
            Log.d("Jago", "Hindi translated: '$text' \u2192 '$translatedText'")
        }
        
        if (isWaitingForReminderTime) {
            handleReminderTimeFollowUp(translatedText)
            return
        }
        if (isWaitingForReminderMessage) {
            handleReminderMessageFollowUp(translatedText)
            return
        }
        if (isWaitingForAlarmTime) {
            handleAlarmTimeFollowUp(translatedText)
            return
        }
        // Handle notification follow-up response
        if (isWaitingForNotificationResponse) {
            handleNotificationFollowUp(translatedText)
            return
        }
        if (isWaitingForWhatsAppMessage) {
            handleWhatsAppMessageFollowUp(text) // use exact spoken wording
            return
        }

        // Hinglish variations + what en-IN STT actually transcribes
        val hindiTriggers = listOf(
            // Hinglish (user speaks Hindi-style)
            "hindi mai", "hindi mein", "hindi me", "hindi main",
            "hindi mein bhejo", "hindi mai bhejo",
            "hindi mein likho", "hindi mein type karo",
            // English transcription (what STT returns when it hears "hindi mai")
            "in hindi", "send in hindi", "hindi language",
            "translate to hindi", "hindi script", "devanagari"
        )
        val englishTriggers = listOf(
            // Hinglish (user speaks Hindi-style)
            "in english", "english mein", "english mai",
            "english me", "english main", "english mein bhejo",
            "english mai bhejo", "english mein likho",
            // English transcription (what STT returns)
            "send in english", "english language",
            "translate to english", "proper english", "english script"
        )

        val lowerText = translatedText.lowercase()
        val wantsHindi = hindiTriggers.any { lowerText.contains(it.lowercase()) }
        val wantsEnglish = englishTriggers.any { lowerText.contains(it.lowercase()) }

        // Strip trigger from text before parsing so parser doesn't get confused
        var cleanText = translatedText
        if (wantsHindi) {
            hindiTriggers.forEach { trigger ->
                cleanText = cleanText.replace(trigger, "", ignoreCase = true).trim()
            }
        } else if (wantsEnglish) {
            englishTriggers.forEach { trigger ->
                cleanText = cleanText.replace(trigger, "", ignoreCase = true).trim()
            }
        }

        val commands = commandParser.parse(cleanText)
        val validCommands = commands.filter { it.type != CommandType.UNKNOWN }

        if (validCommands.isNotEmpty()) {
            val command = validCommands.first()
            Log.d("WakeWordService", "Executing local command: ${command.type}")

            // If this is a message command AND user wants translation
            if ((command.type == CommandType.SEND_WHATSAPP_MESSAGE) &&
                (wantsHindi || wantsEnglish) &&
                !command.messageBody.isNullOrEmpty()) {

                // Translate async then send
                serviceScope.launch {
                    val originalBody = command.messageBody ?: ""
                    JagoTTS.speak("Translating message...")

                    val translatedBody = if (wantsHindi) {
                        TranslationClient.toDevanagari(originalBody)
                    } else {
                        TranslationClient.toEnglish(originalBody)
                    }

                    if (translatedBody != null) {
                        Log.d("Jago", "Message translated: '$originalBody' \u2192 '$translatedBody'")
                        val translatedCommand = command.copy(messageBody = translatedBody)
                        actionExecutor?.execute(translatedCommand)
                    } else {
                        // Translation failed — send original as fallback
                        JagoTTS.speakWithCallback(
                            "Translation failed. Sending in original language."
                        ) {
                            actionExecutor?.execute(command)
                        }
                    }
                    hideOverlayWithDelay()
                }
                return // Don't fall through to normal execution
            }

            // Normal command execution (no translation needed)
            when (command.type) {
                CommandType.SET_REMINDER -> handleNewReminderCommand(command)
                CommandType.SET_ALARM_CUSTOM, CommandType.SET_ALARM -> handleNewAlarmCommand(command)
                CommandType.SCHEDULED_ACTION -> handleScheduledCommand(command)
                CommandType.READ_NOTIFICATIONS -> {
                    val notifications = com.example.jago.logic.NotificationStore.getAndClear(this)
                    if (notifications.isEmpty()) {
                        JagoTTS.speakBilingual(
                            "No new notifications.",
                            "Koi nayi notification nahi hai."
                        )
                        hideOverlayWithDelay()
                    } else {
                        pendingNotifications = notifications
                        currentNotificationIndex = 0
                        readSingleNotification(notifications[0])
                    }
                }
                CommandType.SET_LANGUAGE -> {
                    val lang = command.messageBody ?: "en"
                    JagoTTS.setLanguage(lang)
                    if (lang == "hi") {
                        JagoTTS.speak("Theek hai, ab main Hindi mein bolunga.")
                    } else {
                        JagoTTS.speak("Okay, I'll speak in English from now on.")
                    }
                    hideOverlayWithDelay()
                }
                CommandType.READ_SCREEN -> {
                    isMidFlow = true
                    hideOverlayWithDelay()
                    serviceScope.launch {
                        kotlinx.coroutines.delay(600) // Wait for overlay to completely close so it doesn't block the screen
                        actionExecutor?.execute(command)
                        isMidFlow = false
                    }
                }
                else -> {
                    validCommands.forEach { cmd ->
                         actionExecutor?.execute(cmd)
                    }
                }
            }
        } else {
            Log.d("WakeWordService", "No local command matched \u2192 Routing to Cerebras AI")
            callCerebrasAsync(cleanText)
        }
    }

    private fun handleNewAlarmCommand(command: Command) {
        if (command.missingTime) {
             isMidFlow = true
             isWaitingForAlarmTime = true
             speechAdapter?.isFollowUpListening = true
             JagoTTS.speakBilingualWithCallback(
                 "When should I set the alarm?",
                 "Alarm kab lagaun?"
             ) {
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
            isMidFlow = false
            speechAdapter?.isFollowUpListening = false
            hideOverlayWithDelay()
        } else {
            speechAdapter?.isFollowUpListening = true
            JagoTTS.speakBilingualWithCallback(
                "I didn't catch the time. Please say something like '7 am'.",
                "Samay samajh nahi aaya. '7 am' jaise bolein."
            ) {
                startListening()
            }
        }
    }

    private fun handleNewReminderCommand(command: Command) {
        when {
            command.missingMessage -> {
                isMidFlow = true
                isWaitingForReminderMessage = true
                pendingTriggerMillis = command.triggerMillis
                pendingFormattedTime = command.formattedTime
                speechAdapter?.isFollowUpListening = true
                JagoTTS.speakBilingualWithCallback(
                    "What should I remind you about?",
                    "Kya yaad dilana hai?"
                ) {
                    startListening()
                }
            }
            command.missingTime -> {
                isMidFlow = true
                isWaitingForReminderTime = true
                pendingReminderMessage = command.messageBody
                speechAdapter?.isFollowUpListening = true
                JagoTTS.speakBilingualWithCallback(
                    "When should I remind you?",
                    "Kab yaad dilana hai?"
                ) {
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
            JagoTTS.speakBilingualWithCallback(
                "I need a time for the reminder.",
                "Samay batao."
            ) {
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
            JagoTTS.speakBilingualWithCallback(
                    "When should I remind you?",
                    "Kab yaad dilana hai?"
                ) {
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
        isMidFlow = false
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

    private fun handleNotificationFollowUp(text: String) {
        val lower = text.lowercase()

        val wantsNext = listOf(
            "agla", "next", "agla padhao", "aage", "agli",
            "next one", "aur padhao", "continue", "aur sunao",
            "haan", "yes", "suno", "padhao", "batao"
        ).any { lower.contains(it) }

        val wantsReply = listOf(
            "jawab", "reply", "jawab dena", "jawab do",
            "respond", "answer", "bhejo", "likho"
        ).any { lower.contains(it) }

        val wantsStop = listOf(
            "band karo", "band", "stop", "bas karo", "bas",
            "rukh ja", "enough", "mat padhao",
            "rehne do", "chodo", "nahi chahiye"
        ).any { lower.contains(it) }

        when {
            wantsStop -> {
                isWaitingForNotificationResponse = false
                isMidFlow = false
                speechAdapter?.isFollowUpListening = false
                pendingNotifications = emptyList()
                currentNotificationIndex = 0
                JagoTTS.speakBilingual("Okay, stopping.", "Theek hai.")
                hideOverlayWithDelay()
            }
            wantsReply -> {
                isWaitingForNotificationResponse = false
                val current = pendingNotifications.getOrNull(currentNotificationIndex)

                if (current?.sender != null) {
                    // Check if user already said the message in the same breath
                    // e.g. "reply main aa raha hoon" or "jawab main aa raha hoon"
                    var inlineMessage = lower
                    listOf("jawab", "reply", "jawab dena", "jawab do",
                        "respond", "answer", "bhejo", "likho").forEach { word ->
                        inlineMessage = inlineMessage.replace(word, "").trim()
                    }
                    inlineMessage = inlineMessage.trim()

                    if (inlineMessage.isNotEmpty() && inlineMessage.length > 2) {
                        // User said message inline — send directly without asking
                        Log.d("WakeWordService", "Inline reply detected: $inlineMessage")
                        isMidFlow = false
                        speechAdapter?.isFollowUpListening = false
                        val finalCommand = com.example.jago.logic.Command(
                            type = com.example.jago.logic.CommandType.SEND_WHATSAPP_MESSAGE,
                            contactName = current.sender,
                            messageBody = inlineMessage
                        )
                        JagoTTS.speakBilingual(
                            "Sending to ${current.sender}",
                            "${current.sender} ko bhej raha hoon"
                        )
                        actionExecutor?.execute(finalCommand)
                        hideOverlayWithDelay()
                    } else {
                        // No inline message — ask what to say
                        isMidFlow = true
                        isWaitingForWhatsAppMessage = true
                        pendingWhatsAppContact = current.sender
                        speechAdapter?.isFollowUpListening = true
                        JagoTTS.speakBilingualWithCallback(
                            "What should I say to ${current.sender}?",
                            "${current.sender} ko kya jawab dun?"
                        ) {
                            startListening()
                        }
                    }
                } else {
                    // No sender — open the app so user can reply manually
                    isMidFlow = false
                    speechAdapter?.isFollowUpListening = false
                    val appName = current?.appName ?: "the app"
                    JagoTTS.speakBilingual(
                        "Opening $appName for you to reply.",
                        "$appName khol raha hoon jawab dene ke liye."
                    )
                    current?.appName?.let { app ->
                        actionExecutor?.execute(
                            com.example.jago.logic.Command(
                                com.example.jago.logic.CommandType.OPEN_APP,
                                contactName = app
                            )
                        )
                    }
                    hideOverlayWithDelay()
                }
            }
            wantsNext -> {
                currentNotificationIndex++
                if (currentNotificationIndex < pendingNotifications.size) {
                    readSingleNotification(pendingNotifications[currentNotificationIndex])
                } else {
                    isWaitingForNotificationResponse = false
                    isMidFlow = false
                    speechAdapter?.isFollowUpListening = false
                    pendingNotifications = emptyList()
                    currentNotificationIndex = 0
                    JagoTTS.speakBilingual(
                        "No more notifications.",
                        "Aur koi notification nahi hai."
                    )
                    hideOverlayWithDelay()
                }
            }
            else -> {
                // Didn't understand — ask again
                JagoTTS.speakBilingualWithCallback(
                    "Say 'next' for next, 'reply' to reply, or 'stop' to stop.",
                    "Agla sunne ke liye 'agla' bolo, jawab ke liye 'jawab', band ke liye 'band'."
                ) {
                    speechAdapter?.isFollowUpListening = true
                    startListening()
                }
            }
        }
    }

    private fun handleWhatsAppMessageFollowUp(text: String) {
        val contact = pendingWhatsAppContact
        isWaitingForWhatsAppMessage = false
        pendingWhatsAppContact = null
        speechAdapter?.isFollowUpListening = false

        if (contact == null) {
            isMidFlow = false
            hideOverlayWithDelay()
            return
        }

        val msgBody = text.trim()

        // Detect if user specified language in the reply itself
        val lowerMsg = msgBody.lowercase()
        val wantsHindi = listOf("in hindi", "hindi mein", "hindi mai", "hindi me")
            .any { lowerMsg.contains(it) }
        val wantsEnglish = listOf("in english", "english mein", "english mai")
            .any { lowerMsg.contains(it) }

        // Strip language trigger from body
        var cleanBody = msgBody
        if (wantsHindi) {
            listOf("in hindi", "hindi mein", "hindi mai", "hindi me")
                .forEach { cleanBody = cleanBody.replace(it, "", ignoreCase = true).trim() }
        } else if (wantsEnglish) {
            listOf("in english", "english mein", "english mai")
                .forEach { cleanBody = cleanBody.replace(it, "", ignoreCase = true).trim() }
        }

        // Determine if translation needed:
        // Either user explicitly said "in hindi" OR global language is set to Hindi
        val shouldTranslateToHindi = wantsHindi ||
            (!wantsEnglish && JagoTTS.currentLanguage == "hi")
        val shouldTranslateToEnglish = wantsEnglish

        if (shouldTranslateToHindi || shouldTranslateToEnglish) {
            // Translate async then send
            serviceScope.launch {
                JagoTTS.speakBilingual("Translating...", "Translate kar raha hoon...")
                val translatedBody = if (shouldTranslateToHindi) {
                    TranslationClient.toDevanagari(cleanBody)
                } else {
                    TranslationClient.toEnglish(cleanBody)
                }
                val finalBody = translatedBody ?: cleanBody // fallback to original
                val finalCommand = com.example.jago.logic.Command(
                    type = com.example.jago.logic.CommandType.SEND_WHATSAPP_MESSAGE,
                    contactName = contact,
                    messageBody = finalBody
                )
                isMidFlow = false
                actionExecutor?.execute(finalCommand)
                hideOverlayWithDelay()
            }
        } else {
            // No translation needed — send as-is
            isMidFlow = false
            val finalCommand = com.example.jago.logic.Command(
                type = com.example.jago.logic.CommandType.SEND_WHATSAPP_MESSAGE,
                contactName = contact,
                messageBody = cleanBody
            )
            actionExecutor?.execute(finalCommand)
        }
    }

    private fun readSingleNotification(
        item: com.example.jago.logic.NotificationStore.NotificationItem
    ) {
        isMidFlow = true
        val remaining = pendingNotifications.size - currentNotificationIndex - 1

        // Build summary separately from content
        val summary = if (item.sender != null) {
            if (JagoTTS.currentLanguage == "hi")
                "${item.appName} par ${item.sender} ka message"
            else
                "${item.appName} message from ${item.sender}"
        } else {
            item.appName
        }

        val followUp = if (remaining > 0) {
            if (JagoTTS.currentLanguage == "hi")
                "$remaining aur hain. Agla padho ki jawab dena hai? Band karne ke liye 'band' bolo."
            else
                "$remaining more. Say 'next' for next, 'reply' to reply, or 'stop' to stop."
        } else {
            if (JagoTTS.currentLanguage == "hi")
                "Yahi tha. Jawab dena hai? Ya band karo."
            else
                "That's all. Say 'reply' to reply or 'stop' to stop."
        }

        // Chain: speak summary → speak content → speak followUp → listen
        // Each step waits for previous to fully complete
        JagoTTS.speakWithCallback(summary) {
            JagoTTS.speakWithCallback(item.content) {
                JagoTTS.speakWithCallback(followUp) {
                    isWaitingForNotificationResponse = true
                    speechAdapter?.isFollowUpListening = true
                    startListening()

                    // Auto-reset after 30 seconds if user doesn't respond
                    // Prevents state getting permanently stuck
                    serviceScope.launch {
                        delay(30000)
                        if (isWaitingForNotificationResponse) {
                            Log.w("Jago", "Notification follow-up timed out — resetting state")
                            isWaitingForNotificationResponse = false
                            isMidFlow = false
                            speechAdapter?.isFollowUpListening = false
                            pendingNotifications = emptyList()
                            currentNotificationIndex = 0
                        }
                    }
                }
            }
        }
    }

    private fun callCerebrasAsync(text: String) {
        serviceScope.launch {
            // Only speak "Let me think..." if response takes longer than 800ms
            var responded = false
            val thinkingJob = serviceScope.launch {
                kotlinx.coroutines.delay(800)
                if (!responded) {
                    JagoTTS.speakBilingual("Let me think...", "Soch raha hoon...")
                }
            }
            
            val response = com.example.jago.logic.CerebrasClient.askAI(text, useSmartModel = false)
            responded = true
            thinkingJob.cancel()
            
            if (!response.isNullOrEmpty()) {
                actionExecutor?.execute(Command(CommandType.AI_RESPONSE, aiResponse = response))
            } else {
                // Cerebras timed out or failed — tell the user clearly
                JagoTTS.speakBilingual(
                    "Sorry, I couldn't connect right now. Please try again.",
                    "Maaf karein, abhi connect nahi ho saka. Dobara try karein."
                )
                hideOverlayWithDelay()
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
            try {
                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord?.startRecording()
                    isDetecting = true
                    Log.d("Jago", "Wake word detection resumed")
                } else {
                    // AudioRecord is in bad state — stop old thread first,
                    // then restart cleanly to avoid double-thread bug
                    Log.w("Jago", "AudioRecord in bad state, restarting capture cleanly")
                    isDetecting = false
                    audioThread?.interrupt()
                    audioThread?.join(500) // wait max 500ms for old thread to die
                    audioThread = null
                    audioRecord?.stop()
                    audioRecord?.release()
                    audioRecord = null
                    startAudioCapture()
                    isDetecting = true
                }
            } catch (e: Exception) {
                Log.e("Jago", "Failed to resume wake word", e)
            }
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
        embeddingSession?.close()
        wakeWordSession?.close()
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
