package com.banglu.engine.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SmartDictionaryTest {
    private fun createDict(): SmartDictionary {
        val dict = SmartDictionary()
        dict.initialize()
        return dict
    }

    @Test fun testInitializeLoadsSeedData() {
        val dict = createDict()
        val stats = dict.getStats()
        assertTrue(stats.totalEntries > 100)
    }

    @Test fun testLookupExact() {
        val dict = createDict()
        val results = dict.lookup("ami")
        assertTrue(results.isNotEmpty())
        assertEquals("আমি", results[0].bengali)
    }

    @Test fun testLookupVariant() {
        val dict = createDict()
        // "amii" is a variant of "ami" in the seed data
        val results = dict.lookup("amii")
        assertTrue(results.isNotEmpty())
    }

    @Test fun testPrefixSearch() {
        val dict = createDict()
        val results = dict.searchByPrefix("am", 10)
        assertTrue(results.size >= 2)  // ami, amar, amake, amra, etc.
    }

    @Test fun testBestMatch() {
        val dict = createDict()
        val best = dict.bestMatch("ami")
        assertNotNull(best)
        assertEquals("আমি", best.bengali)
    }

    @Test fun testAddMapping() {
        val dict = createDict()
        dict.addMapping("testword", "টেস্ট", 80)
        val results = dict.lookup("testword")
        assertTrue(results.isNotEmpty())
        assertEquals("টেস্ট", results[0].bengali)
    }

    @Test fun testNormalizePhonetic() {
        // "bhalow" -> ow→ou -> "bhalou" -> ou$ → o -> "bhalo"
        assertEquals("bhalo", SmartDictionary.normalizePhonetic("bhalow"))
        assertEquals("i", SmartDictionary.normalizePhonetic("ee"))
        assertEquals("u", SmartDictionary.normalizePhonetic("oo"))
    }

    @Test fun testGetPhoneticForBengali() {
        val dict = createDict()
        val phonetic = dict.getPhoneticForBengali("আমি")
        assertNotNull(phonetic)
        assertEquals("ami", phonetic)
    }

    @Test fun testCacheHit() {
        val dict = createDict()
        dict.lookup("ami")  // First call - populate cache
        val results = dict.lookup("ami")  // Second call - cache hit
        assertEquals("আমি", results[0].bengali)
    }

    @Test fun testBangladesh() {
        val dict = createDict()
        val results = dict.lookup("bangladesh")
        assertTrue(results.isNotEmpty())
        assertEquals("বাংলাদেশ", results[0].bengali)
    }

    @Test fun testHas() {
        val dict = createDict()
        assertTrue(dict.has("ami"))
        assertTrue(!dict.has("xyznotexist"))
    }
}
