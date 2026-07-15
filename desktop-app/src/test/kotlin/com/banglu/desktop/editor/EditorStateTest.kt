package com.banglu.desktop.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorStateTest {
    private val engine = TestEngine.facade

    @Test
    fun engineFacadeConvertsThroughTheRealStore() {
        assertEquals("কেমন", engine.convert("kemon"))
        assertTrue(engine.suggest("kemon").contains("কেমন"))
        assertTrue(engine.instant("kemon").isNotEmpty())
    }

    private fun newState() = EditorState(engine)

    /** Simulates the UI: each char arrives via applyEdit on the display text. */
    private fun EditorState.type(s: String) {
        for (c in s) applyEdit(
            display.substring(0, cursor) + c + display.substring(cursor),
            cursor + 1
        )
    }

    /** Simulates the async refine landing for the current forming word. */
    private fun EditorState.settle() {
        if (!forming) return
        refine(formingRaw, engine.convert(formingRaw), engine.suggest(formingRaw))
    }

    @Test
    fun lettersFormLiveAndSpaceCommitsTheRefinedWord() {
        val s = newState()
        s.type("kemon")
        assertEquals("kemon", s.formingRaw)
        assertTrue(s.formingBangla.isNotEmpty())          // instant preview visible
        s.settle()
        assertEquals("কেমন", s.formingBangla)              // refined in place
        assertEquals("কেমন", s.display)
        s.type(" ")
        assertEquals("কেমন ", s.committed)                 // WYSIWYG commit
        assertEquals("", s.formingRaw)
    }

    @Test
    fun spaceBeforeRefineCommitsExactlyTheVisiblePreview() {
        val s = newState()
        s.type("kemon")
        val visible = s.formingBangla                      // refine never lands
        s.type(" ")
        assertEquals("$visible ", s.committed)
    }

    @Test
    fun staleRefineIsIgnoredAfterMoreTyping() {
        val s = newState()
        s.type("ke")
        s.refine("ke", "কে", listOf("কে"))                 // late result for old raw…
        s.type("mon")
        s.refine("ke", "কে", listOf("কে"))                 // …must not clobber "kemon"
        assertEquals("kemon", s.formingRaw)
        s.settle()
        assertEquals("কেমন", s.formingBangla)
    }

    @Test
    fun setAllReplacesTheDocument() {
        val s = newState()
        s.type("kemon")
        s.setAll("আমার প্রিয় বন্ধু")
        assertEquals("আমার প্রিয় বন্ধু", s.committed)
        assertEquals("", s.formingRaw)
        assertEquals(s.committed.length, s.cursor)
    }

    @Test
    fun backspaceEditsTheRawBanglishNotTheOutput() {
        val s = newState()
        s.type("kali")
        s.applyEdit(s.display.dropLast(1), s.cursor - 1)   // one backspace
        assertEquals("kal", s.formingRaw)
        s.settle()
        assertEquals("কাল", s.formingBangla)
    }

    @Test
    fun backspaceOnCommittedTextDeletesOneChar() {
        val s = newState()
        s.setAll("কেমন ")
        s.applyEdit("কেমন", 4)
        assertEquals("কেমন", s.committed)
    }

    @Test
    fun doubleSpaceMakesDari() {
        val s = newState()
        s.type("kemon")
        s.type(" ")
        s.type(" ")
        assertTrue(s.committed.endsWith("। "), "got: '${s.committed}'")
        s.type(" ")                                        // third space: plain space
        assertTrue(s.committed.endsWith("।  "))
    }

    @Test
    fun digitsCommitTheFormingWordAndBecomeBengali() {
        val s = newState()
        s.type("kemon")
        s.settle()
        s.type("5")
        assertEquals("কেমন৫", s.committed)
        s.banglaDigits = false
        s.type("7")
        assertEquals("কেমন৫7", s.committed)
    }

    @Test
    fun punctuationAndNewlineCommitFirst() {
        val s = newState()
        s.type("kemon")
        s.settle()
        s.type(",")
        assertEquals("কেমন,", s.committed)
        s.type("acho")
        s.settle()
        s.type("\n")
        assertTrue(s.committed.endsWith("আছো\n"))
    }

    @Test
    fun pasteCommitsFormingThenAcceptsVerbatim() {
        val s = newState()
        s.type("kemon")
        s.settle()
        val pasted = s.display + " আছো বন্ধু"
        s.applyEdit(pasted, pasted.length)                 // multi-char change
        assertEquals("কেমন আছো বন্ধু", s.committed)
        assertEquals("", s.formingRaw)
    }
}
