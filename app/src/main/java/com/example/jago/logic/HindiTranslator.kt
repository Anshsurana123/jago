// Required Notice: Copyright 2026 Ansh. (https://github.com/Anshsurana123/jago)
package com.example.jago.logic

object HindiTranslator {

    // Maps Hindi phrases/words → English equivalents
    // Covers Hinglish too (mixed Hindi-English that Indian users naturally say)
    private val hindiToEnglish = mapOf(

        // === CALLS ===
        "call karo" to "call",
        "call karna" to "call",
        "call lagao" to "call",
        "phone karo" to "call",
        "phone lagao" to "call",
        "baat karna" to "call",
        "phone karna" to "call",
        "ghanta bajao" to "call",

        // === WHATSAPP ===
        "whatsapp karo" to "open whatsapp",
        "whatsapp bhejo" to "send whatsapp message",
        "whatsapp message bhejo" to "send whatsapp message",
        "message bhejo" to "message",
        "message karo" to "message",
        "msg bhejo" to "message",
        "msg karo" to "message",

        // === FLASHLIGHT ===
        "torch jalao" to "flashlight on",
        "torch band karo" to "flashlight off",
        "torch on karo" to "flashlight on",
        "torch off karo" to "flashlight off",
        "light jalao" to "flashlight on",
        "light band karo" to "flashlight off",
        "torchlight jalao" to "flashlight on",
        "torchlight band karo" to "flashlight off",

        // === VOLUME ===
        "awaaz badhao" to "volume up",
        "volume badhao" to "volume up",
        "awaaz kam karo" to "volume down",
        "volume kam karo" to "volume down",
        "awaaz band karo" to "volume mute",
        "awaaz mute karo" to "volume mute",
        "silent karo" to "silent mode",
        "silent mode karo" to "silent mode",

        // === BRIGHTNESS ===
        "screen tej karo" to "brightness increase",
        "brightness badhao" to "brightness increase",
        "screen dim karo" to "brightness decrease",
        "brightness kam karo" to "brightness decrease",
        "andhera kam karo" to "brightness increase",
        "chamak badhao" to "brightness increase",
        "chamak kam karo" to "brightness decrease",

        // === BATTERY ===
        "battery kitni hai" to "battery check",
        "battery check karo" to "battery check",
        "charge kitna hai" to "battery check",
        "battery batao" to "battery check",

        // === LOCK ===
        "phone lock karo" to "lock device",
        "lock karo" to "lock device",
        "screen band karo" to "lock device",

        // === WIFI ===
        "wifi kholo" to "open wifi settings",
        "wifi settings kholo" to "open wifi settings",
        "wifi on karo" to "open wifi settings",

        // === BLUETOOTH ===
        "bluetooth kholo" to "open bluetooth settings",
        "bluetooth on karo" to "open bluetooth settings",
        "bluetooth settings kholo" to "open bluetooth settings",

        // === OPEN APP ===
        "kholo" to "open",
        "chalao" to "open",
        "shuru karo" to "open",
        "band karo" to "close",

        // === MEDIA ===
        "gaana chalao" to "play music",
        "gaana bajao" to "play music",
        "music chalao" to "play music",
        "music bajao" to "play music",
        "rok do" to "pause",
        "ruko" to "pause",
        "pause karo" to "pause",
        "agla gaana" to "next song",
        "next karo" to "next",
        "pichla gaana" to "previous song",
        "wapas karo" to "previous",

        // === SCREENSHOT ===
        "screenshot lo" to "take screenshot",
        "screen capture karo" to "take screenshot",

        // === DND ===
        "disturb mat karo" to "do not disturb",
        "dnd on karo" to "do not disturb on",
        "dnd off karo" to "do not disturb off",

        // === PHOTO ===
        "photo lo" to "take photo",
        "tasveer lo" to "take photo",
        "selfie lo" to "take photo",

        // === REMINDER ===
        "yaad dilao" to "remind me",
        "yaad kara do" to "remind me",
        "reminder lagao" to "remind me",
        "mujhe yaad dilao" to "remind me",

        // === ALARM ===
        "alarm lagao" to "set alarm",
        "alarm set karo" to "set alarm",
        "subah uthana" to "set alarm",
        "neend se uthana" to "set alarm",

        // === SEARCH ===
        "dhundo" to "search",
        "search karo" to "search",
        "khojo" to "search",

        // === CALCULATE ===
        "calculate karo" to "calculate",
        "hisaab lagao" to "calculate",
        "jod" to "plus",
        "ghatao" to "minus",
        "guna" to "times",
        "bhago" to "divided by",

        // === TIME WORDS ===
        "abhi" to "now",
        "kal" to "tomorrow",
        "aaj" to "today",
        "subah" to "morning",
        "shaam" to "evening",
        "raat" to "night",
        "minute mein" to "in minutes",
        "ghante mein" to "in hours",
        "minute baad" to "in minutes",
        "ghante baad" to "in hours",
        "baje" to "o clock",

        // === COMMON FILLERS (just remove these) ===
        "zara" to "",
        "please" to "",
        "jago" to "",
        "mujhe" to "",
        "mera" to "",
        "meri" to "",
        "thoda" to "",
        "yaar" to "",
        "bhai" to ""
    )

    fun translate(input: String): String {
        if (!isHindi(input)) return input

        var text = input.lowercase().trim()

        // Message body markers — everything AFTER these stays untouched
        val messageMarkers = listOf(
            // Full command phrases (highest priority, check first)
            "whatsapp message bhejo",
            "whatsapp bhejo",
            "whatsapp karo",
            "message bhejo",
            "message karo",
            "msg bhejo",
            "msg karo",
            "bolo"
        )

        // Find if there's a message marker in the text
        var commandPart = text
        var messagePart = ""

        for (marker in messageMarkers.sortedByDescending { it.length }) {
            val idx = text.indexOf(marker)
            if (idx != -1) {
                // Split — translate command, keep message raw
                commandPart = text.substring(0, idx + marker.length)
                messagePart = text.substring(idx + marker.length).trim()
                break
            }
        }

        // If no marker found but text starts with "message/msg [name]",
        // protect everything after the second word as message body
        if (messagePart.isEmpty()) {
            val msgNounRegex = Regex("^(message|msg|whatsapp)\\s+(\\S+)\\s+(.+)$")
            val msgMatch = msgNounRegex.find(commandPart)
            if (msgMatch != null) {
                val action = msgMatch.groupValues[1]      // "message"
                val contact = msgMatch.groupValues[2]     // "ansh"
                val body = msgMatch.groupValues[3]        // "kal milte hain" — protect this
                commandPart = "$action $contact"
                messagePart = body
                android.util.Log.d("HindiTranslator", "Noun-style message detected: contact=$contact, body=$body")
            }
        }

        // Only translate the command part
        val sortedMap = hindiToEnglish.entries.sortedByDescending { it.key.length }
        for ((hindi, english) in sortedMap) {
            commandPart = commandPart.replace(
                Regex("\\b${Regex.escape(hindi)}\\b"), english
            )
        }

        // Remove "ko" only from command part (indirect object marker, meaningless to parser)
        commandPart = commandPart.replace(Regex("\\bko\\b"), "").replace(Regex("\\s+"), " ").trim()

        // Rejoin — message body stays exactly as spoken
        val result = if (messagePart.isNotEmpty()) {
            "$commandPart $messagePart"
        } else {
            commandPart
        }

        return result.replace(Regex("\\s+"), " ").trim()
    }

    // Returns true if the text contains any Hindi characters or known Hindi words
    fun isHindi(text: String): Boolean {
        // Check for Devanagari script
        if (text.any { it.code in 0x0900..0x097F }) return true
        // Check for common Hinglish words
        val hindiMarkers = listOf("karo", "karna", "jalao", "badhao", "batao",
            "kholo", "chalao", "lagao", "bhejo", "baje", "yaad",
            "awaaz", "gaana", "subah", "shaam", "raat", "abhi",
            "bolo", "padhao", "sunao", "dilao", "bhago")
        // Use word-boundary matching to avoid false positives on English words
        // e.g. "domain" contains "mai", "band" matches "rock band"
        val lower = text.lowercase()
        return hindiMarkers.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lower) }
    }
}
