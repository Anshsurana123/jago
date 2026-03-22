package com.example.jarvis.service.speech

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

class VoskAdapter(private val context: Context) : SpeechAdapter {
    
    private var speechService: SpeechService? = null
    private var model: Model? = null
    private var callback: SpeechAdapter.Callback? = null
    override var isFollowUpListening = false

    init {
        initModel()
    }

    private fun initModel() {
        StorageService.unpack(context, "model-en-us", "model",
            { model: Model ->
                this.model = model
            },
            { _ ->
                // Handle error
            }
        )
    }

    override fun startListening(callback: SpeechAdapter.Callback) {
        this.callback = callback
        
        // Cleanup previous session to avoid state leak, UNLESS it's a follow-up
        if (!isFollowUpListening) {
            destroy()
        }

        if (model != null) {
            try {
                val recognizer = Recognizer(model, 16000.0f)
                speechService = SpeechService(recognizer, 16000.0f)
                speechService?.startListening(object : RecognitionListener {
                    override fun onResult(hypothesis: String) {
                        // hypothesis is JSON
                        val text = parseResult(hypothesis)
                        if (text.isNotEmpty()) {
                            callback.onResult(text)
                        } else {
                            callback.onError("No speech detected")
                        }
                        destroy()
                    }

                    override fun onPartialResult(hypothesis: String) {
                        val text = parsePartial(hypothesis)
                        if (text.isNotEmpty()) {
                            callback.onPartialResult(text)
                        }
                    }

                    override fun onFinalResult(hypothesis: String) {
                        val text = parseResult(hypothesis)
                        if (text.isNotEmpty()) {
                            callback.onResult(text)
                        }
                        destroy()
                    }

                    override fun onError(exception: Exception) {
                        callback.onError(exception.message ?: "Vosk Error")
                        destroy()
                    }

                    override fun onTimeout() {
                         callback.onError("Timeout")
                         destroy()
                    }
                })
            } catch (e: Exception) {
                callback.onError(e.message ?: "Failed to start Vosk")
                destroy()
            }
        } else {
            callback.onError("Vosk model not loaded yet")
        }
    }

    private fun parseResult(json: String): String {
        return json.substringAfter("text\" : \"").substringBefore("\"").trim()
    }

    private fun parsePartial(json: String): String {
        return json.substringAfter("partial\" : \"").substringBefore("\"").trim()
    }

    override fun stopListening() {
        if (isFollowUpListening) return
        speechService?.stop()
        speechService = null
    }

    override fun destroy() {
        if (isFollowUpListening) return
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (e: Exception) {}
        speechService = null
    }
}
