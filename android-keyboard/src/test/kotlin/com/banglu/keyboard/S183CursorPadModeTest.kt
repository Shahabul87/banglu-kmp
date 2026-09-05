package com.banglu.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * S183 — the cursor pad is a transient layer (like the clipboard): it never
 * becomes the remembered letter mode, and every exit returns to the letter
 * mode the user was in (an EN user gets EN back, not the BN default).
 */
class S183CursorPadModeTest {
    @Test
    fun cursorPadIsNotALetterMode() {
        // A transient mode collapses away; a letter mode is kept as-is.
        assertEquals(KeyboardMode.BANGLU, LanguageModePolicy.collapseTransient(KeyboardMode.CURSOR, KeyboardMode.BANGLU))
        assertFalse(LanguageModePolicy.collapseTransient(KeyboardMode.CURSOR, KeyboardMode.ENGLISH) == KeyboardMode.CURSOR)
    }

    @Test
    fun collapseFromCursorPadReturnsTheLetterMode() {
        assertEquals(KeyboardMode.ENGLISH, LanguageModePolicy.collapseTransient(KeyboardMode.CURSOR, KeyboardMode.ENGLISH))
        assertEquals(KeyboardMode.BANGLU, LanguageModePolicy.collapseTransient(KeyboardMode.CURSOR, KeyboardMode.BANGLU))
    }

    @Test
    fun globeToggleFromCursorPadLeavesTheLetterModeAlone() {
        val r = LanguageModePolicy.globeToggle(KeyboardMode.CURSOR, KeyboardMode.ENGLISH)
        assertEquals(KeyboardMode.ENGLISH, r.mode)
        assertEquals(KeyboardMode.ENGLISH, r.letterMode)
    }
}
