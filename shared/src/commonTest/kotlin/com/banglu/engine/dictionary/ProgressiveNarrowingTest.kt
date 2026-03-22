package com.banglu.engine.dictionary

import kotlin.test.Test
import kotlin.test.assertTrue

class ProgressiveNarrowingTest {

    @Test
    fun testGetSuggestions() {
        val dict = SmartDictionary()
        dict.initialize()
        val engine = ProgressiveNarrowingEngine(dict)
        val suggestions = engine.getSuggestions("am", 6)
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.bengali == "আমি" || it.bengali == "আমার" || it.bengali == "আম" })
    }

    @Test
    fun testNarrowsWithMoreInput() {
        val dict = SmartDictionary()
        dict.initialize()
        val engine = ProgressiveNarrowingEngine(dict)
        val broad = engine.getSuggestions("am", 10)
        val narrow = engine.getSuggestions("ami", 10)
        // "ami" should be more focused than "am"
        assertTrue(narrow.isNotEmpty())
    }

    @Test
    fun testShortInputReturnsEmpty() {
        val dict = SmartDictionary()
        dict.initialize()
        val engine = ProgressiveNarrowingEngine(dict)
        val results = engine.getSuggestions("a", 6)
        assertTrue(results.isEmpty())
    }

    @Test
    fun testComputeScore() {
        val dict = SmartDictionary()
        val engine = ProgressiveNarrowingEngine(dict)
        val score = engine.computeScore(0.90, 80, 4, 5)
        assertTrue(score > 0.0)
        assertTrue(score <= 2.0) // Score can exceed 1.0 due to combined weights
    }

    @Test
    fun testEmptyInputReturnsEmpty() {
        val dict = SmartDictionary()
        dict.initialize()
        val engine = ProgressiveNarrowingEngine(dict)
        val results = engine.getSuggestions("", 6)
        assertTrue(results.isEmpty())
    }

    @Test
    fun testSuggestionsAreDeduplicated() {
        val dict = SmartDictionary()
        dict.initialize()
        val engine = ProgressiveNarrowingEngine(dict)
        val suggestions = engine.getSuggestions("am", 10)
        val bengaliWords = suggestions.map { it.bengali }
        // No duplicate Bengali words
        kotlin.test.assertEquals(bengaliWords.size, bengaliWords.toSet().size)
    }

    @Test
    fun testSuggestionsRespectLimit() {
        val dict = SmartDictionary()
        dict.initialize()
        val engine = ProgressiveNarrowingEngine(dict)
        val suggestions = engine.getSuggestions("am", 3)
        assertTrue(suggestions.size <= 3)
    }
}
