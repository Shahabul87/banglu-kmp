package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S174 (user: "type a letter wrong in the middle and try to fix that — you
 * will feel it"): a letter typed or deleted with the caret INSIDE a Bengali
 * word must re-compose the WHOLE word (prefix + edit + suffix), not just the
 * prefix — the one-sided resume left "স্বাধি" + "নতা" and a space in the
 * middle on commit.
 */
class S174MidWordEditTest {
    private val reverse: (String) -> String = { mapOf("স্বাধ" to "sbadh", "নতা" to "nota", "স্বা" to "sba", "বল" to "bol")[it] ?: "?" }
    private val preview: (String) -> String = { mapOf("sbadh" to "স্বাধ", "nota" to "নতা", "sba" to "স্বা", "bol" to "বল", "sbanota" to "স্বানতা")[it] ?: "?" }

    @Test
    fun insertInsideAWordPlansBothSides() {
        val p = BackspaceResume.planForMidWordEdit(textBeforeCursor = "আমি স্বাধ", textAfterCursor = "নতা বলি", reverse = reverse, instantPreview = preview)!!
        assertEquals("স্বাধ".length, p.deleteBefore)
        assertEquals("নতা".length, p.deleteAfter)
        assertEquals("sbadh", p.romanPrefix)
        assertEquals("nota", p.romanSuffix)
    }

    @Test
    fun caretAtWordEndIsNotAMidWordEdit() {
        assertNull(BackspaceResume.planForMidWordEdit("আমি স্বাধ", " নতা", reverse, preview))
        assertNull(BackspaceResume.planForMidWordEdit("আমি স্বাধ", "", reverse, preview))
    }

    @Test
    fun suffixWithUnusableRomanIsRefused() {
        // S175b PIN FLIP (documented decision): a suffix whose rule-only
        // preview merely differs (নতা → "nota" → নট) now PLANS — the echo gate
        // refused every internal-ো word on device. Only an unusable reverse
        // (non a-z, empty, over-long) refuses the typing plan.
        val badPreview: (String) -> String = { if (it == "nota") "নট" else preview(it) }
        assertEquals("sbadhnota", BackspaceResume.planForMidWordEdit("স্বাধ", "নতা", reverse, badPreview)!!.let { it.romanPrefix + it.romanSuffix })
        val badReverse: (String) -> String = { if (it == "নতা") "n?ta" else reverse(it) }
        assertNull(BackspaceResume.planForMidWordEdit("স্বাধ", "নতা", badReverse, preview))
    }

    @Test
    fun deleteInsideAWordDropsTheLastClusterBeforeTheCaret() {
        // S88 cluster law: backspace removes the whole last cluster (ধী), so
        // the prefix that survives is স্বা, re-composed with the suffix নতা.
        val p = BackspaceResume.planForMidWordBackspace(textBeforeCursor = "স্বাধী", textAfterCursor = "নতা", reverse = reverse, instantPreview = preview)!!
        assertEquals("স্বাধী".length, p.deleteBefore)
        assertEquals("নতা".length, p.deleteAfter)
        assertEquals("sba", p.romanPrefix)
        assertEquals("nota", p.romanSuffix)
    }
}
