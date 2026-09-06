package com.banglu.keyboard

/**
 * S194: the spacebar must commit on release (a tap and a cursor drag look the
 * same on the way down), but fast two-thumb typists land the next letter before
 * lifting from space. Letters commit on their down, so the space arrived one
 * character late ("theq uick" — measured with injected touches on the S22; the
 * Samsung keyboard writes "the quick"). The keyboard root watches every pointer
 * down; when one lands while space is held, the space commits right then, in
 * press order, and the rest of the hold is inert — no second commit on release
 * and no cursor drag from a finger that already typed its space.
 *
 * Pure state: the root observer calls [onOtherPointerDown], the spacebar calls
 * the rest. The service's onSpace (double-space দাঁড়ি included) is the same call
 * either way.
 */
class SpaceRolloverPolicy {
    enum class Release { COMMIT, NOTHING }

    private var held = false
    private var committedEarly = false
    private var cursor = false

    /** True while the held space may still turn into a cursor drag. */
    val canEngageCursor: Boolean get() = held && !committedEarly && !cursor

    fun onSpaceDown() {
        held = true
        committedEarly = false
        cursor = false
    }

    /** Another pointer went down. Returns true when the held space must commit NOW. */
    fun onOtherPointerDown(): Boolean {
        if (!held || committedEarly || cursor) return false
        committedEarly = true
        return true
    }

    fun onCursorModeEngaged() {
        if (canEngageCursor) cursor = true
    }

    fun onSpaceUp(): Release {
        val out = if (held && !committedEarly && !cursor) Release.COMMIT else Release.NOTHING
        held = false
        committedEarly = false
        cursor = false
        return out
    }
}
