package com.banglu.engine

import com.banglu.engine.platform.CachedDictionary
import com.banglu.engine.platform.PlatformStorage
import com.banglu.engine.types.LearnedWord
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * S136 (production re-audit, F-001): a snapshot write already queued on the
 * persistence lane must NOT resurrect data after an erase. The adapter
 * clears memory first and queues the persisted delete behind the pending
 * write on the same single lane, so the final persisted state is empty.
 */
class S136EraseOrderingJvmTest {

    /** Minimal storage: only the members the test cares about do anything. */
    private open class BaseStorage : PlatformStorage {
        override suspend fun getLearnedWords(): List<LearnedWord> = emptyList()
        override suspend fun saveLearnedWord(phonetic: String, bengali: String, frequency: Int) {}
        override suspend fun clearLearnedWords() {}
        override suspend fun getDictionaryVersion(): String? = null
        override suspend fun cacheDictionary(
            words: List<String>,
            frequencies: Map<String, Int>?,
            disambigMap: Map<String, String>?,
            version: String
        ) {}
        override suspend fun getCachedDictionary(currentVersion: String): CachedDictionary? = null
    }

    private class SlowStorage : BaseStorage() {
        @Volatile var identity: String? = null
        @Volatile var durableCalls = 0
        override suspend fun saveIdentityUserData(data: String) {
            delay(150) // a slow write that is still in flight when the erase is requested
            identity = data
        }
        override suspend fun loadIdentityUserData(): String? = identity
        override suspend fun clearIdentityUserDataDurably(): Boolean {
            durableCalls++
            identity = null
            return true
        }
        override suspend fun clearAllLearningDataDurably(): Boolean {
            durableCalls++
            identity = null
            return true
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        SmartEngineAdapter.reset()
    }

    @Test
    fun queuedIdentityWriteCannotOutliveErase() = runBlocking {
        val storage = SlowStorage()
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.initialize(storage, loader = null)
        SmartEngineAdapter.configurePersistenceScope(scope)
        SmartEngineAdapter.configureLearning(enabled = true, personalDictionary = true, identityAssist = true)

        SmartEngineAdapter.recordIdentity("rahim@gmail.com") // queues a 150ms write on the lane
        delay(20)
        assertTrue(SmartEngineAdapter.eraseAllLearning(), "durable erase confirmed")
        assertEquals(1, storage.durableCalls)
        delay(400) // let anything still queued drain
        assertNull(storage.identity, "the earlier write landed BEFORE the erase and was deleted")
        assertTrue(SmartEngineAdapter.identitySavedFills().isEmpty())
    }

    @Test
    fun eraseReportsStorageFailure() = runBlocking {
        val failing = object : BaseStorage() {
            override suspend fun clearAllLearningDataDurably(): Boolean = false
        }
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.initialize(failing, loader = null)
        assertEquals(false, SmartEngineAdapter.eraseAllLearning())
    }
}
