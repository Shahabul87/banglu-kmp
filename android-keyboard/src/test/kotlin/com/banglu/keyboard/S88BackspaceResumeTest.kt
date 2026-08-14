package com.banglu.keyboard

import com.banglu.engine.SmartEngine
import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * S88 (tester 2026-08-13): abaro -> backspace -> retype o must yield the same
 * word as typing abaro fresh. The resume plan re-enters roman composition on
 * the committed fragment; these tests run the REAL rule layer (seed engine
 * instant preview + ReverseTransliterator) — the same functions the IME
 * injects.
 */
class S88BackspaceResumeTest {

    private val engine = SmartEngine().also { it.initializeSync() }

    private fun plan(before: String) = BackspaceResume.plan(
        textBeforeCursor = before,
        reverse = { ReverseTransliterator.reverseWord(it) },
        instantPreview = { engine.convertForInstantPreview(it) },
    )

    @Test
    fun abaroClassResumesOnRomanFragment() {
        // আবারও: standalone ও is its own cluster — fragment আবার, roman abar.
        val p = plan("আমি আবারও")
        assertNotNull(p, "আবারও must produce a resume plan")
        assertEquals("আবারও".length, p.deleteLength)
        assertEquals("abar", p.romanBuffer)
        assertEquals("আবার", p.visibleFragment)
        // Typing o on the resumed buffer re-converts the WHOLE word — the
        // consistency the tester asked for (same key -> same word, always).
        assertEquals(
            engine.convertWord("abaro").bengali,
            engine.convertWord(p.romanBuffer + "o").bengali
        )
    }

    @Test
    fun karEndingWordResumesConsistently() {
        // আবারো: the trailing cluster is র+ো — fragment আবা, roman aba.
        val p = plan("আবারো")
        assertNotNull(p, "আবারো must produce a resume plan")
        assertEquals("aba", p.romanBuffer)
        assertEquals("আবা", p.visibleFragment)
    }

    @Test
    fun deleteLengthCoversOnlyTheTrailingWord() {
        val p = plan("সে আবারও")
        assertNotNull(p)
        assertEquals("আবারও".length, p.deleteLength)
    }

    @Test
    fun noPlanWithoutTrailingBengaliWord() {
        assertNull(plan(""), "empty text")
        assertNull(plan("hello"), "latin text")
        assertNull(plan("আবারও "), "trailing space separates the word")
    }

    @Test
    fun singleClusterWordFallsBackToPlainDeletion() {
        // Deleting the only cluster empties the word — resume is pointless.
        assertNull(plan("সে ও"))
    }

    @Test
    fun nonRoundTrippingFragmentFallsBack() {
        // A fragment whose rule-layer echo differs from the on-screen text
        // must NOT resume (the backspace would visibly change the word).
        val p = BackspaceResume.plan(
            textBeforeCursor = "আবারও",
            reverse = { ReverseTransliterator.reverseWord(it) },
            instantPreview = { "ভিন্ন" },
        )
        assertNull(p)
    }

    @Test
    fun clusterBoundaryKeepsKarAttached() {
        // আবারো -> boundary before র (র+ো delete together, never a bare র).
        val b = BackspaceResume.previousUserVisibleClusterBoundary("আবারো")
        assertEquals("আবা", "আবারো".substring(0, b))
    }
}
