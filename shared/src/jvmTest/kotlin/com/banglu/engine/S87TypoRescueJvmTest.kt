package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S87 (tester 2026-08-13: "tumi amke thogieco" degraded to আম্কে + থগিএছ):
 *  1. dative pronoun shorthands (amke/amk/tmk/tomke) join the curated
 *     MOBILE_SHORTHAND class — no habit chain can do a general medial-a drop;
 *  2. at the raw-transliteration floor, edit-distance-1 REAL words surface
 *     as tap-to-fix strip chips (typo_rescue). The primary stays the user's
 *     literal — new names remain typeable, nothing auto-replaces.
 */
class S87TypoRescueJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun dativePronounShorthandsResolve() {
        assertEquals("আমাকে", engine.convertWord("amke").bengali)
        assertEquals("আমাকে", engine.convertWord("amk").bengali)
        assertEquals("তোমাকে", engine.convertWord("tmk").bengali)
        assertEquals("তোমাকে", engine.convertWord("tomke").bengali)
    }

    @Test
    fun voicingTypoResolvesToTheIntendedWord() {
        // S100 repin: the S89 typo-tolerance layer + chat-register
        // frequencies now resolve thogieco (g for k) DIRECTLY to the
        // intended word as primary — strictly better than the original
        // S87 tap-to-fix chip. The typo_rescue chip path remains as
        // defense-in-depth for junk that resolution can't reach.
        val primary = engine.convertWord("thogieco")
        assertEquals(fold("ঠকিয়েছ"), fold(primary.bengali))
        val strip = engine.getSuggestions("thogieco", 6)
        assertEquals(fold("ঠকিয়েছ"), fold(strip.first().bengali), "strip[0] is the commit contract")
    }

    @Test
    fun rescueNeverReplacesThePrimary() {
        // The floor contract survives for junk BEYOND typo reach (2+ edits
        // from every store key): the literal stays the commit — a new name
        // must remain typeable, nothing auto-replaces.
        val primary = engine.convertWord("thogieqo")
        assertTrue(primary.confidence <= 0.65, "primary must stay at the raw floor, was ${primary.source}@${primary.confidence}")
        val strip = engine.getSuggestions("thogieqo", 6)
        assertEquals(fold(primary.bengali), fold(strip.first().bengali), "strip[0] is the commit contract")
    }

    @Test
    fun confidentKeysNeverGetRescueChips() {
        // Real words must not sprout typo chips (the floor gate).
        for (key in listOf("tumi", "amake", "kortam", "thik")) {
            val strip = engine.getSuggestions(key, 8)
            assertTrue(
                strip.none { it.source == "typo_rescue" },
                "'$key' must not carry rescue chips: ${strip.map { "${it.bengali}(${it.source})" }}"
            )
        }
    }
}
