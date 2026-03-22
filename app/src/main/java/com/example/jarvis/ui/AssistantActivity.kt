package com.example.jarvis.ui

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.jarvis.R
import com.example.jarvis.logic.JarvisTTS

class AssistantActivity : AppCompatActivity(), AssistantUIBridge.AssistantUIListener {

    private lateinit var statusText: TextView
    private lateinit var partialText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.assistant_overlay) // Reusing the overlay layout

        statusText = findViewById(R.id.overlayStatusText)
        partialText = findViewById(R.id.overlayPartialText)
        progressBar = findViewById(R.id.overlayProgressBar)

        findViewById<android.view.View>(R.id.btnSchedule).setOnClickListener {
             val intent = android.content.Intent(this, com.example.jarvis.ui.ScheduledTasksActivity::class.java).apply {
                 addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
             }
             startActivity(intent)
             finish()
        }

        // Register with Bridge
        AssistantUIBridge.setListener(this)

        // Handle Back Press
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (JarvisTTS.isSpeaking) {
                    JarvisTTS.stopSpeaking()
                }
                finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Ensure listener is active if activity was paused
        AssistantUIBridge.setListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        AssistantUIBridge.removeListener()
    }

    // Bridge Callbacks
    override fun onUpdateStatus(text: String) {
        runOnUiThread {
            statusText.text = text
        }
    }

    override fun onUpdatePartial(text: String) {
        runOnUiThread {
            partialText.text = text
        }
    }

    override fun onClose() {
        runOnUiThread {
            finish()
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                 overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                 @Suppress("DEPRECATION")
                 overridePendingTransition(0, 0)
            }
        }
    }
}
