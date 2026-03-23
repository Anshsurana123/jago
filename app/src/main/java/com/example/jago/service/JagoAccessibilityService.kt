package com.example.jago.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo

class JagoAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: JagoAccessibilityService? = null
        private var targetApp: String? = null
        private var targetContact: String? = null
        private var pendingAutoSend: Boolean = false
        private var isAutoSending: Boolean = false
        private var automationActive: Boolean = false
        
        // Notification storage — stores recent notifications for reading
        private val recentNotifications = mutableListOf<String>()
        private const val MAX_NOTIFICATIONS = 20
        
        // Polling variables
        private var chooserRetryCount = 0
        private const val MAX_CHOOSER_RETRIES = 10
        private const val CHOOSER_POLL_INTERVAL = 300L

        fun primeDirectShare() {
            pendingAutoSend = true
            isAutoSending = true
            automationActive = true
            Log.d("JagoAccessibility", "Direct Share Auto-Send primed (Automation Active)")
            instance?.startAutoSendPolling()
        }

        fun primeAutomation(app: String?, contact: String?) {
            targetApp = app
            targetContact = contact
            pendingAutoSend = false // Reset just in case
            isAutoSending = false
            Log.d("JagoAccessibility", "Automation primed: App=$app, Contact=$contact")
            
            // Start polling for the app in the chooser immediately if an app is targeted
            if (app != null) {
                instance?.startChooserPolling()
            }
        }

        fun takeScreenshot(): Boolean {
            val result = instance?.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false
            if (result) {
                Log.d("JagoAccessibility", "Screenshot triggered")
            } else {
                Log.e("JagoAccessibility", "Failed to trigger screenshot or service not running")
            }
            return result
        }

        fun clickShutter(): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            val nodes = findShutterNodes(root)
            for (node in nodes) {
                if (node.isClickable) {
                    val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (result) {
                        Log.d("JagoAccessibility", "Shutter triggered")
                        return true
                    }
                }
            }
            Log.e("JagoAccessibility", "Shutter button not found or not clickable")
            return false
        }

        fun clickFirstSpotifyResult(): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            Log.d("JagoAccessibility", "Scanning for Spotify results...")
            
            val nodes = findSpotifyNodes(root)
            if (nodes.isNotEmpty()) {
                // Heuristic: The first result is often the "Top Result" or "Song"
                // We want to avoid clicking the "Filters" (e.g., Songs, Artists, Playlists) which are usually at the very top.
                // We'll iterate and pick the first one that seems substantial (has title + subtitle usually).
                
                for (node in nodes) {
                    if (node.isClickable) {
                         val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                         if (result) {
                             Log.d("JagoAccessibility", "Clicked Spotify node: ${node.viewIdResourceName} / ${node.className}")
                             return true
                         }
                    } else {
                        // Try parent
                         var parent = node.parent
                         while (parent != null) {
                             if (parent.isClickable) {
                                  val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                  if (result) {
                                      Log.d("JagoAccessibility", "Clicked Spotify parent node")
                                      return true
                                  }
                                  break
                             }
                             parent = parent.parent
                         }
                    }
                }
            }
            Log.e("JagoAccessibility", "No suitable Spotify result found")
            return false
        }

        private fun findSpotifyNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
            val list = mutableListOf<AccessibilityNodeInfo>()
            val stack = java.util.Stack<AccessibilityNodeInfo>()
            stack.push(root)
            
            // We want to find list items.
            // In Spotify, these are usually ViewGroups containing TextViews.
            
            while (stack.isNotEmpty()) {
                val node = stack.pop()
                if (node == null) continue
                
                // Exclude common non-content areas
                val id = node.viewIdResourceName?.lowercase() ?: ""
                if (id.contains("navigation") || id.contains("tab") || id.contains("toolbar") || id.contains("search_box")) {
                    continue
                }

                // Check text content to see if it looks like a song row
                // We are looking for a container that has text, but isn't just a filter chip
                if ((node.className == "android.view.ViewGroup" || node.className == "android.widget.FrameLayout" || node.className == "android.widget.LinearLayout") && node.isClickable) {
                     // Check children for text
                     if (hasTextChild(node)) {
                         list.add(node)
                     }
                }
                
                // Add children to stack (reverse order to process top-down naturally if using stack)
                for (i in node.childCount - 1 downTo 0) {
                     node.getChild(i)?.let { stack.push(it) }
                }
            }
            return list
        }

        private fun hasTextChild(node: AccessibilityNodeInfo): Boolean {
             for (i in 0 until node.childCount) {
                 val child = node.getChild(i) ?: continue
                 if (!child.text.isNullOrEmpty()) {
                     return true
                 }
                 // Deep check? Maybe just 1 level is enough for performance
             }
             return false
        }

        private fun findShutterNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
            val list = mutableListOf<AccessibilityNodeInfo>()
            val stack = java.util.Stack<AccessibilityNodeInfo>()
            stack.push(root)
            
            while (stack.isNotEmpty()) {
                val node = stack.pop()
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                val text = node.text?.toString()?.lowercase() ?: ""
                val id = node.viewIdResourceName?.lowercase() ?: ""
                
                if (desc.contains("shutter") || desc.contains("take") || desc.contains("capture") || desc.contains("photo") ||
                    text.contains("shutter") || id.contains("shutter") || id.contains("center") || id.contains("capture")) {
                    list.add(node)
                }
                
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { stack.push(it) }
                }
            }
            return list
        }

        fun isServiceRunning(): Boolean = instance != null

        fun readNotifications(): String {
            synchronized(recentNotifications) {
                if (recentNotifications.isEmpty()) {
                    return "No new notifications"
                }
                // Return most recent 5 notifications, newest first
                val toRead = recentNotifications.takeLast(5).reversed()
                val result = toRead.joinToString(". ")
                // Clear after reading so next call doesn't repeat
                recentNotifications.clear()
                Log.d("JagoAccessibility", "Reading ${toRead.size} notifications")
                return result
            }
        }

        fun readScreen(): String {
            val root = instance?.rootInActiveWindow
                ?: return "Cannot read screen right now"
        
            val texts = mutableListOf<String>()
            collectVisibleText(root, texts)
        
            if (texts.isEmpty()) return "Nothing to read on screen"
        
            // Join with pause between items for natural TTS flow
            val result = texts.distinct().joinToString(". ")
            Log.d("JagoAccessibility", "Screen text collected: ${texts.size} items")
            return result
        }
        
        private fun collectVisibleText(
            node: AccessibilityNodeInfo?,
            texts: MutableList<String>
        ) {
            if (node == null) return
        
            // Only collect visible nodes with meaningful text
            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
        
                // Add text if it exists and isn't just whitespace or a single char
                if (!text.isNullOrEmpty() && text.length > 1) {
                    texts.add(text)
                } else if (!desc.isNullOrEmpty() && desc.length > 1 && text.isNullOrEmpty()) {
                    // Use content description as fallback (important for icons/buttons)
                    texts.add(desc)
                }
            }
        
            // Recurse into children
            for (i in 0 until node.childCount) {
                collectVisibleText(node.getChild(i), texts)
            }
        }

        private var isSearchingForContact = false

        private fun waitForUI(timeoutMs: Long = 4000): AccessibilityNodeInfo? {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                val root = instance?.rootInActiveWindow
                if (root != null && root.childCount > 0) {
                    Log.d("JagoAccessibility", "UI frame detected")
                    return root
                }
                try { Thread.sleep(120) } catch (e: InterruptedException) { e.printStackTrace() }
            }
            Log.d("JagoAccessibility", "UI frame NOT detected (timeout)")
            return null
        }


        private fun selectContactViaSearch(contactName: String) {
            Log.d("JagoAccessibility", "Starting Search Automation for: $contactName")
            
            // Step 1: Find and Click Search Button
            // We use a short polling to ensure UI is ready, or just one shot if we assume waitForUI was done (but we are replacing logic)
            // Let's rely on a helper that re-posts itself if not found, or use the requested flow.
            // Prompt says: "Find Search Button... If found -> ACTION_CLICK... Wait ~500ms... etc"
            
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            
            val step1FindSearch = object : Runnable {
                var attempts = 0
                override fun run() {
                    val root = instance?.rootInActiveWindow
                    if (root == null) {
                        if (attempts < 5) {
                            attempts++
                            handler.postDelayed(this, 500) 
                            return
                        }
                        Log.e("JagoAccessibility", "Failed to access root window for Search")
                        Companion.isSearchingForContact = false
                        return
                    }

                    val searchNode = findSearchNode(root)
                    if (searchNode != null) {
                        Log.d("JagoAccessibility", "WhatsApp Search clicked")
                        // Use CLICKABLE parent if needed, similar to previous robust logic
                        var clickable = searchNode
                        while (clickable != null && !clickable.isClickable) {
                            clickable = clickable.parent
                        }
                        clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: searchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        
                        // Step 2: Wait for Search Field (500ms)
                        handler.postDelayed({ 
                            enterContactName(contactName) 
                        }, 500)
                    } else {
                        if (attempts < 5) {
                            attempts++
                            handler.postDelayed(this, 500)
                        } else {
                            Log.e("JagoAccessibility", "Search button not found")
                            Companion.isSearchingForContact = false
                        }
                    }
                }
            }
            handler.post(step1FindSearch)
        }

        private fun enterContactName(contactName: String) {
             val handler = android.os.Handler(android.os.Looper.getMainLooper())
             val root = instance?.rootInActiveWindow ?: return
             
             // Step 3: Enter Contact Name
             // Find EditText
             val editText = findNodeByClassName(root, "android.widget.EditText")
             if (editText != null) {
                 val arguments = android.os.Bundle()
                 arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, contactName)
                 editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                 Log.d("JagoAccessibility", "Contact name entered: $contactName")
                 
                 // Step 4: Wait Results (700ms)
                 handler.postDelayed({
                     clickFirstResult()
                 }, 700)
             } else {
                 Log.e("JagoAccessibility", "Search field (EditText) not found")
                 Companion.isSearchingForContact = false
             }
        }

        private fun clickFirstResult() {
             val root = instance?.rootInActiveWindow ?: return
             
             // Step 5: Click First Result
             // We look for a clickable node that is likely a list item.
             // Usually matching the contact Name or just the first result in the list.
             // Let's find ANY clickable node that is NOT the search box and NOT the toolbar.
             // Or better: The first result usually has the contact name?
             // Prompt says: "Click FIRST search result"
             
             // We can search for the contact name again?
             // Or simply find the first clickable item in the list.
             // Let's try to find potential results.
             
             // Strategy: Find nodes that are clickable and are NOT the EditText.
             val allNodes = flattenNodes(root)
             for (node in allNodes) {
                 if (node.isClickable && node.className != "android.widget.EditText" && node.className != "android.widget.ImageButton") {
                      // Check if it looks like a result (has children, text?)
                      // This is a heuristic. 
                      // Better: Search for specific text if we know it?
                      // But prompt says "Click FIRST search result".
                      
                      // Let's try to find the contact name in the result list?
                      // If we searched for "Mummy", the result should contain "Mummy".
                      val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                       if (text.isNotEmpty()) {
                           Log.d("JagoAccessibility", "First search result clicked: $text")
                           node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                           
                           // Success
                           Companion.targetContact = null
                           Companion.isSearchingForContact = false
                           com.example.jago.logic.JagoTTS.speak("Ready to send")
                           return
                       }
                 }
             }
             
             Log.e("JagoAccessibility", "No clickble search result found")
             Companion.isSearchingForContact = false
        }
        
        private fun findSearchNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (!queue.isEmpty()) {
                val node = queue.poll() ?: continue
                val desc = node.contentDescription?.toString() ?: ""
                val id = node.viewIdResourceName?.toString() ?: ""
                
                if (desc.contains("Search", ignoreCase = true) || id.contains("search", ignoreCase = true)) {
                    return node
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
            return null
        }
        
        private fun findNodeByClassName(root: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
             val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (!queue.isEmpty()) {
                val node = queue.poll() ?: continue
                if (node.className == className) {
                    return node
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
            return null
        }
        
        private fun flattenNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
            val list = mutableListOf<AccessibilityNodeInfo>()
            val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (!queue.isEmpty()) {
                val node = queue.poll() ?: continue
                list.add(node)
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
            return list
        }
    }
    
    // Polling Handler
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    private fun startChooserPolling() {
        Log.d("JagoAccessibility", "Starting chooser polling loop")
        Companion.chooserRetryCount = 0
        handler.post(chooserPollingRunnable)
    }

    private val chooserPollingRunnable = object : Runnable {
        override fun run() {
            if (Companion.targetApp == null) {
                return
            }
            
            if (Companion.chooserRetryCount >= Companion.MAX_CHOOSER_RETRIES) {
                 Log.e("JagoAccessibility", "Chooser target not found after ${Companion.MAX_CHOOSER_RETRIES} attempts")
                 Companion.targetApp = null // Stop trying
                 return
            }

            Log.d("JagoAccessibility", "Chooser scan attempt ${Companion.chooserRetryCount + 1}")
            
            val root = rootInActiveWindow
            if (root != null) {
                if (checkForAppInChooser(root, Companion.targetApp!!)) {
                    Companion.targetApp = null // Success
                    return
                }
            } else {
                 Log.d("JagoAccessibility", "Root window is null, waiting...")
            }

            Companion.chooserRetryCount++
            handler.postDelayed(this, Companion.CHOOSER_POLL_INTERVAL)
        }
    }
    
    private fun checkForAppInChooser(root: AccessibilityNodeInfo, appName: String): Boolean {
        val matchedNode = findNodeByText(root, appName)
        if (matchedNode != null) {
             if (matchedNode.isClickable) {
                 Log.d("JagoAccessibility", "Chooser target clicked: $appName")
                 matchedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                 return true
             } else {
                 var parent = matchedNode.parent
                 while (parent != null) {
                     if (parent.isClickable) {
                          Log.d("JagoAccessibility", "Chooser target clicked (Parent): $appName")
                          parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                          return true
                     }
                     parent = parent.parent
                 }
             }
        }
        return false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("JagoAccessibility", "Service Connected")
        instance = this
        
        // Explicitly request key filtering to ensure it works
        val info = serviceInfo
        if (info != null) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            serviceInfo = info
            Log.d("JagoAccessibility", "Key filtering flag set explicitly")
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("JagoAccessibility", "Service Unbound")
        instance = null
        handler.removeCallbacks(chooserPollingRunnable)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        // Capture incoming notifications for "read notifications" feature
        if (event?.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: "unknown"
            val text = event.text?.joinToString(" ")?.trim()
            if (!text.isNullOrEmpty() && pkg != "com.example.jago") {
                val appName = try {
                    instance?.packageManager
                        ?.getApplicationLabel(
                            instance!!.packageManager.getApplicationInfo(pkg, 0)
                        )?.toString() ?: pkg
                } catch (e: Exception) { pkg }
        
                val notification = "$appName: $text"
                synchronized(recentNotifications) {
                    // Avoid duplicate consecutive notifications
                    if (recentNotifications.lastOrNull() != notification) {
                        recentNotifications.add(notification)
                        if (recentNotifications.size > MAX_NOTIFICATIONS) {
                            recentNotifications.removeAt(0)
                        }
                    }
                }
                Log.d("JagoAccessibility", "Notification captured: $notification")
            }
        }

        // Global Window State Monitoring for TTS Interruption
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (com.example.jago.logic.JagoTTS.isSpeaking) {
                val packageName = event.packageName?.toString()
                if (packageName != null && packageName != "com.example.jago") {
                    Log.d("JagoAccessibility", "Window changed to $packageName -> Stopping speech")
                    com.example.jago.logic.JagoTTS.stopSpeaking()
                }
            }
        }

        // Hybrid Automation Logic
        if ((event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            
            // Stage 1 is now handled by startChooserPolling() triggered from primeAutomation
            // We keep this purely for Stage 2 (Contact Selection inside the app)
            // or if polling misses it (which it shouldn't, but redundancy is okay if managed carefully)
            // Actually, let's let polling handle Stage 1 entirely to avoid double clicks.

            if (Companion.targetContact != null && !Companion.isSearchingForContact) {
                 Companion.isSearchingForContact = true
                 // Use Handler-based search automation instead of Thread.sleep
                 Companion.selectContactViaSearch(Companion.targetContact!!)
            }

            // Direct Share Auto-Send Logic
            if (Companion.pendingAutoSend && event.packageName == "com.whatsapp" && !Companion.isAutoSending) {
                Log.d("JagoAccessibility", "WhatsApp UI detected. Starting Auto-Send Polling...")
                Companion.isAutoSending = true
                instance?.startAutoSendPolling()
            }
        }
    }

    private fun startAutoSendPolling() {
        Log.d("JagoAccessibility", "Auto-Send Polling Started")
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        val autoSendRunnable = object : Runnable {
            var attempts = 0
            val maxAttempts = 20 // 6 seconds (300ms * 20) to handle slow animations

            override fun run() {
                if (!Companion.pendingAutoSend) {
                     Log.d("JagoAccessibility", "Auto-Send aborted or completed")
                     Companion.isAutoSending = false
                     return
                }

                if (attempts >= maxAttempts) {
                    Log.e("JagoAccessibility", "Auto-Send timed out after 6 seconds")
                    
                    // Final Resort: Blind Gesture Click (User Requested)
                    Log.w("JagoAccessibility", "Attempting Backup Gesture Click at (1121, 2522)")
                    instance?.performGestureClick(1121f, 2522f)
                    
                    Companion.pendingAutoSend = false
                    Companion.isAutoSending = false
                    return
                }

                // Strategy 1: Iterate ALL Windows (Popup logic)
                val windows = instance?.windows
                if (windows != null && windows.isNotEmpty()) {
                    for (window in windows) {
                        val root = window.root
                        if (root != null) {
                            if (tryFindAndClickSend(root)) {
                                return
                            }
                        }
                    }
                }

                // Strategy 2: Fallback to Active Window Root (Main logic)
                val activeRoot = rootInActiveWindow
                if (activeRoot != null) {
                    if (tryFindAndClickSend(activeRoot)) {
                        return
                    }
                } else {
                     Log.d("JagoAccessibility", "Auto-Send: Root window null")
                }

                attempts++
                handler.postDelayed(this, 300)
            }
        }
        handler.post(autoSendRunnable)
    }

    private fun performGestureClick(x: Float, y: Float) {
        val path = android.graphics.Path()
        path.moveTo(x, y)
        path.lineTo(x + 1, y + 1) // Small movement to ensure path is valid
        
        val builder = android.accessibilityservice.GestureDescription.Builder()
        builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)) // 50ms tap
        
        val gesture = builder.build()
        val dispatched = dispatchGesture(gesture, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                Log.d("JagoAccessibility", "Gesture Click Completed at ($x, $y)")
            }

            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                Log.e("JagoAccessibility", "Gesture Click Cancelled")
            }
        }, null)
        
        Log.d("JagoAccessibility", "dispatchGesture dispatched: $dispatched")
    }

    private fun tryFindAndClickSend(root: AccessibilityNodeInfo): Boolean {
        // Method 1: ID Search (Primary)
        val sendNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")

        if (sendNodes != null && sendNodes.isNotEmpty()) {
            val sendButton = sendNodes[0]
             if (sendButton.isVisibleToUser && performRobustClick(sendButton)) {
                 Log.d("JagoAccessibility", "Auto-Send SUCCESS via ID")
                 finishAutoSend()
                 return true
            }
        }

        // Method 2: Text Search "Send" (Fallback)
        val textNodes = root.findAccessibilityNodeInfosByText("Send")
        if (textNodes != null) {
            for (node in textNodes) {
                val desc = node.contentDescription?.toString()
                val text = node.text?.toString()
                
                if (node.className == "android.widget.ImageButton" && 
                    (desc.equals("Send", ignoreCase = true) || text.equals("Send", ignoreCase = true))) {
                    
                    if (node.isVisibleToUser && performRobustClick(node)) {
                         Log.d("JagoAccessibility", "Auto-Send SUCCESS via Description")
                         finishAutoSend()
                         return true
                    }
                }
            }
        }
        return false
    }

    private fun finishAutoSend() {
        Companion.pendingAutoSend = false
        Companion.isAutoSending = false
        
        if (Companion.automationActive) {
            Log.d("JagoAccessibility", "Automation success. Returning to previous app in 500ms...")
            handler.postDelayed({
                returnToPreviousApp()
            }, 500)
            Companion.automationActive = false
        }
    }

    private fun returnToPreviousApp() {
        Log.d("JagoAccessibility", "Performing Double Back Action to return to previous app")
        performGlobalAction(GLOBAL_ACTION_BACK)
        
        // WhatsApp often needs 2 back presses (Chat -> Main List -> Previous App)
        handler.postDelayed({
             Log.d("JagoAccessibility", "Performing Second Back Action")
             performGlobalAction(GLOBAL_ACTION_BACK)
        }, 300)
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        // Deprecated, use performRobustClick
        return performRobustClick(node)
    }

    private fun performRobustClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                // Focus first (Strategy 2 from user)
                current.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val result = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (result) return true
            }
            current = current.parent
        }
        return false
    }

    private fun findNodesByDescription(root: AccessibilityNodeInfo, description: String): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (!queue.isEmpty()) {
            val node = queue.poll() ?: continue
            val desc = node.contentDescription?.toString() ?: ""
            if (desc.equals(description, ignoreCase = true)) {
                list.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return list
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (!queue.isEmpty()) {
            val node = queue.poll() ?: continue

            val nodeText = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            
            // Case-insensitive match, prefer exact but accept contains for now as per "Hybrid Automation"
            if (nodeText.equals(text, ignoreCase = true) || desc.equals(text, ignoreCase = true)) {
                return node
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }



    override fun onKeyEvent(event: android.view.KeyEvent?): Boolean {
        if (event != null) {
            Log.d("JagoAccessibility", "Key Event: ${event.keyCode}, Action: ${event.action}")
            if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_DOWN) {
                if (com.example.jago.logic.JagoTTS.isSpeaking) {
                    Log.d("JagoAccessibility", "Back button detected")
                    Log.d("JagoAccessibility", "Stopping TTS from AccessibilityService")
                    com.example.jago.logic.JagoTTS.stopSpeaking()
                    // Consume the event so it doesn't trigger back navigation in the foreground app
                    return true
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        Log.d("JagoAccessibility", "Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
