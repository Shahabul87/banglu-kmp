package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S85 (tester 2026-08-13, English note: "user type pattern -> engine should
 * return প্যাটার্ন in editor AND pattern in suggestion"): two halves —
 *  1. compiler: word-final consonant after ER takes the reph (প্যাটার্ন,
 *     মডার্ন, স্ট্যান্ডার্ড — the whole -ern/-ard class was unrefed);
 *  2. engine: ENGLISH_LEXICON-resolved primaries now get the raw-word
 *     english_passthrough chip (the heuristic detector missed them, and the
 *     literal "alternative" they carry is Latin-filtered off the strip).
 */
class S85EnglishDualReturnJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun patternGetsRephAndEnglishChip() {
        assertEquals("প্যাটার্ন", engine.convertWord("pattern").bengali)
        val strip = engine.getSuggestions("pattern", 8)
        assertTrue(
            strip.any { it.source == "english_passthrough" && it.bengali == "pattern" },
            "strip must carry the raw english chip, got ${strip.map { it.bengali }}"
        )
    }

    @Test
    fun lexiconResolvedKeysAlwaysCarryTheRawChip() {
        for (key in listOf("because", "pattern")) {
            val strip = engine.getSuggestions(key, 8)
            assertTrue(
                strip.any { it.source == "english_passthrough" && it.bengali == key },
                "'$key' strip must carry the raw english chip, got ${strip.map { it.bengali }}"
            )
        }
    }

    @Test
    fun detectorResolvedKeysKeepTheirChip() {
        // The pre-existing behavior (computer/office class) must survive the
        // gate change.
        for (key in listOf("computer", "office", "engine")) {
            val strip = engine.getSuggestions(key, 8)
            assertTrue(
                strip.any { it.source == "english_passthrough" && it.bengali == key },
                "'$key' lost its english chip: ${strip.map { it.bengali }}"
            )
        }
    }

    @Test
    fun erFinalRephClassResolvedInLexicon() {
        // Store-level pins of the compiler fix (db 3.8.11).
        val store = ConjunctSolutionRoundJvmTest.engine
        assertEquals("মডার্ন", store.convertWord("modern").bengali)
        assertEquals("স্ট্যান্ডার্ড", store.convertWord("standard").bengali)
    }
}
