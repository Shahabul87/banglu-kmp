package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S86 (tester 2026-08-13, ট/ঠ note: "ট or ঠ দিয়ে শব্দ হচ্ছে না"): the live
 * preview showed raw থ-garbage (thokiyecho -> থকিয়েচ) while Space committed
 * ঠকিয়েছ. Root cause was a compiler ordering hole: final_o created new
 * chh-final aliases AFTER the chh-collapse family had run, so the typed
 * -cho/-co/-so spellings had zero store rows for every ছ-final word without
 * an independent -ো corpus twin. Fixed by re-running the collapses after
 * final_o (db 3.8.11); the composing path then hits the store like any other
 * key — parity by construction.
 */
class S86ThFinalOParityJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun chhFinalOSpellingsResolveAndPreviewTheCommit() {
        for (key in listOf("thokiyecho", "thokiecho", "thokieco", "thokiyeco")) {
            val commit = engine.convertWord(key)
            assertEquals(fold("ঠকিয়েছ"), fold(commit.bengali), "commit for '$key'")
            assertEquals(
                fold(commit.bengali),
                fold(engine.convertForComposing(key).bengali),
                "preview must equal commit for '$key'"
            )
        }
    }

    @Test
    fun bareTwinWinsByFrequencyLawAndOTwinStaysReachable() {
        // Documented decision (matches the S25 dekhto -> দেখত pin): when the
        // bare ছ-final verb and its -ো twin share an o-typed key at the same
        // priority, frequency decides — করেছ@80 over করেছো@11. The twin must
        // stay one tap away on the strip.
        assertEquals(fold("করেছ"), fold(engine.convertWord("korecho").bengali))
        val strip = engine.getSuggestions("korecho", 8)
        kotlin.test.assertTrue(
            strip.any { fold(it.bengali) == fold("করেছো") },
            "করেছো must stay reachable: ${strip.map { it.bengali }}"
        )
    }
}
