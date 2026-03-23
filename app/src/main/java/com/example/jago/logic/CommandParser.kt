package com.example.jago.logic

import java.util.Locale
import android.util.Log

data class Command(
    val type: CommandType, 
    val contactName: String? = null, 
    val messageBody: String? = null,
    val numericValue: Int? = null,
    val isRelative: Boolean = false,
    val aiResponse: String? = null,
    val missingTime: Boolean = false,
    val missingMessage: Boolean = false,
    val missingTimeUnit: Boolean = false,
    val triggerMillis: Long? = null,
    val formattedTime: String? = null,
    val repeatIntervalMillis: Long? = null,
    val searchPlatform: String? = null, // "google" or "youtube"
    val captureNew: Boolean = false,
    val usesContextReference: Boolean = false,
    val scheduledCommand: Command? = null,
    val triggerAtMillis: Long? = null,
    val hour: Int? = null,
    val minute: Int? = null
)

enum class CommandType {
    CALL, OPEN_WHATSAPP, LOCK_DEVICE, OPEN_APP, 
    FLASHLIGHT_ON, FLASHLIGHT_OFF, VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE,
    BRIGHTNESS_INCREASE, BRIGHTNESS_DECREASE, BATTERY_CHECK,
    OPEN_WIFI_SETTINGS, OPEN_BLUETOOTH_SETTINGS,
    QUERY_BRIGHTNESS, QUERY_VOLUME, QUERY_FLASHLIGHT,
    PLAY_MEDIA, PAUSE_MEDIA, NEXT_MEDIA, PREVIOUS_MEDIA,
    TAKE_SCREENSHOT,
    ENABLE_DND, DISABLE_DND, SILENT_MODE, FOCUS_MODE,
    CLICK_PHOTO,
    SET_REMINDER,
    CLOSE_APP,
    AI_RESPONSE,
    CALCULATE,
    SEARCH,
    SET_ALARM,
    SET_ALARM_CUSTOM,
    PLAY_SPOTIFY,
    SEND_RECENT_PHOTO,
    SEND_WHATSAPP_MESSAGE,
    SCREENSHOT_AND_WHATSAPP,
    SCHEDULED_ACTION,
    OPEN_SCHEDULE,
    READ_NOTIFICATIONS,
    READ_SCREEN,
    SET_LANGUAGE,
    UNKNOWN
}

class CommandParser {

    private val intentSeeds = mapOf(
        "FLASHLIGHT" to listOf("flashlight", "torch", "light", "lamp"),
        "VOLUME" to listOf("volume", "sound", "audio", "mute"),
        "BRIGHTNESS" to listOf("brightness", "screen brightness", "brighter", "darker", "dim"),
        "BATTERY" to listOf("battery", "charge", "power"),
        "LOCK" to listOf("lock"),
        "WIFI" to listOf("wifi"),
        "BLUETOOTH" to listOf("bluetooth"),
        "CALL" to listOf("call", "dial", "ring"),
        "OPEN_APP" to listOf("open", "launch", "start", "run"),
        "MESSAGE" to listOf("message", "text", "send"),
        "MEDIA_PLAY" to listOf("play", "resume"),
        "MEDIA_PAUSE" to listOf("pause", "stop music"),
        "MEDIA_NEXT" to listOf("next", "skip", "track"),
        "MEDIA_PREV" to listOf("previous", "back track", "back song"),
        "SCREENSHOT" to listOf("screenshot", "capture screen", "take screenshot"),
        "DND" to listOf("dnd", "do not disturb", "don't disturb", "dont disturb"),
        "SILENT" to listOf("silent mode", "mute phone", "can't hear", "cant hear"),
        "FOCUS" to listOf("focus mode"),
        "PHOTO_ACTION" to listOf("click", "take", "capture", "photo", "picture"),
        "REMINDER" to listOf("remind", "reminder", "notify me", "tell me"),
        "CLOSE_APP" to listOf("close", "exit", "terminate", "quit", "minimize", "stop"),
        "SEARCH" to listOf("search", "look up", "find"),
        "ALARM" to listOf("alarm", "wake"),
        "RECENT_PHOTO" to listOf("recent pic", "recent photo", "latest pic", "latest photo", "recent image", "latest image"),
        "SCHEDULE_VIEW" to listOf("scheduled tasks", "future tasks", "open schedule", "show schedule", "my schedule", "pending tasks"),
        "READ_NOTIFICATIONS" to listOf(
            "notification", "notifications", "notify",
            "padhao notification", "notification padhao",
            "kya aaya", "messages padhao", "alerts padhao",
            "read notifications", "check notifications",
            "inbox", "kya notification"
        ),
        "READ_SCREEN" to listOf(
            "screen padhao", "read screen", "kya likha hai",
            "screen kya hai", "padhao screen", "what's on screen",
            "what is on screen", "describe screen",
            "screen batao", "kya dikh raha hai", "screen read karo"
        ),
        "SET_LANGUAGE" to listOf(
            // English — all variations
            "speak in english", "speak english",
            "english mai bolo", "english mein bolo", "english me bolo",
            "english main bolo", "english m bolo",
            "angreji mai bolo", "angreji mein bolo", "angreji me bolo",
            "angreji main bolo", "angreji bol",
            "switch to english", "english language",
            "talk in english", "respond in english",
            "english mein", "english mai", "english me",
            "angreji mein", "angreji mai",
            // Hindi — all variations
            "speak in hindi", "speak hindi",
            "hindi mai bolo", "hindi mein bolo", "hindi me bolo",
            "hindi main bolo", "hindi m bolo", "hindi bol",
            "switch to hindi", "hindi language",
            "talk in hindi", "respond in hindi",
            "bolo hindi mein", "hindi mein bol",
            "hindi mein", "hindi mai", "hindi me",
            "hindi main"
        )
    )
    
    // ... existing actionModifiers ...

    // ... existing parse methods ...




    private val actionModifiers = mapOf(
        "ON" to listOf("on", "enable", "start", "turn on", "active", "activate"),
        "OFF" to listOf("off", "disable", "stop", "turn off", "inactive", "deactivate"),
        "UP" to listOf("up", "increase", "raise", "higher", "more"),
        "DOWN" to listOf("down", "decrease", "lower", "less", "dim", "reduce"),
        "MUTE" to listOf("mute", "silence", "quiet"),
        "QUERY" to listOf("what is", "status", "current", "how much", "level", "tell me")
    )
    
    private val contextualPhrases = mapOf(
        "too bright" to CommandType.BRIGHTNESS_DECREASE,
        "very bright" to CommandType.BRIGHTNESS_DECREASE,
        "hurting my eyes" to CommandType.BRIGHTNESS_DECREASE,
        "screen is bright" to CommandType.BRIGHTNESS_DECREASE,
        "screen bright" to CommandType.BRIGHTNESS_DECREASE,
        "reduce brightness" to CommandType.BRIGHTNESS_DECREASE,
        "too dark" to CommandType.BRIGHTNESS_INCREASE,
        "can't see" to CommandType.BRIGHTNESS_INCREASE,
        "increase brightness" to CommandType.BRIGHTNESS_INCREASE,
        "screen is dark" to CommandType.BRIGHTNESS_INCREASE,
        "screen dark" to CommandType.BRIGHTNESS_INCREASE,
        "too loud" to CommandType.VOLUME_DOWN,
        "very loud" to CommandType.VOLUME_DOWN,
        "this is loud" to CommandType.VOLUME_DOWN,
        "lower volume" to CommandType.VOLUME_DOWN,
        "too quiet" to CommandType.VOLUME_UP,
        "can't hear" to CommandType.VOLUME_UP,
        "increase volume" to CommandType.VOLUME_UP,
        "volume is low" to CommandType.VOLUME_UP,
        "volume low" to CommandType.VOLUME_UP
    )

    fun parse(text: String): List<Command> {
        // Pre-process to avoid splitting composite commands
        // "screenshot and send" -> "screenshot with send" (or similar to avoid 'and')
        // We use a temporary placeholder that doesn't contain the split keywords
        var processedText = text.replace(Regex("\\b(screenshot|capture screen) (and|then) (send|share)\\b"), "$1 with $3")
        
        val conjunctions = Regex("\\b(and|then|&)\\b")
        val segments = processedText.split(conjunctions).map { it.trim() }.filter { it.isNotEmpty() }
        
        Log.d("CommandParser", "Detected ${segments.size} potential command segments")
        
        val commands = segments.map { segment ->
            parseSingleSegment(segment)
        }

        // If we have multiple commands, filter out UNKNOWN to avoid noise
        return if (commands.size > 1) {
            commands.filter { it.type != CommandType.UNKNOWN }
        } else {
            commands
        }
    }

    private fun parseSingleSegment(text: String): Command {
        // 0. Pre-check for Scheduling (Time triggers)
        // We do this BEFORE normalization to preserve case if needed, but normalization is fine.
        val cleanText = normalize(text)

        // EXCEPTION: Don't schedule if it's already a native scheduling command (Alarm, Reminder)
        // We'll let the normal parsing flow handle those first. If they fail or return UNKNOWN, we *could* retry, 
        // but Alarm/Reminder are high priority.
        
        // Let's rely on the fact that Alarm/Reminder strings typically start with "remind me" or "wake me".
        // If the user says "Turn on light at 5pm", it won't trigger Alarm logic efficiently unless "at 5pm" is caught.
        
        // STRATEGY: 
        // 1. Try to extract time.
        // 2. If time found, remove it and parse the REST.
        // 3. If the REST parses to a valid command (and IS NOT Alarm/Reminder), wrap it.
        
        val sequenceCheck = extractSchedulingTime(cleanText)
        val triggerTime = sequenceCheck.first
        val textWithoutTime = sequenceCheck.second
        
        // If we found a time, rely on textWithoutTime for the inner command parsing
        val textToParse = if (triggerTime != null) textWithoutTime else cleanText
        // But wait, "Set alarm at 5pm" -> "Set alarm" -> SET_ALARM (missing time).
        // "Remind me to go" (missing time) -> SET_REMINDER.
        // If we strip time from "Set alarm at 5pm", we get "Set alarm", which parses to SET_ALARM (missing time).
        // We don't want to schedule a "missing time alarm" to run at 5pm. We want the alarm SET for 5pm.
        
        // So: Only wrap if the inner command is NOT (Alarm, Reminder).
        // OR: If the inner command IS Alarm/Reminder, we discard our scheduling wrapper and return the original parsing of the FULL text.
        
        // Let's parse the text WITHOUT time first.
        var innerCommand = parseCommandLogic(textToParse)
        
        // If we detected a time trigger...
        if (triggerTime != null) {
             // Check if inner command is forbidden from being wrapped
             if (innerCommand.type != CommandType.SET_ALARM && 
                 innerCommand.type != CommandType.SET_ALARM_CUSTOM && 
                 innerCommand.type != CommandType.SET_REMINDER &&
                 innerCommand.type != CommandType.UNKNOWN) {
                 
                 Log.d("CommandParser", "Scheduling detected: $triggerTime, wrapping ${innerCommand.type}")
                 return Command(
                     type = CommandType.SCHEDULED_ACTION, 
                     scheduledCommand = innerCommand, 
                     triggerAtMillis = triggerTime,
                     formattedTime = sequenceCheck.third // Add this field to Pair or lookup
                 )
             }
             // If it was UNKNOWN, maybe the stripped text lost meaning? 
             // "at 5pm" -> "" -> UNKNOWN.
        }

        // If no scheduling or invalid inner command, try parsing the ORIGINAL full text 
        // (This handles Alarm/Reminder which need the time info)
        return parseCommandLogic(cleanText)
    }

    // Moved the main switch-case logic here to allow reuse
    private fun parseCommandLogic(cleanText: String): Command {
        
        // 0. Priority Intent Lock - Spotify Playback
        // "play timeless on spotify", "put on believer on spotify", "start spotify"
        if (cleanText.contains("spotify") && (cleanText.contains("play") || cleanText.contains("put on") || cleanText.contains("start"))) {
            Log.d("CommandParser", "Priority intent locked -> PLAY_SPOTIFY")
            var query = cleanText
            val seeds = listOf("play", "put on", "start", "on spotify", "in spotify", "spotify", "jago")
            seeds.forEach { query = query.replace(it, "") }
            query = query.trim()
            
            return Command(type = CommandType.PLAY_SPOTIFY, messageBody = query)
        }

        // 0. Priority Intent Lock - ALARM (Detects "alarm", "wake", "wake me")
        // This MUST happen before any numeric or fuzzy parsing to prevent AI fallback
        val alarmSeeds = intentSeeds["ALARM"] ?: emptyList()
        if (alarmSeeds.any { cleanText.contains(it) }) {
             Log.d("CommandParser", "Priority intent locked -> ALARM")
             return parseAlarm(cleanText)
        }
        
        // Extract numeric value
        val numericRegex = Regex("\\d+")
        val match = numericRegex.find(cleanText)
        val numericValue = match?.value?.toInt()
        
        // Detect mode: 'to' forces absolute, 'by' forces relative.
        val isAbsoluteKeyword = cleanText.contains(" to ")
        val isRelativeKeyword = cleanText.contains(" by ")

        val isRelative = when {
            isAbsoluteKeyword -> false
            isRelativeKeyword -> true
            else -> cleanText.contains("increase") || 
                    cleanText.contains("decrease") || 
                    cleanText.contains("reduce") ||
                    cleanText.endsWith(" up") || 
                    cleanText.endsWith(" down") ||
                    cleanText.contains(" up ") ||
                    cleanText.contains(" down ")
        }

        // 1. Detect HIGH-SPECIFICITY intents first (to avoid collisions like 'screen')
        val priority1Keys = listOf("SET_LANGUAGE", "READ_NOTIFICATIONS", "READ_SCREEN", "RECENT_PHOTO", "REMINDER", "SCREENSHOT", "MEDIA_PLAY", "MEDIA_PAUSE", "MEDIA_NEXT", "MEDIA_PREV", "FLASHLIGHT", "OPEN_APP", "CLOSE_APP", "CALL", "MESSAGE", "DND", "SILENT", "FOCUS", "PHOTO_ACTION", "SEARCH", "SCHEDULE_VIEW")
        var matchedIntentKey: String? = null
        var matchedSeed: String? = null

        // Safety lists for Context Awareness
        val questionPhrases = listOf("what is", "define", "explain", "tell me about", "meaning of", "how do", "why is", "who is")
        val cameraActionVerbs = listOf("take", "click", "capture", "snap", "shoot")

        val isQuestionContext = questionPhrases.any { cleanText.contains(it) }

        for (key in priority1Keys) {
            val seeds = intentSeeds[key] ?: continue
            for (seed in seeds) {
                // Strict Camera Safety Logic
                if (key == "PHOTO_ACTION") {
                     // Rule 1: Never trigger camera in a question context
                     if (isQuestionContext) {
                         Log.d("CommandParser", "Question context detected -> Ignoring camera intent")
                         continue 
                     }
                     // Rule 2: If seed is a noun ("photo", "picture"), require an action verb
                     if ((seed == "photo" || seed == "picture") && cameraActionVerbs.none { cleanText.contains(it) }) {
                         continue
                     }
                }

                if (cleanText.contains(seed)) {
                    matchedIntentKey = key
                    matchedSeed = seed
                    if (key == "SCREENSHOT") Log.d("CommandParser", "Screenshot intent detected")
                    break
                }
            }
            if (matchedIntentKey != null) break
        }

        // 2. Detect DEVICE CONTROL intents if priority 1 didn't match
        if (matchedIntentKey == null) {
            val priority2Keys = listOf("BRIGHTNESS", "VOLUME", "BATTERY", "LOCK", "WIFI", "BLUETOOTH")
            for (key in priority2Keys) {
                val seeds = intentSeeds[key] ?: continue
                for (seed in seeds) {
                    if (cleanText.contains(seed)) {
                        matchedIntentKey = key
                        matchedSeed = seed
                        if (key == "BRIGHTNESS") Log.d("CommandParser", "Brightness intent detected")
                        break
                    }
                }
                if (matchedIntentKey != null) break
            }
        }

        // 3. Detect CONTEXTUAL variations if no direct seeds matched AND no numeric value
        if (matchedIntentKey == null && numericValue == null) {
            for ((phrase, type) in contextualPhrases) {
                if (cleanText.contains(phrase)) {
                    Log.d("CommandParser", "Contextual phrase detected: '$phrase'")
                    Log.d("CommandParser", "Resolved intent: $type")
                    if (type == CommandType.BRIGHTNESS_INCREASE || type == CommandType.BRIGHTNESS_DECREASE) {
                        Log.d("CommandParser", "Brightness intent detected")
                    }
                    return Command(type)
                }
            }
        }

        // 4. Resolve Modifier for matched seeds
        var matchedModifier: String? = null
        for ((modKey, modValues) in actionModifiers) {
            for (value in modValues) {
                if (cleanText.contains(value)) {
                    matchedModifier = modKey
                    break
                }
            }
            if (matchedModifier != null) break
        }

        if (matchedIntentKey != null) {
            Log.d("CommandParser", "Segment match: $matchedIntentKey (via '$matchedSeed'), mod: $matchedModifier, num: $numericValue, rel: $isRelative")
        }

        // 3. Resolve CommandType
        return when (matchedIntentKey) {
            "FLASHLIGHT" -> {
                if (matchedModifier == "QUERY" || (cleanText.contains("level") && !cleanText.contains("set"))) Command(CommandType.QUERY_FLASHLIGHT)
                else if (matchedModifier == "OFF") Command(CommandType.FLASHLIGHT_OFF, numericValue = numericValue, isRelative = isRelative)
                else Command(CommandType.FLASHLIGHT_ON, numericValue = numericValue, isRelative = isRelative)
            }
            "VOLUME" -> {
                if (matchedModifier == "QUERY") Command(CommandType.QUERY_VOLUME)
                else when (matchedModifier) {
                    "UP" -> Command(CommandType.VOLUME_UP, numericValue = numericValue, isRelative = isRelative)
                    "DOWN" -> Command(CommandType.VOLUME_DOWN, numericValue = numericValue, isRelative = isRelative)
                    "MUTE" -> Command(CommandType.VOLUME_MUTE)
                    "OFF" -> Command(CommandType.VOLUME_MUTE)
                    else -> if (numericValue != null) Command(CommandType.VOLUME_UP, numericValue = numericValue, isRelative = isRelative) 
                            else Command(CommandType.VOLUME_UP)
                }
            }
            "BRIGHTNESS" -> {
                if (matchedModifier == "QUERY") Command(CommandType.QUERY_BRIGHTNESS)
                else if (matchedModifier == "DOWN" || cleanText.contains("dim") || cleanText.contains("darker") || cleanText.contains("reduce")) 
                    Command(CommandType.BRIGHTNESS_DECREASE, numericValue = numericValue, isRelative = isRelative)
                else Command(CommandType.BRIGHTNESS_INCREASE, numericValue = numericValue, isRelative = isRelative)
            }
            "BATTERY" -> Command(CommandType.BATTERY_CHECK)
            "LOCK" -> Command(CommandType.LOCK_DEVICE)
            "WIFI" -> Command(CommandType.OPEN_WIFI_SETTINGS)
            "BLUETOOTH" -> Command(CommandType.OPEN_BLUETOOTH_SETTINGS)
            
            "CALL" -> {
                val payload = cleanText.removePrefix(matchedSeed ?: "").replace(":", "").trim()
                if (payload.isNotEmpty()) Command(CommandType.CALL, contactName = payload)
                else Command(CommandType.UNKNOWN)
            }
            "OPEN_APP" -> {
                // Special check for WhatsApp
                if (cleanText.contains("whatsapp")) return Command(CommandType.OPEN_WHATSAPP)
                
                val payload = cleanText.removePrefix(matchedSeed ?: "").replace(":", "").trim()
                if (payload.isNotEmpty()) Command(CommandType.OPEN_APP, contactName = payload)
                else Command(CommandType.UNKNOWN)
            }
            "SCHEDULE_VIEW" -> Command(CommandType.OPEN_SCHEDULE)
            "MESSAGE" -> {
                // Generalized Messaging Logic
                // Patterns: 
                // 1. "send message to [Name] that [Body]"
                // 2. "text [Name] saying [Body]"
                // 3. "tell [Name] [Body]"
                // 4. "message [Name] [Body]"
                
                var content = cleanText
                val seeds = intentSeeds["MESSAGE"] ?: emptyList()
                
                // 1. Remove triggering verb
                // Sort seeds by length desc to remove "send message" before "message"
                val sortedSeeds = seeds.sortedByDescending { it.length }
                for (seed in sortedSeeds) {
                    if (content.contains(seed)) {
                        content = content.replaceFirst(seed, "").trim()
                        break 
                    }
                }
                
                if (content.isEmpty()) return Command(CommandType.UNKNOWN)

                // 2. Identify Contact vs Body separation
                // Delimiters: " to ", " that ", " saying "
                var contactName: String = ""
                var messageBody: String = ""

                // Handle "to" prefix for contact
                if (content.startsWith("to ")) {
                     content = content.substring(3).trim()
                }

                // Split by " that " or " saying " first as they are strong separators
                // "text mom that i am late" -> Contact: mom, Body: i am late
                val strongDelimiters = listOf(" that ", " saying ")
                var splitFound = false
                
                for (delimiter in strongDelimiters) {
                    if (content.contains(delimiter)) {
                        val parts = content.split(delimiter, limit = 2)
                        contactName = parts[0].replace(":", "").trim()
                        messageBody = parts[1].trim()
                        splitFound = true
                        break
                    }
                }

                if (!splitFound) {
                    val words = content.split(" ")
                    if (words.size >= 3) {
                        // Try two-word contact name first (e.g. "Ram Prasad hello")
                        val twoWordContact = "${words[0]} ${words[1]}"
                        val remainingAfterTwo = words.drop(2).joinToString(" ")
                        
                        // Heuristic: if third word onwards looks like a message (common message starters)
                        // OR if two-word contact is more than 6 chars total (likely a real name)
                        val messageStarters = listOf("main", "mai", "mein", "i ", "aaj", "kal", "bhai",
                            "yaar", "hello", "hi", "hey", "please", "pls", "kya", "ab", "abhi")
                        val looksLikeMessage = messageStarters.any { remainingAfterTwo.startsWith(it) }
                        
                        if (looksLikeMessage || twoWordContact.length > 6) {
                            contactName = twoWordContact.replace(":", "").trim()
                            messageBody = remainingAfterTwo.trim()
                        } else {
                            // Fall back to single word contact
                            contactName = words[0].replace(":", "").trim()
                            messageBody = words.drop(1).joinToString(" ").trim()
                        }
                    } else if (words.size == 2) {
                        contactName = words[0].replace(":", "").trim()
                        messageBody = words[1].trim()
                    } else if (words.size == 1) {
                        contactName = words[0].replace(":", "").trim()
                        messageBody = ""
                    }
                }
                
                // PRONOUN DETECTION FIX
                // If the user says "Send it to Ansh", "it" might be caught as contact above if strict parsing fails
                // Or if content was "it to Ansh"
                if (isPronoun(contactName)) {
                     // Check if there is a "to [Name]" pattern in the body
                     // "it to Ansh"
                     val toRegex = Regex("\\bto (.+)")
                     val toMatch = toRegex.find(messageBody) // messageBody has the rest
                     if (toMatch != null) {
                         val actualContact = toMatch.groupValues[1].trim()
                         Log.d("CommandParser", "Contextual Message detected: 'Send it to $actualContact'")
                         return Command(CommandType.SEND_RECENT_PHOTO, contactName = actualContact, usesContextReference = true)
                     } else {
                         // "Send it" -> "it" is contact, body is empty?
                         return Command(CommandType.SEND_RECENT_PHOTO, contactName = null, usesContextReference = true)
                     }
                }

                Command(CommandType.SEND_WHATSAPP_MESSAGE, contactName = contactName, messageBody = messageBody)
            }
            "MEDIA_PLAY" -> Command(CommandType.PLAY_MEDIA)
            "MEDIA_PAUSE" -> Command(CommandType.PAUSE_MEDIA)
            "MEDIA_NEXT" -> Command(CommandType.NEXT_MEDIA)
            "MEDIA_PREV" -> Command(CommandType.PREVIOUS_MEDIA)
            "SCREENSHOT" -> {
                if (cleanText.contains("send") || cleanText.contains("share")) {
                    // Extract contact name: "screenshot and send to Mom"
                    // Handle "send it to", "share this with", etc.
                    val sendRegex = Regex(".*(send|share)(?: (it|this|that|them))?(?: (to|with))? ")
                    var contact = cleanText.replace(sendRegex, "").trim()
                    
                    if (contact.isNotEmpty() && !isPronoun(contact)) {
                         Command(CommandType.SCREENSHOT_AND_WHATSAPP, contactName = contact, captureNew = true)
                    } else {
                         Command(CommandType.TAKE_SCREENSHOT)
                    }
                } else {
                    Command(CommandType.TAKE_SCREENSHOT)
                }
            }
            "DND" -> {
                if (matchedModifier == "OFF") Command(CommandType.DISABLE_DND)
                else Command(CommandType.ENABLE_DND)
            }
            "SILENT" -> Command(CommandType.SILENT_MODE)
            "FOCUS" -> Command(CommandType.FOCUS_MODE)
            "PHOTO_ACTION" -> {
                Log.d("CommandParser", "Camera action detected")
                Command(CommandType.CLICK_PHOTO)
            }
            "REMINDER" -> {
                parseReminder(cleanText)
            }
            "CLOSE_APP" -> {
                val payload = cleanText.removePrefix(matchedSeed ?: "").trim()
                Command(CommandType.CLOSE_APP, contactName = if (payload.isNotEmpty()) payload else null)
            }
            "SEARCH" -> {
                parseSearch(cleanText)
            }
            "ALARM" -> {
                parseAlarm(cleanText)
            }
            "RECENT_PHOTO" -> {
                Log.d("CommandParser", "Recent photo intent detected")
                var payload = cleanText.replace(Regex("\\bthe\\b"), "")
                
                // 1. Remove specific filler phrases iteratively
                // Must order by length descending to catch "send a" before "send" if needed, 
                // though cleanText is already normalized.
                val fillers = listOf(
                    "send a", "share a", 
                    "send", "share", 
                    "recent pic", "recent photo", 
                    "latest pic", "latest photo",
                    "recent image", "latest image",
                    "recent", "latest"
                )
                
                fillers.forEach { filler ->
                    payload = payload.replace(filler, "")
                }
                
                // 2. Remove "to" only if it's a prefix or surrounded by spaces
                // We want to avoid removing "tom" from "tommy"
                payload = payload.trim()
                if (payload.startsWith("to ")) {
                    payload = payload.substring(3).trim()
                }
                payload = payload.replace(" to ", " ")

                // 3. Extract App Name (on/via/in)
                var contact: String?
                var appName: String? = null

                val separatorRegex = Regex(" (on|via|in) ")
                val separatorMatch = separatorRegex.find(payload)

                if (separatorMatch != null) {
                    val splitIndex = separatorMatch.range.first
                    contact = payload.substring(0, splitIndex).trim()
                    appName = payload.substring(separatorMatch.range.last + 1).trim()
                } else {
                    contact = payload.trim()
                }
                
                // Final cleanup of contact
                if (contact.isEmpty() || isPronoun(contact)) contact = null
                else Log.d("CommandParser", "Cleaned contact phrase: $contact")
                
                if (appName?.isEmpty() == true) appName = null

                Command(CommandType.SEND_RECENT_PHOTO, contactName = contact, searchPlatform = appName, usesContextReference = true)
            }
            "READ_NOTIFICATIONS" -> Command(CommandType.READ_NOTIFICATIONS)
            "READ_SCREEN" -> Command(CommandType.READ_SCREEN)
            "SET_LANGUAGE" -> {
                // Check for Hindi vs English keywords
                val hindiKeywords = listOf("hindi", "hindi mein", "hindi mai", "hindi me", "hindi main")
                val englishKeywords = listOf("english", "angreji")
                val isHindi = hindiKeywords.any { cleanText.contains(it) }
                val isEnglish = englishKeywords.any { cleanText.contains(it) }
                // If both detected (shouldn't happen) default to Hindi
                val lang = when {
                    isHindi && !isEnglish -> "hi"
                    isEnglish -> "en"
                    else -> "en" // default
                }
                Command(type = CommandType.SET_LANGUAGE, messageBody = lang)
            }
            else -> {
                // Try parsing as Math
                val mathCommand = parseMath(cleanText)
                mathCommand ?: Command(CommandType.UNKNOWN)
            }
        }
    }

    private fun parseAlarm(cleanText: String): Command {
        Log.d("CommandParser", "Parsing alarm: $cleanText")
        
        var hour: Int? = null
        var minute: Int? = null
        var missingTime = false
        val now = System.currentTimeMillis()
        
        // 1. Relative Time: "after 5 mins", "in 1 hour", "wake me up in 10 minutes"
        val relativeRegex = Regex("\\b(in|after|later|within|next|later in) (\\d+) (minutes|minute|mins|min|hours|hour|hrs|hr)\\b")
        val matchRelative = relativeRegex.find(cleanText)
        
        if (matchRelative != null) {
            val value = matchRelative.groupValues[2].toLong()
            val unit = matchRelative.groupValues[3]
            val multiplier = when {
                unit.startsWith("hour") || unit.startsWith("hr") -> 3600000L
                unit.startsWith("min") -> 60000L
                else -> 60000L
            }
            
            val triggerTime = now + (value * multiplier)
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = triggerTime
            
            hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            minute = calendar.get(java.util.Calendar.MINUTE)
            
            Log.d("CommandParser", "Relative Alarm Time detected: $hour:$minute (from now + $value $unit)")
        } else {
            // 2. Absolute Time: "at 7:30 pm", "at 6 am"
            val timeRegex = Regex("(at|for) (\\d{1,2})(:(\\d{2}))?\\s*(pm|am)?")
            val match = timeRegex.find(cleanText)
            
            if (match != null) {
                hour = match.groupValues[2].toInt()
                minute = if (match.groupValues[4].isNotEmpty()) match.groupValues[4].toInt() else 0
                val amPm = match.groupValues[5]

                // Convert to 24h format
                if (amPm == "pm" && hour < 12) hour += 12
                if (amPm == "am" && hour == 12) hour = 0
                
                Log.d("CommandParser", "Alarm Time detected: $hour:$minute")
            } else {
                missingTime = true
            }
        }

        return Command(
            type = CommandType.SET_ALARM_CUSTOM,
            hour = hour,
            minute = minute,
            missingTime = missingTime
        )
    }

    private fun parseSearch(cleanText: String): Command {
        var query = cleanText
        var platform = "google" // Default

        // 1. Detect " on " pattern for dynamic app search
        // Regex looks for " on " surrounded by spaces
        val onRegex = Regex(" on (.+)")
        val match = onRegex.find(cleanText)

        if (match != null) {
            // "Search timeless on spotify" -> group 1 is "spotify"
            platform = match.groupValues[1].trim()
            // Query is the part BEFORE " on "
            val splitIndex = cleanText.lastIndexOf(" on ")
            if (splitIndex != -1) {
                query = cleanText.substring(0, splitIndex).trim()
            }
        } else {
            // Legacy/Simple detection
            if (query.contains("youtube")) {
                platform = "youtube"
            } else if (query.contains("google")) {
                platform = "google"
            }
        }

        // 2. Extract Query Cleanup
        // Remove keywords
        val seeds = intentSeeds["SEARCH"] ?: emptyList()
        seeds.forEach { query = query.replace(it, "") }
        
        // Remove platform names ONLY if we did legacy detection or if they are redundant
        if (match == null) {
             query = query.replace("youtube", "").replace("google", "")
        }
        
        // Remove specific connectors (in case "on" wasn't caught or "in" is used)
        query = query.replace(Regex("\\b(in)\\b"), "")
        // We don't remove "on" globally anymore because we used it as a delimiter, 
        // but if the user said "search on spotify" (query empty), we handle it.

        // Final trim
        query = query.trim().replace(Regex("\\s+"), " ")
        
        Log.d("CommandParser", "Search intent detected")
        Log.d("CommandParser", "Platform: $platform")
        Log.d("CommandParser", "Query: $query")
        
        return if (query.isNotEmpty()) {
            Command(CommandType.SEARCH, messageBody = query, searchPlatform = platform)
        } else {
            // Fallback if query is empty
            if (platform != "google") {
                 // User said "Search on Spotify" but no query -> "Open Spotify" effectively, or ask for query
                 // For now, return command but empty body might be handled in Executor
                 Command(CommandType.SEARCH, messageBody = "", searchPlatform = platform)
            } else {
                 Command(CommandType.UNKNOWN, aiResponse = "I didn't catch what to search for.")
            }
        }
    }

    private fun parseReminder(cleanText: String): Command {
        Log.d("CommandParser", "Parsing reminder: $cleanText")
        
        // 1. Time Extraction
        var triggerMillis: Long? = null
        var formattedTime: String? = null
        var missingTimeUnit = false
        val now = System.currentTimeMillis()

        // 1.1 Relative time: "in/after/later/within/next X minutes..."
        val relativeRegex = Regex("\\b(in|after|later|within|next|later in) (\\d+) (minutes|minute|mins|min|hours|hour|hrs|hr|seconds|second|secs|sec)\\b")
        val matchRelative = relativeRegex.find(cleanText)
        
        if (matchRelative != null) {
            val value = matchRelative.groupValues[2].toLong()
            val unit = matchRelative.groupValues[3]
            val multiplier = when {
                unit.startsWith("hour") || unit.startsWith("hr") -> 3600000L
                unit.startsWith("min") -> 60000L
                unit.startsWith("sec") -> 1000L
                else -> 0L
            }
            if (multiplier > 0) {
                triggerMillis = now + (value * multiplier)
                formattedTime = "in $value ${if (value == 1L) unit.removeSuffix("s") else unit}"
                Log.d("CommandParser", "Relative reminder detected")
                Log.d("CommandParser", "Computed trigger time: $triggerMillis ms")
            }
        } else {
            // Check if user said "after 30" without units
            val partialRelativeRegex = Regex("\\b(in|after|later|within|next|later in) (\\d+)\\b")
            if (partialRelativeRegex.containsMatchIn(cleanText)) {
                missingTimeUnit = true
            }
        }

        // 1.2 Absolute time: "at X", "at X:Y", "at X pm"
        // 1.2 Absolute time: "at X", "at X:Y", "at X pm", "at X p.m."
        if (triggerMillis == null && !missingTimeUnit) {
            val absoluteRegex = Regex("at (\\d{1,2})(:(\\d{2}))?\\s*(pm|am|p\\.m\\.|a\\.m\\.|p\\.m|a\\.m)?")
            val matchAbsolute = absoluteRegex.find(cleanText)
            if (matchAbsolute != null) {
                var hour = matchAbsolute.groupValues[1].toInt()
                val minute = if (matchAbsolute.groupValues[3].isNotEmpty()) matchAbsolute.groupValues[3].toInt() else 0
                val amPmRaw = matchAbsolute.groupValues[4]
                val amPm = amPmRaw.replace(".", "")

                if (amPm == "pm" && hour < 12) hour += 12
                if (amPm == "am" && hour == 12) hour = 0
                
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
                calendar.set(java.util.Calendar.MINUTE, minute)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                
                if (calendar.timeInMillis <= now) {
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                
                triggerMillis = calendar.timeInMillis
                formattedTime = "at ${matchAbsolute.groupValues[1]}${if (matchAbsolute.groupValues[3].isNotEmpty()) ":${matchAbsolute.groupValues[3]}" else ""}${if (amPm.isNotEmpty()) " $amPm" else ""}"
            }
        }
        
        // 1.3 Natural variations: morning (9am), evening (6pm), night (9pm)
        if (triggerMillis == null && !missingTimeUnit) {
            val timeMap = mapOf("morning" to 9, "afternoon" to 14, "evening" to 18, "night" to 21)
            for ((key, hour) in timeMap) {
                if (cleanText.contains(key)) {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    if (calendar.timeInMillis <= now) calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    triggerMillis = calendar.timeInMillis
                    formattedTime = "this $key"
                    break
                }
            }
        }

        // 2. Repeat Pattern Detection
        var repeatIntervalMillis: Long? = null
        
        // 2.1 Daily patterns: "every day", "daily", "each day"
        val dailyRegex = Regex("\\b(every day|daily|each day)\\b")
        if (dailyRegex.find(cleanText) != null) {
            repeatIntervalMillis = 24 * 60 * 60 * 1000L // 24 hours
            Log.d("CommandParser", "Repeating reminder detected: DAILY")
            Log.d("CommandParser", "Repeat interval: $repeatIntervalMillis ms")
        }
        
        // 2.2 Weekly patterns: "every Monday", "every Tuesday", etc.
        val weeklyRegex = Regex("\\bevery (monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b")
        if (weeklyRegex.find(cleanText) != null) {
            repeatIntervalMillis = 7 * 24 * 60 * 60 * 1000L // 7 days
            Log.d("CommandParser", "Repeating reminder detected: WEEKLY")
            Log.d("CommandParser", "Repeat interval: $repeatIntervalMillis ms")
        }
        
        // 2.3 Weekdays pattern
        val weekdaysRegex = Regex("\\b(weekdays|every weekday)\\b")
        if (weekdaysRegex.find(cleanText) != null) {
            repeatIntervalMillis = 24 * 60 * 60 * 1000L // Daily (weekday logic handled in receiver)
            Log.d("CommandParser", "Repeating reminder detected: WEEKDAYS")
            Log.d("CommandParser", "Repeat interval: $repeatIntervalMillis ms")
        }
        
        // 2.4 Interval patterns: "every X minutes/hours"
        val intervalRegex = Regex("\\bevery (\\d+) (minutes|minute|mins|min|hours|hour|hrs|hr)\\b")
        val matchInterval = intervalRegex.find(cleanText)
        if (matchInterval != null) {
            val value = matchInterval.groupValues[1].toLong()
            val unit = matchInterval.groupValues[2]
            val multiplier = when {
                unit.startsWith("hour") || unit.startsWith("hr") -> 3600000L
                unit.startsWith("min") -> 60000L
                else -> 60000L
            }
            repeatIntervalMillis = value * multiplier
            Log.d("CommandParser", "Repeating reminder detected: INTERVAL")
            Log.d("CommandParser", "Repeat interval: $repeatIntervalMillis ms")
            
            // If interval pattern but no absolute time, set trigger to now + interval
            if (triggerMillis == null) {
                triggerMillis = now + repeatIntervalMillis
                formattedTime = "in ${matchInterval.groupValues[1]} ${matchInterval.groupValues[2]}"
                Log.d("CommandParser", "Computed trigger time: $triggerMillis ms")
            }
        }

        // 3. Message Extraction
        var message = cleanText
        // Remove time phrases (updated list to include all relative keywords)
        val timePhrases = listOf(
            Regex("\\bat \\d{1,2}(:\\d{2})?\\s*(pm|am)?\\b"),
            Regex("\\b(in|after|later|within|next|later in) \\d+ (minutes|minute|mins|min|hours|hour|hrs|hr|seconds|second|secs|sec)\\b"),
            Regex("\\b(in|after|later|within|next|later in) \\d+\\b"), // For partials
            Regex("\\btomorrow( at \\d{1,2}(:\\d{2})?\\s*(pm|am)?)?\\b"),
            Regex("\\b(morning|evening|night|afternoon)\\b")
        )
        timePhrases.forEach { regex ->
            message = message.replace(regex, "")
        }
        
        // Remove seeds and fillers
        val seeds = intentSeeds["REMINDER"] ?: emptyList()
        seeds.forEach { seed ->
            message = message.replace(Regex("\\b$seed\\b"), "")
        }
        
        val fillers = listOf("to", "me", "please", "jago", "about", "for")
        fillers.forEach { filler ->
            message = message.replace(Regex("\\b$filler\\b"), "")
        }
        
        message = message.trim().replace(Regex("\\s+"), " ")

        // Strict validation
        val missingTime = triggerMillis == null && !missingTimeUnit
        val missingMessage = message.isEmpty()

        return Command(
            type = CommandType.SET_REMINDER,
            messageBody = if (missingMessage) null else message,
            triggerMillis = triggerMillis,
            formattedTime = formattedTime,
            missingTime = missingTime,
            missingMessage = missingMessage,
            missingTimeUnit = missingTimeUnit,
            repeatIntervalMillis = repeatIntervalMillis
        )
    }

    private fun parseMath(text: String): Command? {
        // fast check for math keywords OR symbols
        val mathKeywords = listOf("plus", "add", "minus", "subtract", "into", "times", "multiply", "divided by", "over", "percent of", "power", "raised to", "square root", "bracket")
        val mathSymbols = listOf("+", "-", "*", "/", "%", "^", "(", ")")
        
        if (mathKeywords.none { text.contains(it) } && mathSymbols.none { text.contains(it) }) return null

        var expression = text
        // Normalize operators & brackets
        expression = expression.replace(" plus ", " + ")
            .replace(" add ", " + ")
            .replace(" minus ", " - ")
            .replace(" subtract ", " - ")
            .replace(" into ", " * ")
            .replace(" times ", " * ")
            .replace(" multiply ", " * ")
            .replace(" divided by ", " / ")
            .replace(" over ", " / ")
            .replace(" percent of ", " % ") 
            .replace(" power ", " ^ ")
            .replace(" raised to ", " ^ ")
            .replace("open bracket", "(")
            .replace("close bracket", ")")
            .replace("bracket open", "(")
            .replace("bracket close", ")")
        
        // Square root handling: "square root of 144" -> "sqrt(144)"
        if (expression.contains("square root of")) {
            val parts = expression.split("square root of")
            if (parts.size > 1) {
                val num = parts[1].trim().filter { it.isDigit() || it == '.' }
                if (num.isNotEmpty()) {
                    expression = expression.replace("square root of $num", "sqrt($num)")
                }
            }
        }
        
        // Basic validation: must contain numbers and operators
        if (expression.any { it.isDigit() } && (expression.any { "+-*/%^".contains(it) } || expression.contains("sqrt"))) {
            Log.d("CommandParser", "Math detected: $expression")
            return Command(CommandType.CALCULATE, messageBody = expression)
        }
        
        return null
    }

    private fun extractSchedulingTime(cleanText: String): Triple<Long?, String, String?> {
        // Returns: TriggerTimeMillis, CleanedText, FormattedTimeString
        val now = System.currentTimeMillis()
        var triggerMillis: Long? = null
        var formattedTime: String? = null
        var workingText = cleanText

        // 1. Relative Time: "in 5 mins", "after 1 hour"
        val relativeRegex = Regex("\\b(in|after|later|within|next|later in) (\\d+) (minutes|minute|mins|min|hours|hour|hrs|hr|seconds|second|secs|sec)\\b")
        val matchRelative = relativeRegex.find(workingText)
        
        if (matchRelative != null) {
            val value = matchRelative.groupValues[2].toLong()
            val unit = matchRelative.groupValues[3]
            val multiplier = when {
                unit.startsWith("hour") || unit.startsWith("hr") -> 3600000L
                unit.startsWith("min") -> 60000L
                unit.startsWith("sec") -> 1000L
                else -> 0L
            }
            if (multiplier > 0) {
                triggerMillis = now + (value * multiplier)
                formattedTime = "in $value $unit"
                workingText = workingText.replace(matchRelative.value, "")
            }
        }

        // 2. Absolute Time: "at 5pm", "at 17:00"
        // 2. Absolute Time: "at 5pm", "at 17:00", "at 5 p.m."
        if (triggerMillis == null) {
            // Regex to match pm, am, p.m., a.m., p.m, a.m
            val absoluteRegex = Regex("\\bat (\\d{1,2})(:(\\d{2}))?\\s*(pm|am|p\\.m\\.|a\\.m\\.|p\\.m|a\\.m)?\\b")
            val matchAbsolute = absoluteRegex.find(workingText)
            
            if (matchAbsolute != null) {
                var hour = matchAbsolute.groupValues[1].toInt()
                val minute = if (matchAbsolute.groupValues[3].isNotEmpty()) matchAbsolute.groupValues[3].toInt() else 0
                val amPmRaw = matchAbsolute.groupValues[4]
                val amPm = amPmRaw.replace(".", "") // Normalize p.m. -> pm

                if (amPm == "pm" && hour < 12) hour += 12
                if (amPm == "am" && hour == 12) hour = 0
                
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
                calendar.set(java.util.Calendar.MINUTE, minute)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                
                if (calendar.timeInMillis <= now) {
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                
                triggerMillis = calendar.timeInMillis
                formattedTime = matchAbsolute.value
                workingText = workingText.replace(matchAbsolute.value, "")
            }
        }
        
        // Cleanup extra spaces
        workingText = workingText.replace(Regex("\\s+"), " ").trim()
        
        return Triple(triggerMillis, workingText, formattedTime)
    }

    private fun isPronoun(word: String): Boolean {
        return word.lowercase() in listOf(
            "it", "that", "this", "them", "him", "her"
        )
    }

    private fun normalize(text: String): String {
        var clean = text.lowercase(Locale.getDefault())
        
        // Remove common fillers and assistant name
        val fillers = listOf("jago", "hey", "please", "can you", "could you", "would you", "kindly", "what is", "calculate") 
        fillers.forEach {
            clean = clean.replace(Regex("\\b$it\\b"), "")
        }

        // Remove punctuation (but keep math symbols and brackets)
        // Adjust regex to keep standard chars but allow typical math words
        return clean.replace(Regex("[^a-z0-9 '.+\\-*/%^():]"), "") 
                    .replace(Regex("\\s+"), " ")
                    .trim()
    }
}
