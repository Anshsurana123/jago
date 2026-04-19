// Required Notice: Copyright 2026 Ansh. (https://github.com/Anshsurana123/jago)
package com.example.jago.logic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TranslationClient {

    private const val TAG = "TranslationClient"
    private const val INPUT_TOOLS_URL =
        "https://inputtools.google.com/request?itc=hi-t-i0-und&num=1&cp=0&cs=1&ie=utf-8&oe=utf-8&app=demopage"

    // Transliterate Hinglish → Devanagari word by word via GET
    suspend fun toDevanagari(text: String): String? = withContext(Dispatchers.IO) {
        try {
            val words = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (words.isEmpty()) return@withContext null

            // Fire ALL word requests in parallel — much faster than sequential
            val devanagariWords = words.map { word ->
                async {
                    try {
                        val encoded = URLEncoder.encode(word, "UTF-8")
                        val url = "$INPUT_TOOLS_URL&text=$encoded"
                        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "Mozilla/5.0")
                            connectTimeout = 4000
                            readTimeout = 4000
                        }
                        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                            val resp = conn.inputStream.bufferedReader().readText()
                            val arr = JSONArray(resp)
                            if (arr.getString(0) == "SUCCESS") {
                                arr.getJSONArray(1).getJSONArray(0).getJSONArray(1).getString(0)
                            } else word
                        } else word
                    } catch (e: Exception) {
                        Log.e(TAG, "Word transliteration failed for '$word'", e)
                        word
                    }
                }
            }.awaitAll() // wait for ALL parallel requests to complete

            val result = devanagariWords.joinToString(" ")
            Log.d(TAG, "Transliterated: '$text' → '$result'")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Transliteration error", e)
            null
        }
    }

    // Translate Hinglish → English
    // Step 1: Hinglish → Devanagari (inputtools)
    // Step 2: Devanagari → English (Google Translate sl=hi works perfectly here)
    suspend fun toEnglish(text: String): String? = withContext(Dispatchers.IO) {
        try {
            // Step 1
            val devanagari = toDevanagari(text)
            if (devanagari.isNullOrEmpty()) {
                Log.e(TAG, "Devanagari step failed for: $text")
                return@withContext null
            }
            Log.d(TAG, "Step 1 Devanagari: '$devanagari'")

            // Step 2
            val encoded = URLEncoder.encode(devanagari, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single" +
                      "?client=gtx&sl=hi&tl=en&dt=t&q=$encoded"

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader().readText()
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
                Log.e(TAG, "Google Translate error: ${conn.responseCode}")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "toEnglish error", e)
            null
        }
    }
}