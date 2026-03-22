package com.banglu.engine.dictionary

import com.banglu.engine.types.PrefixResult
import com.banglu.engine.types.TrieEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PhoneticTrieTest {
    @Test
    fun testInsertAndExactMatch() {
        val trie = PhoneticTrie()
        trie.insert("ami", "আমি", 100)
        val results = trie.exactMatch("ami")
        assertEquals(1, results.size)
        assertEquals("আমি", results[0].bengali)
        assertEquals(100, results[0].frequency)
    }

    @Test
    fun testExactMatchMiss() {
        val trie = PhoneticTrie()
        trie.insert("ami", "আমি", 100)
        assertEquals(0, trie.exactMatch("tumi").size)
    }

    @Test
    fun testCaseInsensitive() {
        val trie = PhoneticTrie()
        trie.insert("Ami", "আমি", 100)
        assertEquals(1, trie.exactMatch("ami").size)
    }

    @Test
    fun testMultipleEntriesSameKey() {
        val trie = PhoneticTrie()
        trie.insert("kal", "কাল", 80)
        trie.insert("kal", "কেলে", 60)
        val results = trie.exactMatch("kal")
        assertEquals(2, results.size)
        assertEquals("কাল", results[0].bengali) // Higher frequency first
    }

    @Test
    fun testDuplicateUpdatesFrequency() {
        val trie = PhoneticTrie()
        trie.insert("ami", "আমি", 50)
        trie.insert("ami", "আমি", 100)
        val results = trie.exactMatch("ami")
        assertEquals(1, results.size)
        assertEquals(100, results[0].frequency)
    }

    @Test
    fun testPrefixSearch() {
        val trie = PhoneticTrie()
        trie.insert("ami", "আমি", 100)
        trie.insert("amar", "আমার", 95)
        trie.insert("amake", "আমাকে", 90)
        trie.insert("tumi", "তুমি", 99)
        val results = trie.prefixSearch("am", 10)
        assertEquals(3, results.size)
        assertTrue(results.all { it.phonetic.startsWith("am") })
    }

    @Test
    fun testHasPrefix() {
        val trie = PhoneticTrie()
        trie.insert("ami", "আমি", 100)
        assertTrue(trie.hasPrefix("a"))
        assertTrue(trie.hasPrefix("am"))
        assertTrue(trie.hasPrefix("ami"))
        assertFalse(trie.hasPrefix("t"))
    }

    @Test
    fun testFuzzyMatch() {
        val trie = PhoneticTrie()
        trie.insert("ami", "আমি", 100)
        trie.insert("amu", "আমু", 50)
        val results = trie.fuzzyMatch("amo", maxDistance = 1, limit = 5)
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun testFuzzyMatchAnchorFirst() {
        val trie = PhoneticTrie()
        trie.insert("ami", "আমি", 100)
        trie.insert("gami", "গামী", 50)
        val results = trie.fuzzyMatch("ami", maxDistance = 1, limit = 5, anchorFirst = true)
        assertTrue(results.all { it.phonetic.startsWith("a") })
    }

    @Test
    fun testClear() {
        val trie = PhoneticTrie()
        trie.insert("ami", "আমি", 100)
        assertEquals(1, trie.getKeyCount())
        trie.clear()
        assertEquals(0, trie.getKeyCount())
        assertEquals(0, trie.exactMatch("ami").size)
    }

    @Test
    fun testEmptyInput() {
        val trie = PhoneticTrie()
        assertEquals(0, trie.exactMatch("").size)
        assertEquals(0, trie.prefixSearch("").size)
    }

    @Test
    fun testPrefixSearchSortedByFrequency() {
        val trie = PhoneticTrie()
        trie.insert("ami", "আমি", 100)
        trie.insert("amar", "আমার", 95)
        trie.insert("amake", "আমাকে", 90)
        val results = trie.prefixSearch("am", 10)
        assertEquals("আমি", results[0].bengali)
        assertEquals("আমার", results[1].bengali)
    }
}
