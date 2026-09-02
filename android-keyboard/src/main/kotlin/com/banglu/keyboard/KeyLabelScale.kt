package com.banglu.keyboard

/**
 * S168 (audit P2-2): keyboard glyphs are sized by the keyboard's own
 * font-size setting, never by the system font scale — the key caps, strip
 * height and number-row hint slots are fixed dp, so system-scaled sp clipped
 * "EN" to "E" and stacked hints over digits at 1.3x (Gboard behaves the same
 * way: its keys ignore the system font size). Pure math, no Compose.
 */
object KeyLabelScale {
    /** The sp to request so the rendered size equals [intendedSp] after the
     *  system multiplies by [systemFontScale]. */
    fun systemIndependentSp(intendedSp: Float, systemFontScale: Float): Float =
        if (systemFontScale > 0f) intendedSp / systemFontScale else intendedSp
}
