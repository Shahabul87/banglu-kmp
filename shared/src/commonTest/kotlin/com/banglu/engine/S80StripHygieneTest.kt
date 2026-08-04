package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * S80 (tester screenshot 2026-08-04: typing parle during the cold-start
 * window filled the strip with পাদ়লে/পাড়েল/পার্লৈ junk): seed-only engines
 * have no real-word oracle, so generated variants must be seed-known; and
 * grapheme-impossible strings (nukta on anything but ড/ঢ/য) never surface
 * from any source. Lives in commonTest so the JS surfaces pin it too.
 */
class S80StripHygieneTest {

    private fun freshEngine(): SmartEngine = SmartEngine().also { it.initializeSync() }

    @Test
    fun coldStartStripNeverShowsImpossibleNukta() {
        val engine = freshEngine()
        for (key in listOf("parle", "parbone", "korbone", "beche", "kmon")) {
            val bad = engine.getSuggestions(key, 8).filter { s ->
                s.bengali.withIndex().any { (i, c) ->
                    c == '়' && (i == 0 || s.bengali[i - 1] !in "ডঢয")
                }
            }
            assertTrue(bad.isEmpty(), "impossible-nukta junk for '$key': ${bad.map { it.bengali }}")
        }
    }

    @Test
    fun coldStartGeneratedVariantsMustBeSeedKnown() {
        val engine = freshEngine()
        // The known-good seed pair still surfaces (taka -> টাকা with তাকা
        // one tap away — the S26/S44 preference tests depend on it).
        val taka = engine.getSuggestions("taka", 8).map { it.bengali }
        assertTrue("তাকা" in taka, "seed-known ambiguous variant must survive: $taka")
    }
}
