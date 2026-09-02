package com.banglu.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S168 (audit P3-3): double-space punctuation may only replace a REAL
 * trailing space. Space → backspace → space inside the window used to eat a
 * letter because the timer alone decided.
 */
class S168DoubleSpacePolicyTest {

    @Test
    fun trailingSpaceInsideWindowIsReplaced() {
        assertTrue(DoubleSpacePolicy.replacesTrailingSpace(withinWindow = true, textBeforeCursor = { "আমি " }))
    }

    @Test
    fun noTrailingSpaceNeverReplaces() {
        assertFalse(DoubleSpacePolicy.replacesTrailingSpace(withinWindow = true, textBeforeCursor = { "আমি" }))
        assertFalse(DoubleSpacePolicy.replacesTrailingSpace(withinWindow = true, textBeforeCursor = { null }))
    }

    @Test
    fun outsideWindowNeverReplaces() {
        assertFalse(DoubleSpacePolicy.replacesTrailingSpace(withinWindow = false, textBeforeCursor = { "আমি " }))
    }
}
