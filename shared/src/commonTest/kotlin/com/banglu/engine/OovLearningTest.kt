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

        SmartEngineAdapter.onWordSelected("rafsan", first.bengali, learnAsWord = true, explicitChoice = true)

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

        SmartEngineAdapter.onWordSelected("rafsan", first.bengali, explicitChoice = true)

        // A plain selection only records an adapter-level ranking preference
        // (selectedPreferenceMap, consumed by applyUserPreference and
        // rerankSuggestionsByPreference on the ADAPTER conversion path) — it
        // never mutates the engine dictionary. The raw engine conversion
        // therefore stays gate-floored at CLEAN_TRANSLITERATION.
        val second = engine.convertWord("rafsan")
        assertEquals(first.bengali, second.bengali)
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, second.source)
    }

    /**
     * When learning is disabled (but personal dictionary is enabled),
     * learnAsWord=true must be a no-op: the commit gate is open but the
     * learning guard short-circuits before engine.addCustomConversion is
     * reached, so the word stays CLEAN_TRANSLITERATION on the next lookup.
     */
    @Test
    fun learningDisabledBlocksOovLearning() {
        SmartEngineAdapter.configureLearning(enabled = false, personalDictionary = true)
        SmartEngineAdapter.initializeSync()
        val engine = SmartEngineAdapter.getEngine()
        engine.loadValidatorWords(listOf("আমি"))

        val first = engine.convertWord("rafsan")
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, first.source)

        SmartEngineAdapter.onWordSelected("rafsan", first.bengali, learnAsWord = true, explicitChoice = true)

        val second = engine.convertWord("rafsan")
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, second.source)
    }

    /**
     * When the personal dictionary is disabled (but learning is enabled),
     * learnAsWord=true must be a no-op: the guard in onWordSelected returns
     * early before the engine dictionary is mutated, so the word remains
     * CLEAN_TRANSLITERATION on the next lookup.
     */
    @Test
    fun personalDictionaryDisabledBlocksOovLearning() {
        SmartEngineAdapter.configureLearning(enabled = true, personalDictionary = false)
        SmartEngineAdapter.initializeSync()
        val engine = SmartEngineAdapter.getEngine()
        engine.loadValidatorWords(listOf("আমি"))

        val first = engine.convertWord("rafsan")
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, first.source)

        SmartEngineAdapter.onWordSelected("rafsan", first.bengali, learnAsWord = true, explicitChoice = true)

        val second = engine.convertWord("rafsan")
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, second.source)
    }
}
