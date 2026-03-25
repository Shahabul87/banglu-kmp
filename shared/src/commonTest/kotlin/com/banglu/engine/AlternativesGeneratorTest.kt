package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertTrue

class AlternativesGeneratorTest {

    private fun createEngine(): SmartEngine {
        val engine = SmartEngine()
        engine.initializeSync()
        return engine
    }

    @Test
    fun testDiphthongSplitOiToI() {
        val engine = createEngine()
        val alts = engine.generateDiphthongAlternatives("বৈ")
        assertTrue(alts.any { it.bengali == "বই" }, "Should generate ৈ→ই split")
    }

    @Test
    fun testDiphthongSplitOuToU() {
        val engine = createEngine()
        val alts = engine.generateDiphthongAlternatives("বৌ")
        assertTrue(alts.any { it.bengali == "বউ" }, "Should generate ৌ→উ split")
    }

    @Test
    fun testInitialVowelSwapOToA() {
        val engine = createEngine()
        val alts = engine.generateInitialVowelAlternatives("অনেক")
        assertTrue(alts.any { it.bengali == "ওনেক" }, "Should generate অ↔ও swap")
    }

    @Test
    fun testInitialVowelSwapAToO() {
        val engine = createEngine()
        val alts = engine.generateInitialVowelAlternatives("ওনেক")
        assertTrue(alts.any { it.bengali == "অনেক" }, "Should generate ও↔অ swap")
    }

    @Test
    fun testAmbiguousCharAlternatives() {
        val engine = createEngine()
        val alts = engine.generateAmbiguousCharAlternatives("তালা")
        assertTrue(alts.any { it.bengali == "টালা" }, "Should generate ত↔ট swap")
    }

    @Test
    fun testGetAlternativesCombinesAllSources() {
        val engine = createEngine()
        val alts = engine.getAlternatives("test", "অনেক")
        assertTrue(alts.isNotEmpty(), "Should generate some alternatives")
    }
}
