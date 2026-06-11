package com.banglu.engine

import com.banglu.engine.platform.InMemoryStorage
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserPreferencePersistenceJvmTest {

    @AfterTest
    fun tearDown() {
        SmartEngineAdapter.reset()
    }

    @Test
    fun persistedSuggestionChoiceCanBeDisabledWithoutMutatingDictionary() = runBlocking {
        val storage = InMemoryStorage()
        storage.saveLearnedWord("taka", "তাকা", 94)

        SmartEngineAdapter.initialize(storage)

        assertEquals("তাকা", SmartEngineAdapter.convertWord("taka").bengali)

        SmartEngineAdapter.configureLearning(enabled = false, personalDictionary = true)

        assertEquals("টাকা", SmartEngineAdapter.convertWord("taka").bengali)
    }

    @Test
    fun persistedCustomConversionWinsEvenWhenTypingLearningIsDisabled() = runBlocking {
        val storage = InMemoryStorage()
        storage.saveLearnedWord("ottadhunik", "অত্যাধুনিক", 120)

        SmartEngineAdapter.initialize(storage)
        SmartEngineAdapter.configureLearning(enabled = false, personalDictionary = true)

        assertEquals("অত্যাধুনিক", SmartEngineAdapter.convertWord("ottadhunik").bengali)
    }
}
