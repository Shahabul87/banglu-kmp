package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S170 (top-1,000 device study): a fast-committed preview must still be
 * corrected when the user is already composing the NEXT word. The old guard
 * required an empty buffer and an editor tail equal to the committed text, so
 * at machine-speed typing long words (ইনস্টিটিউট, রবীন্দ্রনাথ, বহিঃসংযোগ) kept
 * their rule-only preview forever.
 */
class S170FastCommitReconcilePolicyTest {

    private val punct: (Char) -> Boolean = { it == '।' || it == ',' || it == '?' || it == '!' }

    @Test
    fun idleTailReplacesInPlace() {
        val p = FastCommitReconcilePolicy.plan(before = "আমি ইনস্তিতিউত ", expected = "ইনস্তিতিউত ", composingNow = "", bufferActive = false, isTightPunctuation = punct)
        assertEquals(FastCommitReconcilePolicy.Plan.ReplaceTail(deleteLength = 11, trailing = ""), p)
    }

    @Test
    fun idleTailKeepsOneTrailingPunctuation() {
        val p = FastCommitReconcilePolicy.plan(before = "ইনস্তিতিউত ।", expected = "ইনস্তিতিউত ", composingNow = "", bufferActive = false, isTightPunctuation = punct)
        assertEquals(FastCommitReconcilePolicy.Plan.ReplaceTail(deleteLength = 12, trailing = "।"), p)
    }

    @Test
    fun composingNextWordReplacesBeforeTheComposingText() {
        val p = FastCommitReconcilePolicy.plan(before = "আমি ইনস্তিতিউত রব", expected = "ইনস্তিতিউত ", composingNow = "রব", bufferActive = true, isTightPunctuation = punct)
        assertEquals(FastCommitReconcilePolicy.Plan.ReplaceBeforeComposing(deleteLength = 13, composingNow = "রব"), p)
    }

    @Test
    fun composingButEditorDisagreesIsSkipped() {
        assertNull(FastCommitReconcilePolicy.plan(before = "আমি ইনস্তিতিউত  রব", expected = "ইনস্তিতিউত ", composingNow = "রব", bufferActive = true, isTightPunctuation = punct))
        assertNull(FastCommitReconcilePolicy.plan(before = "আমি ইনস্তিতিউত রব", expected = "ইনস্তিতিউত ", composingNow = "", bufferActive = true, isTightPunctuation = punct))
    }

    @Test
    fun unknownEditorTextIsSkipped() {
        assertNull(FastCommitReconcilePolicy.plan(before = null, expected = "ইনস্তিতিউত ", composingNow = "", bufferActive = false, isTightPunctuation = punct))
        assertNull(FastCommitReconcilePolicy.plan(before = "কিছু", expected = "ইনস্তিতিউত ", composingNow = "", bufferActive = false, isTightPunctuation = punct))
    }
}
