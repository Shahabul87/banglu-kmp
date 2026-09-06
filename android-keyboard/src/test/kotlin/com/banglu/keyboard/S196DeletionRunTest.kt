package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S196 (user: "bad deleting experience … engine shows horrified words"): inside a
 * resumed word every backspace removes ONE visible cluster and re-derives the
 * roman from what remains; the preview never shows a conversion of a roman the
 * user never typed.
 */
class S196DeletionRunTest {
    private val reverse: (String) -> String = {
        mapOf(
            "বিশ্ববিদ্যা" to "bishwabiddya", "বিশ্ববিদ্" to "bishwabidd", "বিশ্ববি" to "bishwabi",
            "তোমা" to "toma", "তো" to "to", "বা" to "ba", "দ" to "d", "" to "",
        )[it] ?: "?"
    }

    @Test
    fun endOfWordStepDropsOneClusterAndRederivesTheRoman() {
        val step = BackspaceResume.planForDeletionStep("বিশ্ববিদ্যাল", null, "", reverse)!!
        assertEquals("বিশ্ববিদ্যা", step.visible)
        assertEquals("bishwabiddya", step.roman)
        assertNull(step.insertAt)
        assertEquals("বিশ্ববিদ্যা".length, step.prefixVisibleLength)
    }

    @Test
    fun aConjunctIsOneVisibleCluster() {
        // বিশ্ববিদ্যা → drop "দ্যা" as one user-visible cluster
        val step = BackspaceResume.planForDeletionStep("বিশ্ববিদ্যা", null, "", reverse)!!
        assertEquals("বিশ্ববি", step.visible)
        assertEquals("bishwabi", step.roman)
    }

    @Test
    fun midWordStepKeepsTheSuffixAndTheEditPoint() {
        val step = BackspaceResume.planForDeletionStep("তোমাদ", "তোমা".length, "d", reverse)!!
        assertEquals("তোদ", step.visible)
        assertEquals("tod", step.roman)
        assertEquals(2, step.insertAt)
        assertEquals("তো".length, step.prefixVisibleLength)
    }

    @Test
    fun nothingBeforeTheEditPointRefuses() {
        assertNull(BackspaceResume.planForDeletionStep("দ", 0, "d", reverse))
        assertNull(BackspaceResume.planForDeletionStep("", null, "", reverse))
    }

    @Test
    fun unusableRomanRefusesSoTheCallerFallsBack() {
        val bad: (String) -> String = { "?" }
        assertNull(BackspaceResume.planForDeletionStep("বিশ্ববিদ্যাল", null, "", bad))
    }
}
