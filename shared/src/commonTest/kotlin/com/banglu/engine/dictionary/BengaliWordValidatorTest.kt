package com.banglu.engine.dictionary

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class BengaliWordValidatorTest {

    @Test
    fun testIsValid() {
        val v = BengaliWordValidator()
        v.loadWords(listOf("আমি", "তুমি", "সে", "আমরা"))
        assertTrue(v.isValid("আমি"))
        assertFalse(v.isValid("xyz"))
    }

    @Test
    fun testFindByPrefix() {
        val v = BengaliWordValidator()
        v.loadWords(listOf("আম", "আমি", "আমার", "আমরা", "বাড়ি"))
        val results = v.findByPrefix("আম", 10)
        assertEquals(4, results.size)
        assertTrue(results.all { it.startsWith("আম") })
    }

    @Test
    fun testFindByPrefixRespectsLimit() {
        val v = BengaliWordValidator()
        v.loadWords(listOf("আম", "আমি", "আমার", "আমরা", "আমাদের"))
        val results = v.findByPrefix("আম", 2)
        assertEquals(2, results.size)
    }

    @Test
    fun testFindByPrefixNoMatch() {
        val v = BengaliWordValidator()
        v.loadWords(listOf("আম", "আমি"))
        val results = v.findByPrefix("বা", 10)
        assertTrue(results.isEmpty())
    }

    @Test
    fun testGetSize() {
        val v = BengaliWordValidator()
        v.loadWords(listOf("আমি", "তুমি"))
        assertEquals(2, v.getSize())
    }

    @Test
    fun testIsLoaded() {
        val v = BengaliWordValidator()
        assertFalse(v.isLoaded())
        v.loadWords(listOf("আমি"))
        assertTrue(v.isLoaded())
    }

    @Test
    fun testFrequencies() {
        val v = BengaliWordValidator()
        v.loadWords(listOf("আমি"))
        v.loadFrequencies(mapOf("আমি" to 100))
        assertEquals(100, v.getFrequency("আমি"))
        assertEquals(0, v.getFrequency("তুমি"))
    }

    @Test
    fun testHasFrequencyData() {
        val v = BengaliWordValidator()
        assertFalse(v.hasFrequencyData())
        v.loadFrequencies(mapOf("আমি" to 100))
        assertTrue(v.hasFrequencyData())
    }

    @Test
    fun testGetSortedWords() {
        val v = BengaliWordValidator()
        v.loadWords(listOf("তুমি", "আমি", "সে"))
        val sorted = v.getSortedWords()
        assertEquals(3, sorted.size)
        // Should be lexicographically sorted
        for (i in 0 until sorted.size - 1) {
            assertTrue(sorted[i] <= sorted[i + 1])
        }
    }

    @Test
    fun testLoadWordsReplacesOld() {
        val v = BengaliWordValidator()
        v.loadWords(listOf("আমি", "তুমি"))
        assertEquals(2, v.getSize())
        v.loadWords(listOf("সে"))
        assertEquals(1, v.getSize())
        assertFalse(v.isValid("আমি"))
        assertTrue(v.isValid("সে"))
    }

    @Test
    fun testEmptyDictionary() {
        val v = BengaliWordValidator()
        v.loadWords(emptyList())
        assertTrue(v.isLoaded())
        assertEquals(0, v.getSize())
        assertFalse(v.isValid("আমি"))
        assertTrue(v.findByPrefix("আ", 10).isEmpty())
    }
}
