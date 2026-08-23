package com.banglu.winime.ui

/** A point in AWT user-space pixels (physical ÷ display scale). */
internal data class AnchorPoint(val x: Int, val y: Int)

/** A window rectangle in the same space. */
internal data class AnchorRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun contains(p: AnchorPoint): Boolean = p.x in left..right && p.y in top..bottom

    /** Minimized/ghost windows report degenerate rects — never anchor to one. */
    val isUsable: Boolean get() = right - left >= MIN_USABLE && bottom - top >= MIN_USABLE

    private companion object {
        const val MIN_USABLE = 50
    }
}

/**
 * S132: decides where the suggestion strip sits, given everything the Win32
 * layer could find out. Pure — the whole contract is pinned in
 * StripAnchorTest on the Mac dev machine.
 *
 * Priority, and why:
 *  1. **The caret.** Ground truth wherever the app exposes one (Word,
 *     Notepad, classic Win32 edit controls). The strip rides under the text
 *     like the web editor's.
 *  2. **The last anchor, if it was taken in THIS window.** Mid-word the
 *     strip must hold still, not chase the mouse across the document; a
 *     last anchor stamped with a different window rect is dead — focus has
 *     moved and it says nothing about where text is now.
 *  3. **The mouse, if it is inside the focused window.** In caret-less apps
 *     (Chrome, Electron, PowerPoint) the user's last click put the mouse at
 *     the very field they are typing into.
 *  4. **The focused window's bottom-center.** The mouse is parked outside
 *     (the field report: over the taskbar) — a deterministic spot inside
 *     the app beats wherever the pointer happens to lie.
 *  5. The bare mouse, when not even a window rect is known.
 */
internal object StripAnchor {

    /** How far above the window's bottom edge the fallback anchor sits. */
    const val WINDOW_BOTTOM_MARGIN = 96

    fun plan(
        caret: AnchorPoint?,
        mouse: AnchorPoint?,
        window: AnchorRect?,
        last: Pair<AnchorPoint, AnchorRect>?,
    ): AnchorPoint? {
        if (caret != null) return caret
        val rect = window?.takeIf { it.isUsable }
        if (rect != null) {
            if (last != null && last.second == rect && rect.contains(last.first)) return last.first
            if (mouse != null && rect.contains(mouse)) return mouse
            return AnchorPoint((rect.left + rect.right) / 2, rect.bottom - WINDOW_BOTTOM_MARGIN)
        }
        return mouse ?: last?.first
    }
}
