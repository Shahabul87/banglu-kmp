package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class S166GestureNavInsetPolicyTest {

    @Test
    fun hiddenPillGestureNavGetsTheFloor() {
        // The iQOO Neo 9 config: gestures + hidden indicator = 0 insets.
        assertEquals(14f, GestureNavInsetPolicy.bottomPaddingDp(0f, 0f, 2, false))
    }

    @Test
    fun visiblePillKeepsItsRealInset() {
        // Samsung-style gesture nav with the pill: real inset > floor wins.
        assertEquals(16f, GestureNavInsetPolicy.bottomPaddingDp(16f, 16f, 2, false))
    }

    @Test
    fun threeButtonNavKeepsTheS30Rule() {
        // 3-button phones report 0 here (IME sits above the bar) — only the
        // 3dp breathing floor, never a dead strip (the S30 regression).
        assertEquals(3f, GestureNavInsetPolicy.bottomPaddingDp(0f, 0f, 0, false))
        assertEquals(3f, GestureNavInsetPolicy.bottomPaddingDp(0f, 0f, 1, false))
    }

    @Test
    fun tappableElementIsHonored() {
        // Some OEMs report the switcher zone via tappableElement only.
        assertEquals(20f, GestureNavInsetPolicy.bottomPaddingDp(0f, 20f, 0, false))
    }

    @Test
    fun landscapeKeepsReportedInsetsUntouched() {
        assertEquals(0f, GestureNavInsetPolicy.bottomPaddingDp(0f, 0f, 2, true))
        assertEquals(9f, GestureNavInsetPolicy.bottomPaddingDp(9f, 0f, 2, true))
    }
}
