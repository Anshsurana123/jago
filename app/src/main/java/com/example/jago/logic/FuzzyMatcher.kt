// Required Notice: Copyright 2026 Ansh. (https://github.com/Anshsurana123/jago)
package com.example.jago.logic

import java.util.Locale
import kotlin.math.min

object FuzzyMatcher {

    /**
     * Calculates the Levenshtein distance between two strings.
     * Lower score = higher similarity.
     */
    fun calculateDistance(s1: String, s2: String): Int {
        val str1 = s1.lowercase(Locale.getDefault()).trim()
        val str2 = s2.lowercase(Locale.getDefault()).trim()

        if (str1 == str2) return 0
        if (str1.isEmpty()) return str2.length
        if (str2.isEmpty()) return str1.length

        val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }

        for (i in 0..str1.length) dp[i][0] = i
        for (j in 0..str2.length) dp[0][j] = j

        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[str1.length][str2.length]
    }

    /**
     * Finds the best match from a list of candidates based on a maximum distance threshold.
     */
    fun <T> findBestMatch(
        query: String,
        candidates: List<T>,
        nameExtractor: (T) -> String,
        threshold: Int = 2
    ): T? {
        if (candidates.isEmpty()) return null
        
        var minDistance = Int.MAX_VALUE
        var bestMatch: T? = null

        for (candidate in candidates) {
            val name = nameExtractor(candidate)
            val distance = calculateDistance(query, name)
            
            if (distance < minDistance) {
                minDistance = distance
                bestMatch = candidate
            }
        }

        return if (minDistance <= threshold) bestMatch else null
    }
}
