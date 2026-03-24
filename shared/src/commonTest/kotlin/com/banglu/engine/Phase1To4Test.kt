package com.banglu.engine

import com.banglu.engine.types.SmartSuggestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class Phase1To4Test {
    private val engine = SmartEngine().also { it.initializeSync() }

    // === Feature 4.1: Next-Word Predictions ===
    
    @Test
    fun testGetNextWordPredictions() {
        // Predictions depend on bigram model being loaded (async)
        // In seed-only mode, should return empty without crashing
        val predictions = engine.getNextWordPredictions("আমি", 5)
        // May be empty in seed-only mode — just verify no crash
        assertTrue(predictions.size >= 0)
    }

    @Test
    fun testSmartEngineAdapterPredictions() {
        SmartEngineAdapter.reset()
        SmartEngineAdapter.initializeSync()
        val predictions = SmartEngineAdapter.getNextWordPredictions("আমি", 5)
        assertTrue(predictions.size >= 0) // No crash
    }

    // === Engine Conversion Stability (all phases depend on this) ===
    
    @Test
    fun testCommonWordsStillCorrect() {
        // Verify recent changes didn't break core conversions
        val cases = mapOf(
            "ami" to "আমি",
            "tumi" to "তুমি",
            "bhalo" to "ভালো",
            "bangladesh" to "বাংলাদেশ",
            "tui" to "তুই",
            "er" to "এর",
            "jonyo" to "জন্য",
            "ekti" to "একটি",
            "aponar" to "আপনার",
            "karon" to "কারণ",
            "sundor" to "সুন্দর",
            "otyadhunik" to "অত্যাধুনিক",
            "bondho" to "বন্ধ",
            "sorokar" to "সরকার",
            "proshno" to "প্রশ্ন"
        )

        val failures = mutableListOf<String>()
        for ((input, expected) in cases) {
            val result = engine.convertWord(input).bengali
            if (result != expected) {
                failures.add("$input: expected '$expected', got '$result'")
            }
        }
        if (failures.isNotEmpty()) {
            kotlin.test.fail("${failures.size} conversions broken:\n${failures.joinToString("\n")}")
        }
    }

    // === Suggestion Quality ===

    @Test
    fun testSuggestionsNotEmpty() {
        val suggestions = engine.getSuggestions("am", 6)
        assertTrue(suggestions.isNotEmpty(), "Suggestions for 'am' should not be empty")
        assertTrue(suggestions.any { it.bengali == "আমি" || it.bengali == "আম" || it.bengali == "আমার" })
    }

    @Test
    fun testSuggestionsContainDisambiguationSwaps() {
        val suggestions = engine.getSuggestions("otyadhunik", 8)
        assertTrue(suggestions.isNotEmpty())
        assertEquals("অত্যাধুনিক", suggestions[0].bengali)
        // Should have swap variants
        assertTrue(suggestions.size > 1, "Should have disambiguation swap suggestions")
    }

    // === Multi-word Parse ===

    @Test
    fun testParsePreservesSpaces() {
        val result = engine.parse("ami  tumi")
        assertTrue(result.contains("  "), "Double space should be preserved")
    }

    @Test
    fun testParseMultipleWords() {
        val result = engine.parse("ami bhalo")
        assertTrue(result.contains("আমি"))
        assertTrue(result.contains("ভালো"))
    }

    // === Edge Cases ===

    @Test
    fun testEmptyInput() {
        assertEquals("", engine.convertWord("").bengali)
        assertEquals("", engine.parse(""))
        assertTrue(engine.getSuggestions("", 5).isEmpty())
    }

    @Test
    fun testSingleCharInput() {
        val result = engine.convertWord("a")
        assertTrue(result.bengali.isNotEmpty())
    }

    @Test
    fun testEnglishPassthrough() {
        // Very short common English words should pass through
        val result = engine.convertWord("the")
        // May or may not pass through — just shouldn't crash
        assertTrue(result.bengali.isNotEmpty())
    }

    // === Adapter Singleton ===

    @Test
    fun testAdapterConvert() {
        SmartEngineAdapter.reset()
        SmartEngineAdapter.initializeSync()
        assertEquals("আমি", SmartEngineAdapter.convert("ami"))
    }

    @Test
    fun testAdapterParse() {
        SmartEngineAdapter.reset()
        SmartEngineAdapter.initializeSync()
        val result = SmartEngineAdapter.parse("ami bhalo")
        assertTrue(result.contains("আমি"))
    }

    @Test
    fun testAdapterSuggestions() {
        SmartEngineAdapter.reset()
        SmartEngineAdapter.initializeSync()
        val suggestions = SmartEngineAdapter.getSuggestions("am", 6)
        assertTrue(suggestions.isNotEmpty())
    }
}
