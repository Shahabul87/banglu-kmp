package com.banglu.keyboard

/**
 * S166 (iQOO Neo 9 field report): full-screen gestures + "hide indicator
 * bar" makes the phone report a ZERO navigation inset, so the bottom key
 * row sat flush against the screen edge — inside the bottom system-gesture
 * zone (the OEM's floating IME-switch globe overlaid the !#1 key, and the
 * "swipe to switch apps" edge gesture competed with key taps).
 *
 * Google's contract for this: content that must stay tappable respects
 * navigationBars ∪ tappableElement, and on gesture navigation the bottom
 * edge belongs to the system even when the pill is hidden. So:
 *  - base = max(navigationBars, tappableElement) — free correctness;
 *  - portrait floor = 14dp on GESTURE navigation (mode 2), 3dp otherwise
 *    (the S30 rule: never pad dead space on phones that don't need it);
 *  - landscape keeps the reported insets untouched (S117 height budget).
 */
object GestureNavInsetPolicy {

    /** Settings.Secure "navigation_mode" value for full-screen gestures. */
    const val NAV_MODE_GESTURE = 2

    fun bottomPaddingDp(
        navInsetDp: Float,
        tappableInsetDp: Float,
        navigationMode: Int,
        landscape: Boolean,
    ): Float {
        val base = maxOf(navInsetDp, tappableInsetDp)
        if (landscape) return base
        val floor = if (navigationMode == NAV_MODE_GESTURE) 14f else 3f
        return maxOf(base, floor)
    }
}
