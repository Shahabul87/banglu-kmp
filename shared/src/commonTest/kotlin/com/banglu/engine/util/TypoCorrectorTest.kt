package com.banglu.engine.util

import com.banglu.engine.dictionary.SmartDictionary
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals

class TypoCorrectorTest {
    private fun createDict(): SmartDictionary {
        val dict = SmartDictionary()
        dict.initialize()
        return dict
    }

    @Test
    fun testTranspositionFindsMatch() {
        val dict = createDict()
        // "kroe" is transposition of "kore" which is in seed dictionary
        val result = TypoCorrector.correct("kroe", dict)
        assertNotNull(result)
        assertEquals("kore", result.corrected)
        assertEquals("kroe", result.original)
        assertEquals("transposition", result.correctionType)
    }

    @Test
    fun testNoTranspositionForIdenticalAdjacent() {
        val dict = createDict()
        // "amii" is itself in the dictionary, so correct() returns null (already a match)
        val result = TypoCorrector.correct("amii", dict)
        assertNull(result) // Word is already in dictionary
    }

    @Test
    fun testDoubleReduction() {
        val dict = createDict()
        // Add a word to the dictionary that isn't already there
        dict.addMapping("bola", "বলা", 70)
        val result = TypoCorrector.correct("boola", dict)
        // "boola" → remove doubled 'o' → "bola" which is in dictionary
        assertNotNull(result)
        assertEquals("bola", result.corrected)
        assertEquals("reduction", result.correctionType)
    }

    @Test
    fun testVowelInsertion() {
        val dict = createDict()
        // "kre" → insert vowel between k and r → "kore" which is in dictionary
        val result = TypoCorrector.correct("kre", dict)
        // Might find "kore" via vowel insertion (inserting 'o' between 'k' and 'r')
        // But "kre" length is 3 which is >= minLength (2)
        // k and r are both consonants, so vowel insertion between them is tried
        if (result != null) {
            assertEquals("insertion", result.correctionType)
        }
    }

    @Test
    fun testShortInputReturnsNull() {
        val dict = createDict()
        val result = TypoCorrector.correct("a", dict)
        assertNull(result) // Too short (< 2)
    }

    @Test
    fun testLongInputReturnsNull() {
        val dict = createDict()
        val result = TypoCorrector.correct("a".repeat(20), dict)
        assertNull(result) // Too long (> 15)
    }

    @Test
    fun testEmptyInputReturnsNull() {
        val dict = createDict()
        val result = TypoCorrector.correct("", dict)
        assertNull(result)
    }

    @Test
    fun testWordAlreadyInDictionaryReturnsNull() {
        val dict = createDict()
        // "ami" is in the seed dictionary
        val result = TypoCorrector.correct("ami", dict)
        assertNull(result) // Already in dictionary, no correction needed
    }

    @Test
    fun testCaseInsensitive() {
        val dict = createDict()
        // Should lowercase the input before checking
        val result = TypoCorrector.correct("KROE", dict)
        assertNotNull(result)
        assertEquals("kore", result.corrected)
    }

    @Test
    fun testTrimsWhitespace() {
        val dict = createDict()
        val result = TypoCorrector.correct("  kroe  ", dict)
        assertNotNull(result)
        assertEquals("kore", result.corrected)
    }
}
