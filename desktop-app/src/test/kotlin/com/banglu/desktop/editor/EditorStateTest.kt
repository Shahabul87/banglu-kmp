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
        s.dismissPopup()        // popup hidden: digits insert (visible popup would make '5' a pick)
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

    @Test
    fun candidatesIncludeTheRawFormForInlineEnglish() {
        val s = newState()
        s.type("kali")
        s.settle()
        assertTrue(s.popupVisible)
        assertTrue(s.candidates.contains("kali"))
    }

    @Test
    fun digitKeyPicksACandidateWhilePopupVisible() {
        val s = newState()
        s.type("kemon")
        s.settle()
        val idx = s.candidates.indexOf(s.formingBangla)    // primary is always listed (invariant #5)
        assertTrue(idx in 0..5, "primary not in candidates: ${s.candidates}")
        val expected = s.candidates[idx]
        s.type(('1' + idx).toString())
        assertEquals("$expected ", s.committed)             // pick commits word + space
        assertEquals("", s.formingRaw)
        // Picking the engine's own first choice must teach NOTHING (invariant #3).
        assertEquals(expected, engine.convert("kemon"))
    }

    @Test
    fun pickingANonPrimaryCandidateTeachesTheEngine() {
        val s = newState()
        s.type("kali")
        s.settle()
        val primary = s.formingBangla
        val other = s.candidates.first { it != primary && it != "kali" }
        s.pickCandidate(s.candidates.indexOf(other))
        assertEquals("$other ", s.committed)
        // The explicit pick becomes the new primary (in-memory preference).
        assertEquals(other, engine.convert("kali"))
    }

    @Test
    fun outOfRangeDigitInsertsWhilePopupVisible() {
        val tiny = object : EngineFacade {
            override fun instant(raw: String) = "ক"
            override fun convert(raw: String) = "ক"
            override fun suggest(raw: String, limit: Int) = listOf("ক", "খ")
            override fun reverse(bangla: String) = "k"
            override fun selected(raw: String, bangla: String, explicit: Boolean) {}
        }
        val s = EditorState(tiny)
        s.type("k")
        s.refine("k", "ক", listOf("ক", "খ"))
        assertTrue(s.popupVisible)
        s.type("5")   // candidates = ক, খ, raw "k" → index 4 out of range → digit inserts
        assertTrue(s.committed.endsWith("৫"), "got: '${s.committed}'")
        assertEquals("", s.formingRaw)
    }

    @Test
    fun escapeDismissesThePopupAndDigitsInsertAgain() {
        val s = newState()
        s.type("kemon")
        s.settle()
        s.dismissPopup()
        assertTrue(!s.popupVisible)
        s.type("3")
        assertTrue(s.committed.endsWith("৩"))              // digit, not a pick
    }

    @Test
    fun undoOfACommitRestoresTheFormingWord() {
        val s = newState()
        s.type("kemon")
        s.settle()
        s.type(" ")
        assertEquals("কেমন ", s.committed)
        s.undo()                                           // un-commit
        assertEquals("kemon", s.formingRaw)
        assertEquals("", s.committed)
        s.redo()
        assertEquals("কেমন ", s.committed)
        assertEquals("", s.formingRaw)
    }

    @Test
    fun pickCreatesExactlyOneUndoStep() {
        val s = newState()
        s.type("ami")
        s.settle()
        s.type(" ")                       // commit #1 → one snapshot
        s.type("kemon")
        s.settle()
        s.pickCandidate(s.candidates.indexOf(s.formingBangla))   // must add exactly ONE step
        s.undo()
        assertEquals("kemon", s.formingRaw)                      // back to forming kemon
        s.undo()                                                 // fails if pick double-pushed
        assertEquals("ami", s.formingRaw)
        assertEquals("", s.committed)
    }

    @Test
    fun undoRestoresDeletedText() {
        val s = newState()
        s.setAll("কেমন আছো")
        s.applyEdit("কেমন", 4)                             // selection-delete " আছো"
        assertEquals("কেমন", s.committed)
        s.undo()
        assertEquals("কেমন আছো", s.committed)
    }

    @Test
    fun clickToFixSwapsACommittedWordAndLearns() {
        val s = newState()
        s.setAll("আমি করছি এখন")
        val range = s.wordRangeAt(5)!!                     // inside করছি
        assertEquals("করছি", s.committed.substring(range.first, range.last + 1))
        val cands = s.candidatesForCommitted(range)
        assertTrue(cands.isNotEmpty())
        val other = cands.first { it != "করছি" }
        s.replaceCommitted(range, other)
        assertEquals("আমি $other এখন", s.committed)
        s.undo()
        assertEquals("আমি করছি এখন", s.committed)
    }

    @Test
    fun wordRangeAtReturnsNullOutsideBengaliWords() {
        val s = newState()
        s.setAll("কেমন আছো")
        assertEquals(null, s.wordRangeAt(4))               // the space
        assertEquals(0..3, s.wordRangeAt(2))
    }
}
