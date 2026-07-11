package com.banglu.engine

import com.banglu.engine.platform.InMemoryStorage
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S26: vetted English-intent flips (line -> লাইন) must be preference-immune,
 * exactly like curated loanwords. Pre-S26 builds auto-learned EVERY space
 * commit as a ranking "preference", so any device that ever committed the old
 * primary (line -> লিনে on 1.5.10) carried a stale learned row that silently
 * vetoed the shipped 1.5.14 fix — observed on the physical phone 2026-07-10.
 */
class EnglishIntentPreferenceShieldJvmTest {

    @AfterTest
    fun tearDown() {
        SmartEngineAdapter.reset()
    }

    @Test
    fun staleLearnedPreferenceCannotOverrideIntentFlip() = runBlocking {
        val storage = InMemoryStorage()
        // Pre-S26 poison shapes: the 96 agreement marker from 1.5.10 and the
        // 94 re-poison recorded once a stale preference was already overriding.
        storage.saveLearnedWord("line", "লিনে", 96)
        storage.saveLearnedWord("line", "লিনে", 94)
        // Non-intent preferences must keep working as before.
        storage.saveLearnedWord("taka", "তাকা", 94)

        SmartEngineAdapter.setPhoneticIndex(ConjunctSolutionRoundJvmTest.store)
        SmartEngineAdapter.initialize(storage, TestDictionaryLoader())

        assertEquals("লাইন", SmartEngineAdapter.convertWord("line").bengali)
        assertEquals("টাইম", SmartEngineAdapter.convertWord("time").bengali)
        assertEquals("তাকা", SmartEngineAdapter.convertWord("taka").bengali)
    }

    /**
     * Lite mode (store attached, NO validator words — the memoryClass<256
     * configuration): the intent flip must fire on the COMMIT path too, not
     * just the composing mirror, or the preview shows লাইন while Space
     * commits লিনে (observed on the 192-class emulator, 2026-07-10).
     */
    @Test
    fun liteModeCommitMatchesComposingPreviewForIntentKeys() {
        val engine = SmartEngine()
        engine.initializeSync()
        engine.setPhoneticIndex(ConjunctSolutionRoundJvmTest.store)

        assertEquals("লাইন", engine.convertWord("line").bengali)
        assertEquals("লাইন", engine.convertForComposing("line").bengali)
        assertEquals("টাইম", engine.convertWord("time").bengali)
    }

    @Test
    fun composingPreviewMirrorsIntentFlip() {
        val engine = ConjunctSolutionRoundJvmTest.engine
        assertEquals("লাইন", engine.convertForComposing("line").bengali)
        assertEquals("টাইম", engine.convertForComposing("time").bengali)
        // Non-intent keys keep the conservative composing behavior.
        assertEquals("নামে", engine.convertForComposing("name").bengali)
    }
}
