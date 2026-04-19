// Required Notice: Copyright 2026 Ansh. (https://github.com/Anshsurana123/jago)
package com.example.jago.logic

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import com.example.jago.logic.ContactResolver.Contact

@RunWith(MockitoJUnitRunner::class)
class ContactResolverTest {

    @Mock
    private lateinit var mockContext: Context

    private val testContacts = listOf(
        Contact("Mummy", "12345"),
        Contact("Rishabh", "67890"),
        Contact("Rishabh ki Mummy", "11223"),
        Contact("Rahul Sharma", "33445"),
        Contact("Rahul Verma", "55667"),
        Contact("Steve Jobs", "99887"),
        Contact("Tim Cook", "77665")
    )

    @Test
    fun testExactMatchPriority() {
        // "Mummy" Should match "Mummy" EXACTLY and NOT "Rishabh ki Mummy"
        val resolver = ContactResolver(mockContext)
        val result = resolver.resolveContact("Mummy", testContacts)
        
        assertTrue("Expected Success", result is ContactResolver.ResolutionResult.Success)
        val success = result as ContactResolver.ResolutionResult.Success
        assertEquals("Mummy", success.contact.name)
    }

    @Test
    fun testStartsWithMatch() {
        // "Rishabh" should match "Rishabh" (Exact) over "Rishabh ki Mummy" (StartsWith)
        // Wait, "Rishabh" matches "Rishabh" EXACTLY. So stage 1 wins.
        val resolver = ContactResolver(mockContext)
        val result = resolver.resolveContact("Rishabh", testContacts)
        
        assertTrue("Expected Success (Exact)", result is ContactResolver.ResolutionResult.Success)
        assertEquals("Rishabh", (result as ContactResolver.ResolutionResult.Success).contact.name)
    }

    @Test
    fun testStartsWithAmbiguity() {
        // "Rahul" matches "Rahul Sharma" and "Rahul Verma" (StartsWith) if "Rahul" (exact) doesn't exist.
        // But here "Rahul" (exact) is NOT in list, only "Rahul Sharma" and "Rahul Verma".
        // So "Rahul" should trigger StartsWith on both.
        val resolver = ContactResolver(mockContext)
        val result = resolver.resolveContact("Rahul", testContacts)

        assertTrue("Expected Ambiguity", result is ContactResolver.ResolutionResult.Ambiguous)
        val ambiguous = result as ContactResolver.ResolutionResult.Ambiguous
        assertEquals(2, ambiguous.matches.size)
    }

    @Test
    fun testContainsMatch() {
        // "Sharma" should match "Rahul Sharma" (Contains)
        val resolver = ContactResolver(mockContext)
        val result = resolver.resolveContact("Sharma", testContacts)

        assertTrue("Expected Success (Contains)", result is ContactResolver.ResolutionResult.Success)
        assertEquals("Rahul Sharma", (result as ContactResolver.ResolutionResult.Success).contact.name)
    }

    @Test
    fun testFuzzyMatch() {
        // "Mammy" (typo) should match "Mummy" (Fuzzy)
        val resolver = ContactResolver(mockContext)
        val result = resolver.resolveContact("Mammy", testContacts)

        assertTrue("Expected Success (Fuzzy)", result is ContactResolver.ResolutionResult.Success)
        assertEquals("Mummy", (result as ContactResolver.ResolutionResult.Success).contact.name)
    }
}
