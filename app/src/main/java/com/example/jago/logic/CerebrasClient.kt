// Required Notice: Copyright 2026 Ansh. (https://github.com/Anshsurana123/jago)
package com.example.jago.logic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

object CerebrasClient {
    private const val TAG = "CerebrasClient"
    private val API_KEY: String
        get() = com.example.jago.BuildConfig.CEREBRAS_API_KEY
    private const val ENDPOINT = "https://api.cerebras.ai/v1/chat/completions"
    private const val MODEL = "llama3.1-8b"

    suspend fun askAI(query: String, useSmartModel: Boolean = true): String? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(15000L) {
                try {
                    val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Authorization", "Bearer $API_KEY")
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")
                        doOutput = true
                        connectTimeout = 8000
                        readTimeout = 10000
                    }

                    val body = JSONObject().apply {
                     put("model", MODEL)
                     put("messages", JSONArray().apply {
                      put(JSONObject().apply {
                     put("role", "system")
                  put("content", "You are a voice assistant. Always respond in 1-2 short sentences maximum. Be direct and concise. No long explanations.")
                  })
                put(JSONObject().apply {
                 put("role", "user")
                 put("content", query)
             })
            })
              put("max_tokens", 100)  // ← short responses only
             }.toString()

                    connection.outputStream.use { it.write(body.toByteArray()) }

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream
                            .bufferedReader()
                            .use(BufferedReader::readText)
                        JSONObject(response)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    } else {
                        val err = connection.errorStream?.bufferedReader()?.readText()
                        Log.e(TAG, "Error ${connection.responseCode}: $err")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Cerebras failed", e)
                    null
                }
            }
        }

    // Specialized function for message translation
    suspend fun translateMessage(text: String, toHindi: Boolean): String? {
        val prompt = if (toHindi) {
            "Convert this Hinglish text to proper Hindi Devanagari script. Return ONLY the Hindi text, no explanation: $text"
        } else {
            "Convert this Hinglish text to proper grammatical English. Return ONLY the English text, no explanation: $text"
        }
        return askAI(prompt)
    }
}