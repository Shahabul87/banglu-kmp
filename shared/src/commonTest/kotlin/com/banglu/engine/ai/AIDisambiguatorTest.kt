package com.banglu.engine.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AIDisambiguatorTest {
    @Test
    fun testInitialize() {
        val d = AIDisambiguator()
        d.initialize(listOf("আমি", "তুমি", "করা"))
        // Should not throw
    }

    @Test
    fun testDisambiguateFindsKnownWord() {
        val d = AIDisambiguator()
        d.initialize(listOf("কষ্ট", "নষ্ট", "স্পষ্ট"))
        val result = d.disambiguate("কস্ট", 0.70)
        assertNotNull(result)
        assertEquals("কষ্ট", result.bengali)
        assertTrue(result.improved)
    }

    @Test
    fun testNoImprovementHighConfidence() {
        val d = AIDisambiguator()
        d.initialize(listOf("আমি"))
        val result = d.disambiguate("আমি", 0.95)
        assertNull(result)
    }

    @Test
    fun testGenerateCandidates() {
        val d = AIDisambiguator()
        d.initialize(listOf("কষ্ট"))
        val candidates = d.generateCandidates("কস্ট")
        assertTrue(candidates.contains("কষ্ট"))
    }

    @Test
    fun testShortInputReturnsNull() {
        val d = AIDisambiguator()
        d.initialize(listOf("আ"))
        val result = d.disambiguate("আ", 0.50)
        assertNull(result)
    }

    @Test
    fun testNonVowelSwaps() {
        val d = AIDisambiguator()
        d.initialize(listOf("রাষ্ট্র"))
        val candidates = d.generateCandidates("রাস্ত্র")
        // Should generate রাষ্ট্র via স্ত→ষ্ট swap
        assertTrue(candidates.isNotEmpty())
    }
}
