package com.banglu.engine

import com.banglu.engine.types.ResolutionSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OovLearningTest {

    @AfterTest
    fun tearDown() {
        // Same reset mechanism as UserPreferenceRankingTest: the adapter is a
        // singleton, so every test must drop the engine + preference maps.
        SmartEngineAdapter.reset()
    }

    @Test
    fun committedOovWordBecomesDictionaryBackedNextTime() {
        SmartEngineAdapter.initializeSync()
        val engine = SmartEngineAdapter.getEngine()
        // Tiny "480K" validator arms the commit gate (same seam as CommitGateTest).
        engine.loadValidatorWords(listOf("আমি"))

        val first = engine.convertWord("rafsan")
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, first.source)

        SmartEngineAdapter.onWordSelected("rafsan", first.bengali, learnAsWord = true)

        val second = engine.convertWord("rafsan")
        assertEquals(first.bengali, second.bengali)
        assertEquals(ResolutionSource.DICTIONARY, second.source)
    }

    @Test
    fun plainSelectionDoesNotAddDictionaryWord() {
        SmartEngineAdapter.initializeSync()
        val engine = SmartEngineAdapter.getEngine()
        engine.loadValidatorWords(listOf("আমি"))

        val first = engine.convertWord("rafsan")
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, first.source)

        SmartEngineAdapter.onWordSelected("rafsan", first.bengali)

        // A plain selection only records an adapter-level ranking preference
        // (selectedPreferenceMap, consumed by applyUserPreference and
        // rerankSuggestionsByPreference on the ADAPTER conversion path) — it
        // never mutates the engine dictionary. The raw engine conversion
        // therefore stays gate-floored at CLEAN_TRANSLITERATION.
        val second = engine.convertWord("rafsan")
        assertEquals(first.bengali, second.bengali)
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, second.source)
    }
}
