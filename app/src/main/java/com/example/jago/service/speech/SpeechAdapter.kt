// Required Notice: Copyright 2026 Ansh. (https://github.com/Anshsurana123/jago)
package com.example.jago.service.speech

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
