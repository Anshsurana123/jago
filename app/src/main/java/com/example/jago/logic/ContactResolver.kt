package com.example.jago.logic

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
            Log.e("Jago", "Permission denied reading contacts", e)
        }
        return uniqueContacts.values.toList()
    }



    private fun findBestMatch(target: String, contacts: List<Contact>): ResolutionResult {
        val normalizedTarget = target.lowercase().trim()
        val targetLower = normalizedTarget
        Log.d("ContactResolver", "Resolving contact for: $targetLower (originally: $target)")

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

        // 3.5 First Name Contains Match
        // "hemesh" should match contact "Hemesh Sharma" even if contains fails
        val firstNameMatches = contacts.filter { contact ->
            val firstName = contact.name.lowercase().split(" ").firstOrNull() ?: ""
            firstName.isNotEmpty() && firstName.contains(targetLower)
        }
        if (firstNameMatches.isNotEmpty()) {
            if (firstNameMatches.size == 1) {
                Log.d("ContactResolver", "Stage 3.5 (FirstName Contains): Found -> ${firstNameMatches.first().name}")
                return ResolutionResult.Success(firstNameMatches.first())
            } else {
                Log.d("ContactResolver", "Stage 3.5 (FirstName Contains): Ambiguous")
                return ResolutionResult.Ambiguous(firstNameMatches)
            }
        }

        // 4. Fuzzy Match — tries full name AND first name separately
        // Indian users say first name only, so "hemesh" should match "Himesh Sharma"
        if (target.length >= 3) {
            val threshold = (target.length / 3).coerceAtLeast(1).coerceAtMost(3)

            val fuzzyMatches = contacts.filter { contact ->
                // Try full name
                val fullDist = FuzzyMatcher.calculateDistance(targetLower, contact.name.lowercase())
                if (fullDist <= threshold) return@filter true

                // Try just first name (before first space)
                val firstName = contact.name.lowercase().split(" ").firstOrNull() ?: ""
                if (firstName.length >= 3) {
                    val firstNameDist = FuzzyMatcher.calculateDistance(targetLower, firstName)
                    if (firstNameDist <= threshold) return@filter true
                }

                false
            }

            if (fuzzyMatches.isNotEmpty()) {
                // If multiple fuzzy matches, pick the one with smallest distance
                val best = fuzzyMatches.minByOrNull { contact ->
                    minOf(
                        FuzzyMatcher.calculateDistance(targetLower, contact.name.lowercase()),
                        FuzzyMatcher.calculateDistance(
                            targetLower,
                            contact.name.lowercase().split(" ").firstOrNull() ?: contact.name.lowercase()
                        )
                    )
                }!!
                Log.d("ContactResolver", "Stage 4 (Fuzzy+FirstName): Found -> ${best.name}")
                return ResolutionResult.Success(best)
            }
        }

        Log.d("ContactResolver", "No matches found in any stage")
        return ResolutionResult.NoMatch
    }
}
