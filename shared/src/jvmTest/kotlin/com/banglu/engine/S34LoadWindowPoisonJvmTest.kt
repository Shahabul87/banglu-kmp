package com.banglu.engine

import com.banglu.engine.rules.CleanTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S34: commits made during the dictionary-load window learned the seed
 * engine's raw transliteration as a personal word (kmon -> ক্মন), which then
 * shadowed the store's resolution (কেমন) on that device forever. Reproduced
 * on a fresh emulator profile 2026-07-12.
 *
 * Two-layer fix, locked here:
 * 1. Prevention lives in SmartEngineAdapter.onWordSelected (no learning while
 *    the seed engine serves without a store).
 * 2. Healing: isLearnedEntryTrusted no longer blanket-trusts an entry that is
 *    the raw transliteration of its own key — only when the index has no
 *    different owner for that key. Poisoned entries are skipped on load
 *    (never deleted, F5b reversibility).
 */
class S34LoadWindowPoisonJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun rawTransliterationEntryIsUntrustedWhenStoreOwnsTheKey() {
        // The poisoning artifact observed on-device.
        assertEquals("ক্মন", CleanTransliterator.transliterate("kmon"))
        assertFalse(engine.isLearnedEntryTrusted("kmon", "ক্মন"))
        // The store's own resolution stays trusted (known word).
        assertTrue(engine.isLearnedEntryTrusted("kmon", "কেমন"))
        assertEquals("কেমন", engine.convertWord("kmon").bengali)
    }

    @Test
    fun rawTransliterationEntryStaysTrustedWithoutAStoreOwner() {
        // A key the index does not own: raw form is legitimate OOV learning
        // (proper nouns etc.) and must keep working.
        val key = "kzwq"
        val raw = CleanTransliterator.transliterate(key)
        if (ConjunctSolutionRoundJvmTest.store.lookupExact(key).isEmpty()) {
            assertTrue(engine.isLearnedEntryTrusted(key, raw))
        }
    }
}
