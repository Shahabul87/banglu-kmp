package com.banglu.keyboard

/**
 * S168 (closed-testing audit P1-1): what backspace does when the host editor
 * has a RANGE selection.
 *
 * `InputConnection.deleteSurroundingText` deletes around the selection and
 * leaves the selected range intact, so every "delete before cursor" path in
 * the service silently ate the character BEFORE a selected word. A range
 * selection must be replaced with the empty string instead (the same
 * `commitText("", 1)` the host itself uses for typing over a selection).
 *
 * Pure decision only — the service owns the InputConnection and the tracked
 * selection it feeds in from `onUpdateSelection` / `EditorInfo`.
 */
object SelectionEditPolicy {

    enum class BackspacePlan { DELETE_BEFORE_CURSOR, DELETE_SELECTION }

    /** Unknown (-1) or collapsed selections fall through to the normal paths. */
    fun backspacePlan(selStart: Int, selEnd: Int): BackspacePlan =
        if (selStart >= 0 && selEnd >= 0 && selStart != selEnd) BackspacePlan.DELETE_SELECTION
        else BackspacePlan.DELETE_BEFORE_CURSOR
}
