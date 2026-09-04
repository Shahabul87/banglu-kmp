package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class S170FastCommitReconcilePolicyTest {
    private val punct: (Char) -> Boolean = { it == '।' || it == ',' || it == '?' || it == '!' }
    private fun plan(before: String?, committed: String, composing: String = "", active: Boolean = false, append: String = " ") =
        FastCommitReconcilePolicy.plan(before, committed, append, composing, active, punct)

    @Test
    fun plainTailIsReplaced() {
        assertEquals(FastCommitReconcilePolicy.Plan.ReplaceTail(11, " "), plan("আমি ইনস্তিতিউত ", "ইনস্তিতিউত"))
    }

    @Test
    fun oneTightPunctuationAfterTheSpaceIsKept() {
        assertEquals(FastCommitReconcilePolicy.Plan.ReplaceTail(12, " ।"), plan("ইনস্তিতিউত ।", "ইনস্তিতিউত"))
    }

    @Test
    fun replacesInFrontOfTheLiveComposingWord() {
        assertEquals(
            FastCommitReconcilePolicy.Plan.ReplaceBeforeComposing(13, " ", "রব"),
            plan("আমি ইনস্তিতিউত রব", "ইনস্তিতিউত", composing = "রব", active = true),
        )
    }

    @Test
    fun dandaFromDoubleSpaceThenNextWordStillReconciles() {
        // S180 PIN FLIP (documented decision, device evidence 2026-09-04): the
        // Facebook demo recording committed বুজতেপার্ছিনা।, the authoritative
        // বুঝতে পারছিনা arrived after the double-space দাঁড়ি, and this shape
        // was refused — the wrong word stayed on screen. The দাঁড়ি model's
        // "। " is a tail, not a user edit.
        assertEquals(
            FastCommitReconcilePolicy.Plan.ReplaceBeforeComposing("বুজতেপার্ছিনা".length + 2 + 2, "। ", "তো"),
            plan("আমি বুজতেপার্ছিনা। তো", "বুজতেপার্ছিনা", composing = "তো", active = true),
        )
        assertEquals(FastCommitReconcilePolicy.Plan.ReplaceTail("বুজতেপার্ছিনা".length + 2, "। "), plan("আমি বুজতেপার্ছিনা। ", "বুজতেপার্ছিনা"))
        assertEquals(FastCommitReconcilePolicy.Plan.ReplaceTail("ইনস্তিতিউত".length + 2, "  "), plan("আমি ইনস্তিতিউত  ", "ইনস্তিতিউত"))
    }

    @Test
    fun aRealEditInBetweenIsLeftAlone() {
        assertNull(plan("আমি ইনস্তিতিউত    রব", "ইনস্তিতিউত", composing = "রব", active = true))   // gap longer than the tail
        assertNull(plan("আমি ইনস্তিতিউত রব", "ইনস্তিতিউত", composing = "", active = true))       // composing unknown
        assertNull(plan("আমি ইনস্তিতিউত", "ইনস্তিতিউত"))                                        // the appended space was deleted
        assertNull(plan("আমি ইনস্তিতিউতক ", "ইনস্তিতিউত"))                                      // a letter was typed onto the word
    }

    @Test
    fun missingOrUnrelatedTextIsLeftAlone() {
        assertNull(plan(null, "ইনস্তিতিউত"))
        assertNull(plan("কিছু", "ইনস্তিতিউত"))
    }
}
