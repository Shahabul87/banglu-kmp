package com.banglu.keyboard

/**
 * S168 (audit P2-5): cursor-arrow steps in UTF-16 units over ONE user-visible
 * cluster, computed from a small text window around the caret. Shares the
 * S88/S136 cluster definition with backspace (BackspaceResume), so an arrow
 * never parks the caret inside কি or ক্ষ.
 */
object CursorStepPolicy {
    /** Look-ahead window the service fetches on each side of the caret. */
    const val WINDOW_CHARS = 32

    fun leftStep(textBeforeCursor: String): Int {
        if (textBeforeCursor.isEmpty()) return 0
        val boundary = BackspaceResume.previousUserVisibleClusterBoundary(textBeforeCursor)
        return (textBeforeCursor.length - boundary).coerceAtLeast(1)
    }

    fun rightStep(textAfterCursor: String): Int {
        if (textAfterCursor.isEmpty()) return 0
        // The first cluster ends at the largest prefix whose only cluster
        // boundary is 0 (i.e. the prefix IS one cluster).
        var best = Character.charCount(textAfterCursor.codePointAt(0))
        var index = best
        while (index < textAfterCursor.length) {
            val next = Character.offsetByCodePoints(textAfterCursor, index, 1)
            if (BackspaceResume.previousUserVisibleClusterBoundary(textAfterCursor, next) == 0) best = next else break
            index = next
        }
        return best
    }
}
