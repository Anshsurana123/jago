package com.example.jago.logic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TranslationClient {

    private const val TAG = "TranslationClient"

    // Free unofficial Google Translate endpoint — no API key needed
    // sl=auto → auto detect source language
    // tl=XX  → target language
    private const val BASE_URL = "https://translate.googleapis.com/translate_a/single" +
            "?client=gtx&sl=auto&dt=t"

    // Translate any text to Hindi Devanagari script
    suspend fun toDevanagari(text: String): String? = translate(text, "hi")

    // Translate any text to clean English
    suspend fun toEnglish(text: String): String? = translate(text, "en")

    private suspend fun translate(text: String, targetLang: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val urlString = "$BASE_URL&tl=$targetLang&q=$encoded"

                val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()

                    // Google Translate response is a nested JSON array
                    // Structure: [[[translated, original, ...], ...], ...]
                    // We extract all translated chunks and join them
                    val outerArray = JSONArray(response)
                    val translationsArray = outerArray.getJSONArray(0)

                    val result = StringBuilder()
                    for (i in 0 until translationsArray.length()) {
                        val chunk = translationsArray.getJSONArray(i)
                        if (!chunk.isNull(0)) {
                            result.append(chunk.getString(0))
                        }
                    }

                    val translated = result.toString().trim()
                    Log.d(TAG, "Translated '$text' to [$targetLang]: '$translated'")
                    translated.ifEmpty { null }

                } else {
                    Log.e(TAG, "Translation failed: ${connection.responseCode}")
                    null
                }

            } catch (e: Exception) {
                Log.e(TAG, "Translation error", e)
                null
            }
        }
}
