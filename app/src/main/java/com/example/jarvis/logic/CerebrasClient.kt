package com.example.jarvis.logic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object CerebrasClient {
    private const val TAG = "CerebrasClient"
    private const val API_KEY = "csk-vtprwjpk3ehprrcmexxtmtcwk2pt5e2f5mvy4h9944ntj35x"
    private const val ENDPOINT = "https://api.cerebras.ai/v1/chat/completions"

    suspend fun askAI(query: String, useSmartModel: Boolean): String? = withContext(Dispatchers.IO) {
        val modelName = if (useSmartModel) "gpt-oss-120b" else "llama-3.3-70b"
        
        try {
            val url = URL(ENDPOINT)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $API_KEY")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 10000

            val jsonBody = JSONObject().apply {
                put("model", modelName)
                val messages = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", query)
                    })
                }
                put("messages", messages)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val jsonResponse = JSONObject(response)
                val content = jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                
                content
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.e(TAG, "Cerebras API Error: Code $responseCode, Message: $errorStream")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cerebras API Error", e)
            null
        }
    }
}
