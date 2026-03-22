package com.example.jarvis.logic

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

object JarvisTTS : TextToSpeech.OnInitListener {
    private const val TAG = "JarvisTTS"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val handler = Handler(Looper.getMainLooper())
    
    // Explicit state tracking
    var isSpeaking = false
        private set
    private var activeUtteranceId: String? = null
    
    // Callback management
    private var pendingCallback: (() -> Unit)? = null
    var onSpeechStateChange: ((Boolean) -> Unit)? = null

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started: $utteranceId")
                    isSpeaking = true
                    handler.post { onSpeechStateChange?.invoke(true) }
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS finished: $utteranceId")
                    isSpeaking = false
                    handler.post { onSpeechStateChange?.invoke(false) }
                    
                    // Only execute callback if it matches the active utterance
                    if (utteranceId == activeUtteranceId) {
                        activeUtteranceId = null
                        // Post delayed callback to ensure TTS audio clears (500ms buffer)
                        handler.postDelayed({
                            Log.d(TAG, "TTS finished → Starting follow-up listening")
                            pendingCallback?.invoke()
                            pendingCallback = null
                        }, 500)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error: $utteranceId")
                    isSpeaking = false
                    activeUtteranceId = null
                    pendingCallback = null
                    handler.post { onSpeechStateChange?.invoke(false) }
                }
            })
            isInitialized = true
        } else {
            Log.e(TAG, "Initialization failed")
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            val utteranceId = "JARVIS_${System.currentTimeMillis()}"
            activeUtteranceId = utteranceId
            
            // Use QUEUE_ADD if already speaking to prevent cut-offs
            val queueMode = if (isSpeaking) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
            
            tts?.speak(text, queueMode, null, utteranceId)
        } else {
            Log.w(TAG, "TTS not initialized yet")
        }
    }

    fun speakWithCallback(text: String, onComplete: () -> Unit) {
        if (isInitialized) {
            pendingCallback = onComplete
            val utteranceId = "JARVIS_${System.currentTimeMillis()}"
            activeUtteranceId = utteranceId
            
            // Use QUEUE_ADD if already speaking to prevent cut-offs
            val queueMode = if (isSpeaking) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
            
            tts?.speak(text, queueMode, null, utteranceId)
            Log.d(TAG, "Speaking with callback: $text (ID: $utteranceId)")
        } else {
            Log.w(TAG, "TTS not initialized yet, invoking callback immediately")
            onComplete()
        }
    }

    fun stopSpeaking() {
        if (isInitialized && isSpeaking) {
            Log.d(TAG, "Speech interrupted by user")
            tts?.stop()
            isSpeaking = false
            activeUtteranceId = null
            pendingCallback = null
            handler.removeCallbacksAndMessages(null)
            handler.post { onSpeechStateChange?.invoke(false) }
        }
    }

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        pendingCallback = null
        activeUtteranceId = null
        isSpeaking = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
