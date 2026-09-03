package com.banglu.keyboard

/**
 * S175: the roman edit point inside a re-composed word.
 *
 * A composing span cannot hold a mid-word caret (the caret sits after the
 * visible word — the transliteration-keyboard convention), so after a
 * mid-word resume (S174: caret inside a committed word, then a letter or a
 * backspace) the roman buffer remembers WHERE the user was editing. Letters
 * typed next are inserted there, backspace removes the letter before it, and
 * the point is dropped as soon as it reaches the end of the buffer. Pure —
 * the service owns the buffer.
 */
object MidWordCaret {

    data class State(val buffer: String, val insertAt: Int?)

    private fun validPoint(state: State): Int? =
        state.insertAt?.takeIf { it in 0 until state.buffer.length }

    fun insert(state: State, ch: Char): State {
        val at = validPoint(state) ?: return State(state.buffer + ch, null)
        val next = at + 1
        val buffer = state.buffer.substring(0, at) + ch + state.buffer.substring(at)
        return State(buffer, next.takeIf { it < buffer.length })
    }

    fun backspace(state: State): State {
        val at = validPoint(state)
        if (at == null || at == 0) return State(state.buffer.dropLast(1), null)
        val buffer = state.buffer.substring(0, at - 1) + state.buffer.substring(at)
        val next = at - 1
        return State(buffer, next.takeIf { it < buffer.length })
    }
}
