package com.example.jago.ui

import android.util.Log

object AssistantUIBridge {
    private const val TAG = "AssistantUIBridge"

    interface AssistantUIListener {
        fun onUpdateStatus(text: String)
        fun onUpdatePartial(text: String)
        fun onClose()
    }

    private var listener: AssistantUIListener? = null
    
    // Maintain state for activity re-creation
    var statusText: String = "Listening..."
        private set
    var partialText: String = ""
        private set
    var isProcessing: Boolean = false
        private set

    fun setListener(newListener: AssistantUIListener) {
        listener = newListener
        // Immediately sync state
        listener?.onUpdateStatus(statusText)
        listener?.onUpdatePartial(partialText)
    }

    fun removeListener() {
        listener = null
    }

    fun updateStatus(text: String) {
        statusText = text
        listener?.onUpdateStatus(text)
    }

    fun updatePartial(text: String) {
        partialText = text
        listener?.onUpdatePartial(text)
    }

    fun closeUI() {
        Log.d(TAG, "Requesting UI closure")
        statusText = "Listening..." // Reset for next time
        partialText = ""
        listener?.onClose()
    }
}
