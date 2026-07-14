package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import com.banglu.engine.dictionary.SeedData
import kotlin.test.assertTrue

/** JVM home of the S26/S44 persistence-contract test (runBlocking is JVM-only). */
class UserPreferencePersistenceJvmTest {

    @kotlin.test.AfterTest
    fun tearDown() { SmartEngineAdapter.reset() }

    /**
     * S26: accepting the engine's own primary (space commit / first-chip tap)
     * must NOT be persisted as a ranking preference — recording it would pin
     * this build's output as a "user choice" and veto engine fixes shipped in
     * later updates (device-observed: line->লিনে accepted on 1.5.10 shadowed
     * the 1.5.14 line->লাইন fix).
     */
    @Test
    fun acceptingEnginePrimaryIsNotPersistedAsPreference() {
        val storage = RecordingStorage()
        SmartEngineAdapter.initializeSync()
        kotlinx.coroutines.runBlocking {
            SmartEngineAdapter.initialize(storage)
            SmartEngineAdapter.configurePersistenceScope(this)

            val primary = SmartEngineAdapter.convertWord("taka").bengali
            assertEquals("টাকা", primary)
            // Explicitly tapping the primary — equal-to-primary skip (S26).
            SmartEngineAdapter.onWordSelected("taka", primary, explicitChoice = true)

            // S44 (audit): a PASSIVE commit of a divergent word (contextual
            // promotion, reconcile result — no user choice) records NOTHING.
            SmartEngineAdapter.onWordSelected("taka", "তাকা")

            // Divergent EXPLICIT tap IS persisted.
            SmartEngineAdapter.onWordSelected("taka", "তাকা", explicitChoice = true)
        }
        assertEquals(listOf("taka:তাকা:94"), storage.saved)
    }

    private class RecordingStorage(
        preload: List<com.banglu.engine.types.LearnedWord> = emptyList()
    ) : com.banglu.engine.platform.PlatformStorage {
        val saved = mutableListOf<String>()
        private val words = preload.toMutableList()
        override suspend fun getLearnedWords() = words.toList()
        override suspend fun saveLearnedWord(phonetic: String, bengali: String, frequency: Int) {
            saved.add("$phonetic:$bengali:$frequency")
            words.add(com.banglu.engine.types.LearnedWord("s${saved.size}", phonetic, bengali, frequency, lastUsed = 0L))
        }
        override suspend fun clearLearnedWords() { words.clear() }
        override suspend fun getDictionaryVersion(): String? = null
        override suspend fun cacheDictionary(
            words: List<String>,
            frequencies: Map<String, Int>?,
            disambigMap: Map<String, String>?,
            version: String
        ) {}
        override suspend fun getCachedDictionary(currentVersion: String) = null
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

            SmartEngineAdapter.onWordSelected(key, alternate, explicitChoice = true)
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
