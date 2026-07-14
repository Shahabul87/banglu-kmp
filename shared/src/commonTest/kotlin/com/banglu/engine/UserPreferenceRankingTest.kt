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

        SmartEngineAdapter.onWordSelected("taka", "তাকা", explicitChoice = true)

        val after = SmartEngineAdapter.convertWord("taka")
        val suggestions = SmartEngineAdapter.getSuggestions("taka", 8).map { it.bengali }

        assertEquals("তাকা", after.bengali)
        assertEquals("তাকা", suggestions.first())
        assertTrue(suggestions.contains("টাকা"))
    }

    @Test
    fun learningSettingCanDisablePreferencePromotion() {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.onWordSelected("taka", "তাকা", explicitChoice = true)
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

        SmartEngineAdapter.onWordSelected("honeymoon", "honeymoon", explicitChoice = true)

        val result = SmartEngineAdapter.convertWord("honeymoon")
        val suggestions = SmartEngineAdapter.getSuggestions("honeymoon", 8).map { it.bengali }

        assertEquals("হানিমুন", result.bengali)
        assertEquals("হানিমুন", suggestions.first())
        assertTrue(suggestions.contains("honeymoon"))
    }

    @Test
    fun everydayLoanwordPrimaryBeatsLearnedEnglishSelection() {
        SmartEngineAdapter.initializeSync()

        SmartEngineAdapter.onWordSelected("practice", "practice", explicitChoice = true)
        SmartEngineAdapter.onWordSelected("scooter", "scooter", explicitChoice = true)
        SmartEngineAdapter.onWordSelected("remove", "remove", explicitChoice = true)
        SmartEngineAdapter.onWordSelected("possible", "possible", explicitChoice = true)

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

}
