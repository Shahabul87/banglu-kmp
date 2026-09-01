package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class S163GlideCommitPolicyTest {

    @Test
    fun banglaPlanNeverErasesEditorChars() {
        val plan = GlideCommitPolicy.planCommit(GlideMode.BANGLA, editorCharsFromFirstKey = 1, word = "কেমন")
        assertEquals(0, plan.eraseEditorChars)
        assertEquals("কেমন ", plan.commitText)
    }

    @Test
    fun englishPlanErasesExactlyTheTypedChars() {
        val plan = GlideCommitPolicy.planCommit(GlideMode.ENGLISH, editorCharsFromFirstKey = 1, word = "hello")
        assertEquals(1, plan.eraseEditorChars)
        assertEquals("hello ", plan.commitText)
    }

    @Test
    fun swapDeletesWordPlusSpace() {
        assertEquals(5 to "কেমনে ", GlideCommitPolicy.swapLengths("কেমন", "কেমনে"))
    }

    @Test
    fun altChipShapeAndNonGhostness() {
        val chip = GlideCommitPolicy.altChip("kemon", "কেমন")
        assertEquals("কেমন", chip.bengali)
        assertEquals("kemon", chip.phonetic)
        assertEquals(GlideCommitPolicy.GLIDE_ALT_SOURCE, chip.source)
        assertEquals(GlideCommitPolicy.GLIDE_ALT_TIER, chip.tier)
        // Alt chips are REAL chips — the blue commit highlight may land on
        // the first of them; never ghost-styled.
        assertFalse(TypedChipPolicy.isGhostTier(chip.tier))
    }
}
