package com.banglu.engine

import com.banglu.engine.platform.CachedDictionary
import com.banglu.engine.platform.PlatformStorage
import com.banglu.engine.types.LearnedWord
import com.banglu.engine.types.ResolutionSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * S140 (v1.5.86 re-audit, last production blocker): an erase that lands
 * WHILE initialize() is still building the engine from a storage snapshot
 * taken before the erase must not let that stale engine be published —
 * the deleted words would resurface until the next clean rebuild.
 *
 * Barrier-controlled: the fake storage hands out the learned word, then
 * blocks initialize() at its next storage read (user bigrams) until the
 * test has run the erase, then lets initialize() finish.
 */
class S140InitializeVersusEraseJvmTest {

    private class GatedStorage : PlatformStorage {
        val readLearned = CompletableDeferred<Unit>()
        val releaseInit = CompletableDeferred<Unit>()
        @Volatile var learned: List<LearnedWord> = listOf(
            // ≥ USER_CUSTOM_CONVERSION_FREQUENCY: baked into the engine dictionary itself.
            LearnedWord("rafsan::রাফসান", "rafsan", "রাফসান", 120, 1L)
        )
        @Volatile var cleared = false
        override suspend fun getLearnedWords(): List<LearnedWord> {
            readLearned.complete(Unit)
            return learned
        }
        override suspend fun getUserBigrams(): Map<String, Map<String, Int>> {
            releaseInit.await() // <- the erase happens while we wait here
            return emptyMap()
        }
        override suspend fun clearAllLearningDataDurably(): Boolean {
            learned = emptyList(); cleared = true; return true
        }
        override suspend fun saveLearnedWord(phonetic: String, bengali: String, frequency: Int) {}
        override suspend fun clearLearnedWords() { learned = emptyList() }
        override suspend fun getDictionaryVersion(): String? = null
        override suspend fun cacheDictionary(words: List<String>, frequencies: Map<String, Int>?, disambigMap: Map<String, String>?, version: String) {}
        override suspend fun getCachedDictionary(currentVersion: String): CachedDictionary? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        SmartEngineAdapter.reset()
    }

    @Test
    fun eraseDuringInitializeDiscardsTheStaleEngine() = runBlocking {
        val storage = GatedStorage()
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.configurePersistenceScope(scope)

        val init = scope.async { SmartEngineAdapter.initialize(storage, loader = null) }
        withTimeout(5_000) { storage.readLearned.await() }   // learned words already read into the build
        assertTrue(SmartEngineAdapter.eraseAllLearning(), "erase confirmed durable")
        assertTrue(storage.cleared)
        storage.releaseInit.complete(Unit)                     // let the stale build finish
        withTimeout(5_000) { init.await() }

        // The stale build must NOT have been published: the deleted custom
        // word may not resolve from the dictionary any more.
        val after = SmartEngineAdapter.getEngine().convertWord("rafsan")
        assertNotEquals(ResolutionSource.DICTIONARY, after.source, "deleted learned word resurfaced from a stale engine")
    }

    @Test
    fun initializeWithoutAnEraseStillPublishes() = runBlocking {
        val storage = GatedStorage()
        storage.releaseInit.complete(Unit)
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.initialize(storage, loader = null)
        assertEquals(ResolutionSource.DICTIONARY, SmartEngineAdapter.getEngine().convertWord("rafsan").source)
    }
}
