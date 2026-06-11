package com.banglu.engine

import com.banglu.engine.dictionary.SeedData
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserPreferenceRankingTest {

    @AfterTest
    fun tearDown() {
        SmartEngineAdapter.reset()
    }

    @Test
    fun explicitSelectionPromotesAmbiguousSpelling() {
        SmartEngineAdapter.initializeSync()

        val before = SmartEngineAdapter.convertWord("taka")
        assertEquals("টাকা", before.bengali)

        SmartEngineAdapter.onWordSelected("taka", "তাকা")

        val after = SmartEngineAdapter.convertWord("taka")
        val suggestions = SmartEngineAdapter.getSuggestions("taka", 8).map { it.bengali }

        assertEquals("তাকা", after.bengali)
        assertEquals("তাকা", suggestions.first())
        assertTrue(suggestions.contains("টাকা"))
    }

    @Test
    fun learningSettingCanDisablePreferencePromotion() {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.onWordSelected("taka", "তাকা")
        SmartEngineAdapter.configureLearning(enabled = false, personalDictionary = true)

        assertEquals("টাকা", SmartEngineAdapter.convertWord("taka").bengali)
    }

    @Test
    fun customConversionWinsForUserAuthoredTypingStyle() {
        SmartEngineAdapter.initializeSync()

        SmartEngineAdapter.addCustomConversion("ottadhunik", "অত্যাধুনিক")

        val result = SmartEngineAdapter.convertWord("ottadhunik")
        val suggestions = SmartEngineAdapter.getSuggestions("ottadhunik", 8).map { it.bengali }

        assertEquals("অত্যাধুনিক", result.bengali)
        assertEquals("অত্যাধুনিক", suggestions.first())
    }

    @Test
    fun customConversionStillWinsWhenTypingLearningIsDisabled() {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.configureLearning(enabled = false, personalDictionary = true)

        SmartEngineAdapter.addCustomConversion("ottadhunik", "অত্যাধুনিক")

        val result = SmartEngineAdapter.convertWord("ottadhunik")
        val suggestions = SmartEngineAdapter.getSuggestions("ottadhunik", 8).map { it.bengali }

        assertEquals("অত্যাধুনিক", result.bengali)
        assertEquals("অত্যাধুনিক", suggestions.first())
    }

    @Test
    fun curatedLoanwordPrimaryBeatsLearnedEnglishSelection() {
        SmartEngineAdapter.initializeSync()

        SmartEngineAdapter.onWordSelected("honeymoon", "honeymoon")

        val result = SmartEngineAdapter.convertWord("honeymoon")
        val suggestions = SmartEngineAdapter.getSuggestions("honeymoon", 8).map { it.bengali }

        assertEquals("হানিমুন", result.bengali)
        assertEquals("হানিমুন", suggestions.first())
        assertTrue(suggestions.contains("honeymoon"))
    }

    @Test
    fun everydayLoanwordPrimaryBeatsLearnedEnglishSelection() {
        SmartEngineAdapter.initializeSync()

        SmartEngineAdapter.onWordSelected("practice", "practice")
        SmartEngineAdapter.onWordSelected("scooter", "scooter")
        SmartEngineAdapter.onWordSelected("remove", "remove")
        SmartEngineAdapter.onWordSelected("possible", "possible")

        val practiceSuggestions = SmartEngineAdapter.getSuggestions("practice", 8).map { it.bengali }
        val scooterSuggestions = SmartEngineAdapter.getSuggestions("scooter", 8).map { it.bengali }
        val removeSuggestions = SmartEngineAdapter.getSuggestions("remove", 8).map { it.bengali }
        val possibleSuggestions = SmartEngineAdapter.getSuggestions("possible", 8).map { it.bengali }

        assertEquals("প্রাকটিস", SmartEngineAdapter.convertWord("practice").bengali)
        assertEquals("স্কুটার", SmartEngineAdapter.convertWord("scooter").bengali)
        assertEquals("রিমুভ", SmartEngineAdapter.convertWord("remove").bengali)
        assertEquals("পসিবল", SmartEngineAdapter.convertWord("possible").bengali)
        assertEquals("প্রাকটিস", practiceSuggestions.first())
        assertEquals("স্কুটার", scooterSuggestions.first())
        assertEquals("রিমুভ", removeSuggestions.first())
        assertEquals("পসিবল", possibleSuggestions.first())
        assertTrue(practiceSuggestions.contains("practice"))
        assertTrue(scooterSuggestions.contains("scooter"))
        assertTrue(removeSuggestions.contains("remove"))
        assertTrue(possibleSuggestions.contains("possible"))
    }

    @Test
    fun bulkPreferencePromotionAcrossManyDictionaryWords() {
        val phoneticKeys = SeedData.SEED_DICTIONARY
            .asSequence()
            .flatMap { it.phonetics.asSequence() }
            .map { it.lowercase().trim() }
            .filter { it.length in 2..12 && it.all { ch -> ch in 'a'..'z' } }
            .distinct()
            .take(1200)
            .toList()

        var exercised = 0
        val failures = mutableListOf<String>()

        for (key in phoneticKeys) {
            SmartEngineAdapter.reset()
            SmartEngineAdapter.initializeSync()

            val suggestions = SmartEngineAdapter.getSuggestions(key, 8)
                .map { it.bengali }
                .distinct()

            val primary = suggestions.firstOrNull() ?: continue
            val alternate = suggestions.drop(1).firstOrNull() ?: continue

            SmartEngineAdapter.onWordSelected(key, alternate)
            val after = SmartEngineAdapter.convertWord(key).bengali
            val ranked = SmartEngineAdapter.getSuggestions(key, 8).map { it.bengali }

            if (after != alternate || ranked.firstOrNull() != alternate || !ranked.contains(primary)) {
                failures.add("$key expected selected '$alternate' first, got primary '$after', suggestions=$ranked")
            }
            exercised++
            if (exercised >= 250) break
        }

        assertTrue(exercised >= 200, "Expected at least 200 ambiguous dictionary words, exercised $exercised")
        assertTrue(failures.isEmpty(), failures.take(10).joinToString("\n"))
    }
}
