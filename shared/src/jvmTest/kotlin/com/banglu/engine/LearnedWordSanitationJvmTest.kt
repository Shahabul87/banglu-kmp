package com.banglu.engine

import com.banglu.engine.platform.InMemoryStorage
import com.banglu.engine.rules.CleanTransliterator
import com.banglu.engine.types.ResolutionSource
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * F5b: learned-word sanitation on load.
 *
 * Pre-gate builds persisted garbage learned entries (kkkkx -> ক্কক্কক্স@98)
 * that override the Engine v3 commit gate through the adapter preference maps.
 * On load, sub-custom (<120) entries are honored only when the engine's gate
 * oracle trusts them (real word, clean floor of their own key, or approved
 * composition). Untrusted entries are skipped — NOT deleted from storage —
 * and the check fails open when no validator/store oracle is available.
 */
class LearnedWordSanitationJvmTest {

    @AfterTest
    fun tearDown() {
        // Adapter is a singleton: drop engine + preference maps between tests.
        SmartEngineAdapter.reset()
    }

    /** Arm the commit gate via the same tiny-validator seam as CommitGateTest. */
    private fun armGate() {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.getEngine().loadValidatorWords(listOf("আমি"))
    }

    @Test
    fun garbageLearnedWordsAreSkippedOnLoad() = runBlocking {
        val storage = InMemoryStorage()
        // Garbage learned by a pre-gate build: NOT a validator word, NOT the
        // clean floor of kkkkx (which is ক্ক্ক্ক্ক্স), NOT a composition.
        storage.saveLearnedWord("kkkkx", "ক্কক্কক্স", 98)

        armGate()
        SmartEngineAdapter.initialize(storage)

        val result = SmartEngineAdapter.convertWord("kkkkx")
        assertNotEquals("ক্কক্কক্স", result.bengali, "pre-gate garbage must not override the gate")
        assertEquals(CleanTransliterator.transliterate("kkkkx"), result.bengali)
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, result.source)
    }

    @Test
    fun legitimateLearnedNamesSurviveLoad() = runBlocking {
        // তাকা is the clean deterministic floor of "taka" (rule 2: floor
        // equivalence — same class as learnAsWord names like rafsan -> রাফসান)
        // and it differs from the seed-dictionary primary টাকা, so this is a
        // discriminating probe: the preference only wins if it survived load.
        assertEquals("তাকা", CleanTransliterator.transliterate("taka"))
        val storage = InMemoryStorage()
        storage.saveLearnedWord("taka", "তাকা", 94)

        armGate()
        SmartEngineAdapter.initialize(storage)

        assertEquals("তাকা", SmartEngineAdapter.convertWord("taka").bengali)
    }

    @Test
    fun customDictionaryEntriesExemptFromSanitation() = runBlocking {
        val storage = InMemoryStorage()
        // Same garbage string, but at CUSTOM_CONVERSION_FREQUENCY (120):
        // explicit user-dictionary entry — dropping it would be data loss.
        storage.saveLearnedWord("kkkkx", "ক্কক্কক্স", 120)

        armGate()
        SmartEngineAdapter.initialize(storage)

        assertEquals("ক্কক্কক্স", SmartEngineAdapter.convertWord("kkkkx").bengali)
    }

    @Test
    fun sanitationFailsOpenWithoutOracle() = runBlocking {
        val storage = InMemoryStorage()
        storage.saveLearnedWord("kkkkx", "ক্কক্কক্স", 98)

        // No validator, no phonetic-index store: there is no word-membership
        // oracle, so sanitation keeps everything (and the commit gate is
        // dormant anyway — seed-only engines keep legacy behavior).
        SmartEngineAdapter.initialize(storage)

        assertEquals("ক্কক্কক্স", SmartEngineAdapter.convertWord("kkkkx").bengali)
    }
}
