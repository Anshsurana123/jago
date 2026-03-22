package com.example.jarvis.logic

import android.content.Context
import android.provider.ContactsContract
import android.database.Cursor
import android.util.Log

class ContactResolver(private val context: Context) {

    data class Contact(val name: String, val phoneNumber: String)

    sealed class ResolutionResult {
        data class Success(val contact: Contact) : ResolutionResult()
        data class Ambiguous(val matches: List<Contact>) : ResolutionResult()
        object NoMatch : ResolutionResult()
    }

    fun resolveContact(name: String): ResolutionResult {
        val contacts = getAllContacts()
        return findBestMatch(name, contacts)
    }

    // For testing purposes
    fun resolveContact(name: String, contacts: List<Contact>): ResolutionResult {
        return findBestMatch(name, contacts)
    }

    private fun getAllContacts(): List<Contact> {
        val uniqueContacts = mutableMapOf<Long, Contact>()
        val contentResolver = context.contentResolver
        try {
            val cursor: Cursor? = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                null,
                null,
                null
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                
                var rawCount = 0

                while (it.moveToNext()) {
                    rawCount++
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else null
                    val number = if (numberIndex >= 0) it.getString(numberIndex) else null
                    val contactId = if (idIndex >= 0) it.getLong(idIndex) else -1L

                    if (name != null && number != null && contactId != -1L) {
                        if (!uniqueContacts.containsKey(contactId)) {
                            uniqueContacts[contactId] = Contact(name, number)
                        }
                    }
                }
                Log.d("ContactResolver", "Loaded contacts: Raw=$rawCount, Unique=${uniqueContacts.size}")
            }
        } catch (e: SecurityException) {
            Log.e("Jarvis", "Permission denied reading contacts", e)
        }
        return uniqueContacts.values.toList()
    }

    private val aliases = mapOf(
        "mum" to "mummy",
        "mom" to "mummy",
        "maa" to "mummy"
    )

    private fun findBestMatch(target: String, contacts: List<Contact>): ResolutionResult {
        val normalizedTarget = target.lowercase().trim()
        val expandedTarget = aliases[normalizedTarget] ?: normalizedTarget
        
        if (expandedTarget != normalizedTarget) {
            Log.d("ContactResolver", "Alias expansion: '$normalizedTarget' -> '$expandedTarget'")
        }
        
        val targetLower = expandedTarget.lowercase()
        Log.d("ContactResolver", "Resolving contact for: $expandedTarget (originally: $target)")

        // 1. Exact Match (Highest Priority)
        val exactMatches = contacts.filter { it.name.lowercase() == targetLower }
        if (exactMatches.isNotEmpty()) {
            if (exactMatches.size == 1) {
                Log.d("ContactResolver", "Stage 1 (Exact): Found 1 match -> ${exactMatches.first().name}")
                return ResolutionResult.Success(exactMatches.first())
            } else {
                Log.d("ContactResolver", "Stage 1 (Exact): Found ${exactMatches.size} matches -> Ambiguous")
                return ResolutionResult.Ambiguous(exactMatches)
            }
        }

        // 2. Starts With Match (Second Priority)
        val startsWithMatches = contacts.filter { it.name.lowercase().startsWith(targetLower) }
        if (startsWithMatches.isNotEmpty()) {
            if (startsWithMatches.size == 1) {
                Log.d("ContactResolver", "Stage 2 (StartsWith): Found 1 match -> ${startsWithMatches.first().name}")
                return ResolutionResult.Success(startsWithMatches.first())
            } else {
                Log.d("ContactResolver", "Stage 2 (StartsWith): Found ${startsWithMatches.size} matches -> Ambiguous")
                return ResolutionResult.Ambiguous(startsWithMatches)
            }
        }

        // 3. Contains Match (Low Priority)
        val containsMatches = contacts.filter { it.name.lowercase().contains(targetLower) }
        if (containsMatches.isNotEmpty()) {
             if (containsMatches.size == 1) {
                Log.d("ContactResolver", "Stage 3 (Contains): Found 1 match -> ${containsMatches.first().name}")
                return ResolutionResult.Success(containsMatches.first())
            } else {
                Log.d("ContactResolver", "Stage 3 (Contains): Found ${containsMatches.size} matches -> Ambiguous")
                return ResolutionResult.Ambiguous(containsMatches)
            }
        }

        // 4. Fuzzy Match (Fallback - Levenshtein)
        // Only if target is at least 3 chars to avoid noise
        if (target.length >= 3) {
            val fuzzyMatches = contacts.filter { 
                val dist = FuzzyMatcher.calculateDistance(target, it.name)
                // Threshold: allow 1 error per 4 characters roughly, or max 2 errors
                val threshold = (target.length / 4).coerceAtLeast(1).coerceAtMost(2)
                dist <= threshold
            }
            
            if (fuzzyMatches.isNotEmpty()) {
                if (fuzzyMatches.size == 1) {
                    Log.d("ContactResolver", "Stage 4 (Fuzzy): Found 1 match -> ${fuzzyMatches.first().name}")
                    return ResolutionResult.Success(fuzzyMatches.first())
                } else {
                    Log.d("ContactResolver", "Stage 4 (Fuzzy): Found ${fuzzyMatches.size} matches -> Ambiguous")
                    return ResolutionResult.Ambiguous(fuzzyMatches)
                }
            }
        }

        Log.d("ContactResolver", "No matches found in any stage")
        return ResolutionResult.NoMatch
    }
}
