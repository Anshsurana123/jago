package com.example.jago.logic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TranslationClient {

    private const val TAG = "TranslationClient"

    // Google Input Tools — batch transliteration endpoint
    // Accepts multiple words in one request, returns best Devanagari for each
    private const val INPUT_TOOLS_URL =
        "https://inputtools.google.com/request?itc=hi-t-i0-und&num=1&cp=0&cs=1&ie=utf-8&oe=utf-8&app=demopage"

    // Transliterate full Hinglish sentence → Devanagari
    // Sends ALL words in a single batch request for better context
    suspend fun toDevanagari(text: String): String? = withContext(Dispatchers.IO) {
        try {
            val words = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (words.isEmpty()) return@withContext null

            // Build batch request body — send all words at once as JSON array
            // Format: {"text": ["word1", "word2", ...], "language": "hi"}
            val textArray = JSONArray().apply {
                words.forEach { put(it) }
            }
            val body = JSONObject().apply {
                put("text", textArray)
                put("language", "hi")
            }.toString()

            // POST request with all words
            val connection = (URL(INPUT_TOOLS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0")
                doOutput = true
                connectTimeout = 6000
                readTimeout = 6000
            }

            connection.outputStream.use { it.write(body.toByteArray()) }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "Raw transliteration response: $response")

                // Response: ["SUCCESS", [["word1", ["देव1",...]], ["word2", ["देव2",...]]]]
                val arr = JSONArray(response)
                if (arr.getString(0) != "SUCCESS") {
                    Log.e(TAG, "Transliteration failed: ${arr.getString(0)}")
                    return@withContext null
                }

                val results = arr.getJSONArray(1)
                val devanagariWords = mutableListOf<String>()

                for (i in 0 until results.length()) {
                    val wordResult = results.getJSONArray(i)
                    val suggestions = wordResult.getJSONArray(1)
                    // Take top suggestion, fall back to original word if empty
                    val devanagari = if (suggestions.length() > 0) suggestions.getString(0)
                                     else words[i]
                    devanagariWords.add(devanagari)
                }

                val result = devanagariWords.joinToString(" ")
                Log.d(TAG, "Transliterated: '$text' → '$result'")
                result

            } else {
                Log.e(TAG, "HTTP error: ${connection.responseCode}")
                // Fallback: try GET request word by word
                fallbackWordByWord(words)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Transliteration error", e)
            null
        }
    }

    // Fallback if batch POST fails — GET request per word
    private fun fallbackWordByWord(words: List<String>): String? {
        return try {
            val devanagariWords = words.map { word ->
                try {
                    val encoded = URLEncoder.encode(word, "UTF-8")
                    val url = "$INPUT_TOOLS_URL&text=$encoded"
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                        connectTimeout = 3000
                        readTimeout = 3000
                    }
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val resp = conn.inputStream.bufferedReader().readText()
                        val arr = JSONArray(resp)
                        if (arr.getString(0) == "SUCCESS") {
                            arr.getJSONArray(1).getJSONArray(0).getJSONArray(1).getString(0)
                        } else word
                    } else word
                } catch (e: Exception) { word }
            }
            devanagariWords.joinToString(" ")
        } catch (e: Exception) {
            null
        }
    }

    // Translate Hinglish → clean English using Google Translate
    suspend fun toEnglish(text: String): String? = withContext(Dispatchers.IO) {
    try {
        // Step 1 — convert Hinglish → Devanagari (already works perfectly)
        val devanagari = toDevanagari(text)
        if (devanagari.isNullOrEmpty()) {
            Log.e(TAG, "Devanagari conversion failed for: $text")
            return@withContext null
        }
        Log.d(TAG, "Step 1 Devanagari: '$devanagari'")

        // Step 2 — translate Devanagari → English (Google handles this perfectly)
        val encoded = URLEncoder.encode(devanagari, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single" +
                  "?client=gtx&sl=hi&tl=en&dt=t&q=$encoded"

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
            connectTimeout = 5000
            readTimeout = 5000
        }

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().readText()
            val arr = JSONArray(response)
            val chunks = arr.getJSONArray(0)
            val result = StringBuilder()
            for (i in 0 until chunks.length()) {
                val chunk = chunks.getJSONArray(i)
                if (!chunk.isNull(0)) result.append(chunk.getString(0))
            }
            val translated = result.toString().trim()
            Log.d(TAG, "Step 2 English: '$text' → '$translated'")
            translated.ifEmpty { null }
        } else {
            Log.e(TAG, "English translation HTTP error: ${connection.responseCode}")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "toEnglish error", e)
        null
    }
 }
} 
