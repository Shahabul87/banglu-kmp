package com.banglu.keyboard

/**
 * S168 (audit P3-3): double-space punctuation (দাঁড়ি / period) replaces the
 * previous space only when the editor really ends with one. The timer alone
 * let space → backspace → space inside the window delete a letter.
 * [textBeforeCursor] is a lambda so the InputConnection round-trip happens
 * only inside the double-tap window.
 */
object DoubleSpacePolicy {
    fun replacesTrailingSpace(withinWindow: Boolean, textBeforeCursor: () -> CharSequence?): Boolean {
        if (!withinWindow) return false
        val before = textBeforeCursor() ?: return false
        return before.isNotEmpty() && before[before.length - 1] == ' '
    }
}
