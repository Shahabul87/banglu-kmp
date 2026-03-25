package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertTrue

class SuggestionPipelineParityTest {

    @Test
    fun testOkarVariantInSuggestions() {
        val engine = SmartEngine()
        engine.initializeSync()
        engine.addWord("bhalo", "ভালো", 95)
        val suggestions = engine.getSuggestions("bhalo")
        assertTrue(suggestions.size >= 2, "Should have primary + ো variant")
    }
}
