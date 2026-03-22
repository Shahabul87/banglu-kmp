package com.banglu.engine

import com.banglu.engine.types.ResolutionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartEngineTest {

    private fun createEngine(): SmartEngine {
        val engine = SmartEngine()
        engine.initializeSync()
        return engine
    }

    @Test
    fun testConvertAmi() {
        val engine = createEngine()
        val result = engine.convertWord("ami")
        assertEquals("আমি", result.bengali)
        assertTrue(result.confidence >= 0.85)
    }

    @Test
    fun testConvertTumi() {
        val engine = createEngine()
        assertEquals("তুমি", engine.convertWord("tumi").bengali)
    }

    @Test
    fun testConvertBangladesh() {
        val engine = createEngine()
        assertEquals("বাংলাদেশ", engine.convertWord("bangladesh").bengali)
    }

    @Test
    fun testConvertBhalo() {
        val engine = createEngine()
        val result = engine.convertWord("bhalo")
        assertEquals("ভালো", result.bengali)
    }

    @Test
    fun testConvertKhabar() {
        val engine = createEngine()
        assertEquals("খাবার", engine.convertWord("khabar").bengali)
    }

    @Test
    fun testParseMultiWord() {
        val engine = createEngine()
        val result = engine.parse("ami tumi")
        assertTrue(result.contains("আমি"), "Result '$result' should contain আমি")
        assertTrue(result.contains("তুমি"), "Result '$result' should contain তুমি")
    }

    @Test
    fun testPreservesSpaces() {
        val engine = createEngine()
        val result = engine.parse("ami  tumi")
        assertTrue(result.contains("  "), "Result '$result' should preserve double spaces")
    }

    @Test
    fun testGetSuggestions() {
        val engine = createEngine()
        val suggestions = engine.getSuggestions("am", 6)
        assertTrue(suggestions.isNotEmpty(), "Suggestions for 'am' should not be empty")
    }

    @Test
    fun testCacheHit() {
        val engine = createEngine()
        engine.convertWord("ami")
        val result = engine.convertWord("ami")
        assertEquals("আমি", result.bengali)
    }

    @Test
    fun testEmptyInput() {
        val engine = createEngine()
        assertEquals("", engine.parse(""))
    }

    @Test
    fun testConvertKoshto() {
        val engine = createEngine()
        // "koshto" is in seed dictionary as কষ্ট
        val result = engine.convertWord("koshto")
        assertEquals("কষ্ট", result.bengali)
    }

    @Test
    fun testConvertEkhon() {
        val engine = createEngine()
        assertEquals("এখন", engine.convertWord("ekhon").bengali)
    }

    @Test
    fun testConvertDhaka() {
        val engine = createEngine()
        assertEquals("ঢাকা", engine.convertWord("dhaka").bengali)
    }

    @Test
    fun testConvertSundor() {
        val engine = createEngine()
        assertEquals("সুন্দর", engine.convertWord("sundor").bengali)
    }

    @Test
    fun testEmptyWordConversion() {
        val engine = createEngine()
        val result = engine.convertWord("")
        assertEquals("", result.bengali)
        assertEquals(0.0, result.confidence)
    }

    @Test
    fun testDictionarySourceForKnownWords() {
        val engine = createEngine()
        val result = engine.convertWord("ami")
        assertEquals(ResolutionSource.DICTIONARY, result.source)
    }

    @Test
    fun testClearCache() {
        val engine = createEngine()
        engine.convertWord("ami")
        engine.clearCache()
        // Should still work after cache clear (just re-computes)
        val result = engine.convertWord("ami")
        assertEquals("আমি", result.bengali)
    }

    @Test
    fun testAddWord() {
        val engine = createEngine()
        engine.addWord("testword", "টেস্টওয়ার্ড", 80)
        val result = engine.convertWord("testword")
        assertEquals("টেস্টওয়ার্ড", result.bengali)
    }

    @Test
    fun testParseEmptyString() {
        val engine = createEngine()
        assertEquals("", engine.parse(""))
    }

    @Test
    fun testParseSingleWord() {
        val engine = createEngine()
        val result = engine.parse("ami")
        assertEquals("আমি", result)
    }

    @Test
    fun testSuggestionsContainPrimary() {
        val engine = createEngine()
        val suggestions = engine.getSuggestions("ami", 6)
        assertTrue(suggestions.any { it.bengali == "আমি" }, "Suggestions should contain আমি")
    }

    @Test
    fun testSuggestionsSorted() {
        val engine = createEngine()
        val suggestions = engine.getSuggestions("am", 6)
        for (i in 1 until suggestions.size) {
            assertTrue(
                suggestions[i - 1].confidence >= suggestions[i].confidence,
                "Suggestions should be sorted by confidence descending"
            )
        }
    }

    @Test
    fun testConvertWordCaseInsensitive() {
        val engine = createEngine()
        val result = engine.convertWord("AMI")
        assertEquals("আমি", result.bengali)
    }

    @Test
    fun testPatternConversionFallback() {
        val engine = createEngine()
        // A word NOT in the seed dictionary should still be converted via patterns
        val result = engine.convertWord("xyz")
        assertTrue(result.bengali.isNotEmpty(), "Pattern engine should produce non-empty output")
    }
}
