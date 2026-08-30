package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * S144 (Windows field report: backspace and space lag): a keystroke on the
 * sqlite-backed store used to issue hundreds of point queries — every edit
 * variant the typo and lattice layers probe — and an antivirus-hooked disk
 * turned those into visible lag. The store's Bloom negative index answers
 * the misses from memory; this pins the budget on the real dictionary.
 */
class S144KeystrokeSqliteBudgetJvmTest {
    @Test
    fun typingAnUnknownWordStaysWithinTheSqliteBudget() {
        val store = JvmSqlitePhoneticIndexStore(TestDictionaryLoader.findDictionarySqlite())
        val t0 = System.nanoTime()
        while (!store.negativeIndexReady && System.nanoTime() - t0 < 60_000_000_000L) Thread.sleep(50)
        assertTrue(store.negativeIndexReady, "negative index must build (took > 60 s)")
        val engine = SmartEngine().also { eng ->
            eng.initializeSync()
            runBlocking { eng.initialize(storage = null, loader = TestDictionaryLoader()) }
            eng.setPhoneticIndex(store)
        }
        // Warm the one-time lazies (lexicon key set, first conversions).
        engine.convertWord("warmup"); engine.convertWord("bhalo")
        val before = store.sqliteQueryCount
        var buf = ""
        for (c in "suggention") { buf += c; engine.convertWord(buf) }
        var back = "commnuity"
        while (back.length > 1) { back = back.dropLast(1); engine.convertWord(back) }
        val queries = store.sqliteQueryCount - before
        println("S144 kinds: ${store.sqliteQueriesByKind}")
        // 18 keystrokes; pre-S144 this was several thousand.
        assertTrue(queries <= 18 * 40, "sqlite queries for 18 keystrokes: $queries (budget 40/keystroke)")
    }
}
