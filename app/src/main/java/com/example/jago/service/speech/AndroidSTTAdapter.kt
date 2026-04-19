// Required Notice: Copyright 2026 Ansh. (https://github.com/Anshsurana123/jago)
package com.example.jago.service.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class AndroidSTTAdapter(private val context: Context) : SpeechAdapter {

    private var retryCount = 0
    private val maxRetries = 2
    private var speechRecognizer: SpeechRecognizer? = null
    private var callback: SpeechAdapter.Callback? = null
    private var isListening = false
    override var isFollowUpListening = false

    override fun startListening(callback: SpeechAdapter.Callback) {
        this.callback = callback
        
        Log.d("AndroidSTT", "Starting listening (follow-up: $isFollowUpListening)...")
        
        // Always destroy previous instance to avoid state lock
        // For follow-ups, we still need to clean up the old recognizer
        // but we do it forcefully (bypassing the isFollowUpListening guard)
        forceDestroyRecognizer()
        
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(createListener())
                
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3) // get top 3 results for better Hindi matching
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN") // English India as primary
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-US"))
                }
                
                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d("AndroidSTT", "SpeechRecognizer started")
            } catch (e: Exception) {
                Log.e("AndroidSTT", "Failed to start recognition", e)
                callback.onError("Failed to start speech recognition")
                destroy()
            }
        } else {
            Log.e("AndroidSTT", "Speech recognition not available")
            callback.onError("Speech recognition not available on this device")
        }
    }

    override fun stopListening() {
        if (isFollowUpListening) {
            Log.d("AndroidSTT", "stopListening skipped: isFollowUpListening is true")
            return
        }
        if (isListening) {
            Log.d("AndroidSTT", "Stopping listening")
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e("AndroidSTT", "Error stopping listening", e)
            }
            isListening = false
        }
    }

    override fun destroy() {
        if (isFollowUpListening) {
            Log.d("AndroidSTT", "destroy skipped: isFollowUpListening is true")
            return
        }
        forceDestroyRecognizer()
    }

    private fun forceDestroyRecognizer() {
        Log.d("AndroidSTT", "Force destroying SpeechRecognizer")
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("AndroidSTT", "Error destroying recognizer", e)
        }
        speechRecognizer = null
        isListening = false
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("AndroidSTT", "onReadyForSpeech")
        }
        
        override fun onBeginningOfSpeech() {
            Log.d("AndroidSTT", "onBeginningOfSpeech")
        }
        
        override fun onRmsChanged(rmsdB: Float) {}
        
        override fun onBufferReceived(buffer: ByteArray?) {}
        
        override fun onEndOfSpeech() {
            Log.d("AndroidSTT", "onEndOfSpeech")
            isListening = false
        }

        override fun onError(error: Int) {
            val errorMessage = getErrorText(error)
            Log.e("AndroidSTT", "onError: $errorMessage ($error)")
            isListening = false

            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && retryCount < maxRetries) {
                // Recognizer was busy — destroy and retry after short delay
                retryCount++
                Log.d("AndroidSTT", "Recognizer busy, retrying ($retryCount/$maxRetries)...")
                destroy()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    callback?.let { startListening(it) }
                }, 600)
                return
            }

            // For all other errors (or if retries exhausted), reset and notify
            retryCount = 0
            // Important: Notify callback so service can resume wake word
            callback?.onError("Speech Error: $errorMessage")
            forceDestroyRecognizer() // Safely clean up (bypass follow-up guard)
        }

        override fun onResults(results: Bundle?) {
            retryCount = 0  // reset retries on success
            Log.d("AndroidSTT", "onResults")
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                // Try each result — use first one that isn't empty
                val text = matches.firstOrNull { it.isNotBlank() } ?: matches[0]
                Log.d("AndroidSTT", "Recognized: $text (from ${matches.size} options)")
                callback?.onResult(text)
            } else {
                callback?.onError("No speech detected")
            }
            forceDestroyRecognizer() // Cleanup after success (bypass follow-up guard)
        }

        override fun onPartialResults(partialResults: Bundle?) {
             val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                callback?.onPartialResult(matches[0])
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
    }
}
