package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S175 (user: "try to type a letter wrong in the middle and try to fix
 * that"): after a mid-word re-composition the roman buffer carries an
 * insertion index, so the letters typed NEXT land where the caret was,
 * not at the end of the word.
 */
class S175MidWordCaretTest {

    @Test
    fun retypedLetterAfterMidWordBackspaceLandsAtTheEditPoint() {
        // kamon → কামন; caret after কা; backspace dropped কা → buffer "mon", edit point 0
        var state = MidWordCaret.State(buffer = "mon", insertAt = 0)
        state = MidWordCaret.insert(state, 'k')
        state = MidWordCaret.insert(state, 'e')
        assertEquals("kemon", state.buffer)
        assertEquals(2, state.insertAt)
    }

    @Test
    fun severalLettersInsertedMidWordStayInTypingOrder() {
        // tomar → তোমার; caret before র; type d, e → tomader
        var state = MidWordCaret.State(buffer = "tomar", insertAt = 4)
        state = MidWordCaret.insert(state, 'd')
        state = MidWordCaret.insert(state, 'e')
        assertEquals("tomader", state.buffer)
        assertEquals(6, state.insertAt)
    }

    @Test
    fun backspaceAtTheEditPointRemovesTheLetterBeforeIt() {
        var state = MidWordCaret.State(buffer = "tomader", insertAt = 6)
        state = MidWordCaret.backspace(state)
        assertEquals("tomadr", state.buffer)
        assertEquals(5, state.insertAt)
        state = MidWordCaret.backspace(state)
        assertEquals("tomar", state.buffer)
        assertEquals(4, state.insertAt)
    }

    @Test
    fun withoutAnEditPointTypingAppendsAndBackspaceDropsTheLast() {
        var state = MidWordCaret.State(buffer = "ami", insertAt = null)
        state = MidWordCaret.insert(state, 'r')
        assertEquals("amir", state.buffer)
        assertEquals(null, state.insertAt)
        state = MidWordCaret.backspace(state)
        assertEquals("ami", state.buffer)
    }

    @Test
    fun anEditPointAtTheEndOfTheWordIsNoEditPoint() {
        val state = MidWordCaret.insert(MidWordCaret.State("abc", insertAt = 3), 'd')
        assertEquals("abcd", state.buffer)
        assertEquals(null, state.insertAt)
    }

    @Test
    fun backspaceAtTheStartOfTheWordFallsBackToEndOfWordBehaviour() {
        val state = MidWordCaret.backspace(MidWordCaret.State("mon", insertAt = 0))
        assertEquals("mo", state.buffer)
        assertEquals(null, state.insertAt)
    }

    @Test
    fun aStaleIndexBeyondTheBufferIsIgnored() {
        val state = MidWordCaret.insert(MidWordCaret.State("ab", insertAt = 7), 'c')
        assertEquals("abc", state.buffer)
        assertEquals(null, state.insertAt)
    }
}

/**
 * S175b: the mid-word TYPING plan must not be refused just because the
 * rule-only instant preview cannot reproduce a word-internal ো (তোমা →
 * "toma" → তমা): the next keystroke re-renders the whole roman through the
 * engine anyway. Found on device — tomar|r + "de" plainly inserted the
 * letters and the space landed inside the word.
 */
class S175MidWordTypingGateTest {
    private val reverse: (String) -> String = { w ->
        mapOf("তোমা" to "toma", "র" to "r", "ভো" to "bho", "লা" to "la")[w] ?: error("unexpected $w")
    }
    // The real engine's rule-only preview: internal ো is lossy.
    private val lossyPreview: (String) -> String = { r ->
        mapOf("toma" to "তমা", "r" to "র", "bho" to "ভ", "la" to "লা")[r] ?: r
    }

    @Test
    fun typingInsideAWordWithInternalOKarStillPlans() {
        val plan = BackspaceResume.planForMidWordEdit("তোমা", "র ", reverse, lossyPreview)
        assertEquals(MidWordEditPlan(4, 1, "toma", "r", "তোমার"), plan)
    }

    @Test
    fun theVisibleWordAtPlanTimeIsTheOriginalText() {
        val plan = BackspaceResume.planForMidWordEdit("ভো", "লা", reverse, lossyPreview)!!
        assertEquals("ভোলা", plan.visibleWord)
    }
}

/**
 * S175c (device, A2): deleting a cluster inside তমাদের left the prefix ত,
 * whose lone reversal is "t" — the inherent vowel was lost and the retyped
 * "ma" built "tmader". On the delete path the whole original word is still
 * known, so the prefix roman comes from reversing the whole word and
 * stripping the reversal of the deleted tail.
 */
class S175MidWordBackspaceRomanTest {
    private val reverse: (String) -> String = { w ->
        mapOf("তমাদের" to "tomader", "মাদের" to "mader", "দের" to "der", "ত" to "t", "তমা" to "toma")[w] ?: error("unexpected $w")
    }
    private val preview: (String) -> String = { r -> mapOf("toder" to "তদের", "tder" to "ৎদের")[r] ?: r }

    @Test
    fun prefixRomanKeepsTheInherentVowelOfTheOriginalWord() {
        val plan = BackspaceResume.planForMidWordBackspace("তমা", "দের ", reverse, preview)!!
        assertEquals("to", plan.romanPrefix)
        assertEquals("der", plan.romanSuffix)
        assertEquals("তদের", plan.visibleWord)
    }

    @Test
    fun deletePlanIsRefusedWhenTheRecomposedWordDoesNotEcho() {
        val lossy: (String) -> String = { r -> if (r == "toder") "টডের" else preview(r) }
        assertEquals(null, BackspaceResume.planForMidWordBackspace("তমা", "দের ", reverse, lossy))
    }
}
