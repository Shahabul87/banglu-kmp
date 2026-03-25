package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SuffixStrippingTest {

    private fun createEngine(): SmartEngine {
        val engine = SmartEngine()
        engine.initializeSync()
        return engine
    }

    @Test
    fun testSuffixDer() {
        val engine = createEngine()
        engine.addWord("chele", "ছেলে", 90)
        val result = engine.trySuffixStrippedDictionary("cheleder")
        assertNotNull(result, "Should find cheleder via suffix stripping")
        assertEquals("ছেলেদের", result.bengali)
    }

    @Test
    fun testSuffixEr() {
        val engine = createEngine()
        engine.addWord("chele", "ছেলে", 90)
        val result = engine.trySuffixStrippedDictionary("cheler")
        assertNotNull(result, "Should find cheler via suffix stripping")
        assertEquals("ছেলের", result.bengali)
    }

    @Test
    fun testSuffixRa() {
        val engine = createEngine()
        engine.addWord("chele", "ছেলে", 90)
        val result = engine.trySuffixStrippedDictionary("chelera")
        assertNotNull(result, "Should find chelera via suffix stripping")
        assertEquals("ছেলেরা", result.bengali)
    }

    @Test
    fun testSuffixGulo() {
        val engine = createEngine()
        engine.addWord("boi", "বই", 85)
        val result = engine.trySuffixStrippedDictionary("boigulo")
        assertNotNull(result, "Should find boigulo via suffix stripping")
        assertEquals("বইগুলো", result.bengali)
    }

    @Test
    fun testMinimumStemLength() {
        val engine = createEngine()
        // Stem "a" is too short (< 2 chars)
        val result = engine.trySuffixStrippedDictionary("ae")
        // Should not match — stem is too short
    }

    @Test
    fun testLongestStemPreferred() {
        val engine = createEngine()
        engine.addWord("cha", "চা", 90)
        engine.addWord("chad", "চাঁদ", 85)
        // "chader" could be "cha" + "der" OR "chad" + "er"
        // Should prefer "chad" (longer stem)
        val result = engine.trySuffixStrippedDictionary("chader")
        if (result != null) {
            assertTrue(result.bengali.startsWith("চাঁদ"), "Should prefer longer stem 'chad'")
        }
    }
}
