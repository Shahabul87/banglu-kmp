package com.banglu.desktop.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S88 (Android parity — tester: abaro -> backspace -> retype o gave আবারও):
 * backspace into a committed Bengali word resumes roman forming on the
 * remaining fragment, so retyping produces the SAME word as typing it fresh.
 */
class S88BackspaceResumeEditorTest {
    private val engine = TestEngine.facade

    private fun newState() = EditorState(engine)

    private fun EditorState.type(s: String) {
        for (c in s) applyEdit(
            display.substring(0, cursor) + c + display.substring(cursor),
            cursor + 1
        )
    }

    private fun EditorState.settle() {
        if (!forming) return
        refine(formingRaw, engine.convert(formingRaw), engine.suggest(formingRaw))
    }

    private fun EditorState.backspaceOnce() {
        applyEdit(display.removeRange(cursor - 1, cursor), cursor - 1)
    }

    @Test
    fun backspaceIntoCommittedWordResumesRomanForming() {
        val s = newState()
        s.type("abaro")
        s.settle()
        s.type(" ")                       // commits the refined word + space
        val committedWord = s.committed.trim()
        s.backspaceOnce()                 // eat the space (no resume: space isn't Bengali)
        assertTrue(!s.forming, "space deletion must not resume")
        s.backspaceOnce()                 // eat into the word -> resume
        assertTrue(s.forming, "backspace into the word must resume forming")
        assertTrue(s.formingRaw.isNotEmpty() && s.formingRaw.all { it in 'a'..'z' })
        // Retype what completes the word: the display must equal the fresh
        // conversion of the same key — the consistency the tester asked for.
        s.type("o")
        s.settle()
        s.type(" ")
        assertEquals(committedWord, s.committed.trim(), "retyped word must match the fresh conversion")
    }

    @Test
    fun nonRoundTrippingWordFallsBackToPlainDeletion() {
        val s = newState()
        // Paste-style arbitrary committed text (never went through forming).
        s.applyEdit("xyz ", 4)
        s.backspaceOnce()
        s.backspaceOnce()
        assertTrue(!s.forming, "latin committed text never resumes")
        assertEquals("xy", s.committed)
    }
}
