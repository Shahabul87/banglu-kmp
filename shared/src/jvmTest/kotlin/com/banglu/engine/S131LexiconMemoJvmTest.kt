package com.banglu.engine

import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.platform.PhoneticIndexStore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * S131.1 (Windows field report #2: "typing and keystrokes is slow"): the
 * English-honesty guard consults english_lexicon on EVERY conversion of every
 * 4+ letter word — and on the live-echo path that meant one sqlite query per
 * keystroke, which an AV-hooked laptop disk turns into visible typing lag.
 * The lexicon answer for a key never changes at runtime: it must be memoized,
 * misses included.
 */
class S131LexiconMemoJvmTest {

    private class CountingStore(private val inner: PhoneticIndexStore) : PhoneticIndexStore by inner {
        var englishLookups = 0
        override fun lookupEnglish(key: String): String? {
            englishLookups++
            return inner.lookupEnglish(key)
        }
    }

    @Test
    fun theTypingPathAsksTheLexiconAtMostOncePerKey() {
        val store = CountingStore(ConjunctSolutionRoundJvmTest.store)
        val engine = SmartEngine()
        engine.initializeSync()
        engine.setPhoneticIndex(store)
        // A user typing the same word again and again — the live echo converts
        // on every keystroke, so an unmemoized lexicon read scales with
        // keystrokes, not with vocabulary.
        repeat(50) { engine.convertWord("kortobbo") }
        repeat(50) { engine.convertWord("real") }
        assertTrue(
            store.englishLookups <= 6,
            "the lexicon must be consulted per unique key, not per keystroke — " +
                "got ${store.englishLookups} lookups for 2 keys"
        )
    }
}
