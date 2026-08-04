package com.banglu.engine

import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S78 (tester round 2026-08-04): the last-tap ranking preference was
 * (a) un-undoable — tapping the engine's own primary back hit the S26
 * equal-to-primary skip and left the stale divergent pick installed forever —
 * and (b) context-blind — it overrode the personal-bigram context promotion.
 * These pins cover both repairs on the seed taka/টাকা/তাকা pair.
 */
class S78PreferenceRepairJvmTest {

    @AfterTest
    fun tearDown() {
        SmartEngineAdapter.reset()
    }

    @Test
    fun tappingEnginePrimaryClearsStoredPreferenceDurably() {
        val storage = RemovalTrackingStorage()
        SmartEngineAdapter.initializeSync()
        runBlocking {
            SmartEngineAdapter.initialize(storage)
            SmartEngineAdapter.configurePersistenceScope(this)

            SmartEngineAdapter.onWordSelected("taka", "তাকা", explicitChoice = true)
            assertEquals("তাকা", SmartEngineAdapter.convertWord("taka").bengali)

            // S78: choosing the engine primary back clears the preference —
            // pre-S78 this tap was silently ignored and তাকা stuck forever.
            SmartEngineAdapter.onWordSelected("taka", "টাকা", explicitChoice = true)
            assertEquals("টাকা", SmartEngineAdapter.convertWord("taka").bengali)
        }
        assertTrue("taka" in storage.removedKeys, "storage row must be removed, got ${storage.removedKeys}")
        assertTrue(
            storage.rows.none { it.phonetic == "taka" && it.frequency < 120 },
            "sub-custom taka rows must be gone: ${storage.rows}"
        )

        // Durable: a fresh initialize from the same storage must not
        // resurrect the cleared preference.
        SmartEngineAdapter.reset()
        SmartEngineAdapter.initializeSync()
        runBlocking { SmartEngineAdapter.initialize(storage) }
        assertEquals("টাকা", SmartEngineAdapter.convertWord("taka").bengali)
    }

    @Test
    fun personalContextEvidenceBeatsStalePreference() {
        SmartEngineAdapter.initializeSync()

        SmartEngineAdapter.onWordSelected("taka", "তাকা", explicitChoice = true)
        assertEquals("তাকা", SmartEngineAdapter.convertWord("taka").bengali)

        // The user's own commits after অনেক say টাকা — twice is evidence.
        val eng = SmartEngineAdapter.getEngine()
        repeat(2) { eng.recordUserBigram("অনেক", "টাকা") }

        assertEquals(
            "টাকা",
            SmartEngineAdapter.convertWordWithContext("taka", listOf("অনেক")).bengali,
            "personal context evidence must beat the context-blind tap"
        )
        // S19: the strip's first chip mirrors the contextual commit.
        assertEquals(
            "টাকা",
            SmartEngineAdapter.getSuggestionsWithContext("taka", listOf("অনেক"), 5).first().bengali
        )
        // WYSIWYG: the composing preview agrees with the commit.
        assertEquals(
            "টাকা",
            SmartEngineAdapter.convertForComposing("taka", "অনেক").bengali
        )
        // Without context the explicit tap still applies.
        assertEquals("তাকা", SmartEngineAdapter.convertWord("taka").bengali)
    }

    private class RemovalTrackingStorage : com.banglu.engine.platform.PlatformStorage {
        val rows = mutableListOf<com.banglu.engine.types.LearnedWord>()
        val removedKeys = mutableListOf<String>()
        override suspend fun getLearnedWords() = rows.toList()
        override suspend fun saveLearnedWord(phonetic: String, bengali: String, frequency: Int) {
            rows.add(
                com.banglu.engine.types.LearnedWord(
                    "$phonetic::$bengali", phonetic, bengali, frequency, lastUsed = rows.size.toLong()
                )
            )
        }
        override suspend fun removeLearnedWord(phonetic: String) {
            removedKeys.add(phonetic)
            rows.removeAll { it.phonetic == phonetic && it.frequency < 120 }
        }
        override suspend fun clearLearnedWords() = rows.clear()
        override suspend fun getDictionaryVersion(): String? = null
        override suspend fun cacheDictionary(
            words: List<String>,
            frequencies: Map<String, Int>?,
            disambigMap: Map<String, String>?,
            version: String
        ) {}
        override suspend fun getCachedDictionary(currentVersion: String) = null
    }
}
