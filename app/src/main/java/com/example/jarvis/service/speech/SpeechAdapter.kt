package com.example.jarvis.service.speech

interface SpeechAdapter {
    interface Callback {
        fun onResult(text: String)
        fun onError(error: String)
        fun onPartialResult(text: String)
    }

    fun startListening(callback: Callback)
    fun stopListening()
    fun destroy()
    var isFollowUpListening: Boolean
}
