package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S169 (native-heap spike at first install): the BN glide lexicon build used
 * `GROUP BY key ORDER BY f DESC` over 1.8M rows — SQLite sorted the whole
 * table in memory (heapprofd: 453 MB of cursor-window allocations, native
 * heap 17 → 182 MB right after the dictionary load). The build now streams
 * the index in key order and keeps a bounded top-K in Kotlin.
 */
class S169GlideTopKTest {

    private fun rows(vararg r: Pair<String, Int>) = r.asSequence()

    @Test
    fun keepsTheHighestFrequencyKeysUpToCap() {
        val out = GlideLexiconStore.topKByFrequency(
            rows("ami" to 900, "tumi" to 800, "kemon" to 700, "bhalo" to 600, "acho" to 500),
            seeds = emptyMap(), cap = 3
        )
        assertEquals(setOf("ami", "tumi", "kemon"), out.keys)
        assertEquals(900, out["ami"])
    }

    @Test
    fun seedsAreAlwaysKeptAndTakeTheHigherFrequency() {
        val out = GlideLexiconStore.topKByFrequency(
            rows("ami" to 900, "kmon" to 5, "tumi" to 800),
            seeds = mapOf("kmon" to 1, "valo" to 1), cap = 3
        )
        assertTrue(out.containsKey("kmon"))
        assertTrue(out.containsKey("valo"))
        assertEquals(5, out["kmon"])
        assertEquals(setOf("kmon", "valo", "ami"), out.keys)
    }

    @Test
    fun filtersNonRomanAndOneLetterKeys() {
        val out = GlideLexiconStore.topKByFrequency(
            rows("a" to 999, "ok!" to 999, "কি" to 999, "ami" to 1),
            seeds = emptyMap(), cap = 10
        )
        assertEquals(setOf("ami"), out.keys)
    }

    @Test
    fun orderIndependentAndBounded() {
        // Keys must be a-z only: encode the number in letters (1 -> "b", 27 -> "bb" ...).
        fun key(n: Int): String { var x = n; val sb = StringBuilder(); while (x > 0) { sb.append('a' + x % 26); x /= 26 }; return "w$sb" }
        val many = (1..10_000).map { key(it) to it }.shuffled(kotlin.random.Random(3)).asSequence()
        val out = GlideLexiconStore.topKByFrequency(many, seeds = emptyMap(), cap = 100)
        assertEquals(100, out.size)
        assertTrue(out.values.all { it > 9_900 })
        assertFalse(out.containsKey(key(1)))
    }
}
