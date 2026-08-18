package com.banglu.keyboard

import com.banglu.engine.SmartEngine
import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * S109 (tester 2026-08-17): editing a committed word by typing — tap the
 * cursor mid-word and press 'a'/'o' to add a kar — used to open a FRESH
 * composition, so the vowel came out as a full letter (দ|বা + 'a' showed
 * দআবা instead of দাবা). planForTyping resumes roman composition on the
 * whole prefix before the cursor, gated on the same exact rule-layer
 * round-trip as the S88 backspace resume.
 */
class S109TypingResumeTest {

    private val engine = SmartEngine().also { it.initializeSync() }

    private fun plan(before: String) = BackspaceResume.planForTyping(
        textBeforeCursor = before,
        reverse = { ReverseTransliterator.reverseWord(it) },
        instantPreview = { engine.convertForInstantPreview(it) },
    )

    @Test
    fun midWordVowelBecomesKar() {
        // Word দবা with the cursor after দ; typing 'a' must produce দা…,
        // not দ + আ. The prefix resumes as roman "d".
        val p = plan("আমি দ")
        assertNotNull(p, "Bengali prefix under the cursor must resume")
        assertEquals("দ".length, p.deleteLength)
        assertEquals("d", p.romanBuffer)
        assertEquals("দ", p.visibleFragment)
        assertEquals("দা", engine.convertForInstantPreview(p.romanBuffer + "a"))
    }

    @Test
    fun appendAfterCommittedWordRecomposesWholeWord() {
        // Typing 'o' directly after committed আবার (no space) re-converts
        // the whole word — same key, same word as typing abaro fresh.
        val p = plan("সে আবার")
        assertNotNull(p)
        assertEquals("আবার".length, p.deleteLength)
        assertEquals("abar", p.romanBuffer)
        assertEquals(
            engine.convertWord("abaro").bengali,
            engine.convertWord(p.romanBuffer + "o").bengali
        )
    }

    @Test
    fun loneVowelResumes() {
        // Committed আ then typing 'r' must become আর, not আ + র.
        val p = plan("আ")
        assertNotNull(p)
        assertEquals("a", p.romanBuffer)
        assertEquals("আর", engine.convertForInstantPreview(p.romanBuffer + "r"))
    }

    @Test
    fun noPlanAfterSpaceOrLatin() {
        assertNull(plan("আবার "), "space before cursor = new word, no resume")
        assertNull(plan("hello"), "Latin text never resumes")
        assertNull(plan(""), "empty editor never resumes")
    }

    @Test
    fun nonRoundTrippingPrefixFallsBack() {
        // A prefix whose reverse key does not reproduce it exactly must
        // return null — the caller keeps plain fresh composition, so the
        // gate can never make editing WORSE than before.
        val weird = "ক্ক্ক"
        val roman = ReverseTransliterator.reverseWord(weird).lowercase()
        if (engine.convertForInstantPreview(roman) != weird) {
            assertNull(plan(weird))
        }
    }
}
