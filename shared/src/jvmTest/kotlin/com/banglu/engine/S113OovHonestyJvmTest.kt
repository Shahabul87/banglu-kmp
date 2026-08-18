package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S113 pins: OOV graceful degradation (user directive: "if a word is not in
 * the dictionary the engine throws garbage that could terrify the user").
 *
 * 1. Onset-integrity floor: a fuzzy-band result whose roman onset class
 *    disagrees with the typed key never commits — the deterministic
 *    transliteration is primary and the fuzzy word rides the strip. Kills
 *    the negation-inversion class (oswabhabikobhabei used to commit
 *    স্বাভাবিকভাবেই — the অ- DROPPED, meaning inverted).
 * 2. Decisive-evidence store arbitration (the S27/S109 ato residual): a
 *    junk-frequency dictionary exact hit no longer silences a 2x-stronger
 *    tier-A store word.
 */
class S113OovHonestyJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun negationInversionNeverCommits() {
        val r = engine.convertWord("oswabhabikobhabei")
        assertEquals("অস্বাভাবিকভাবেই", r.bengali)
        // The honest floor is LOW confidence by design — the IME treats it
        // as raw-transliteration territory, never a confident correction.
        assertTrue(r.confidence < 0.7, "floor must stay low-confidence, got ${r.confidence}")
    }

    @Test
    fun sameOnsetRescuesAreUntouched() {
        // The floor keys on onset class — legitimate fuzzy rescues with the
        // typed onset keep working (the S22/S87 machinery).
        assertEquals("অবিশ্বাস্য", engine.convertWord("obisasso").bengali)
        assertEquals("বুঝতে পারছিনা", engine.convertWord("bujteparcina").bengali)
    }

    @Test
    fun junkDictExactNoLongerSilencesTheStore() {
        // ato: আটো@25 (extended dict) vs অটো@77 (tier-A store) — the S27
        // class documented as residual in S109 and S110, now arbitrated by
        // decisive evidence (dict < 40, store >= 2x).
        assertEquals("অটো", engine.convertWord("ato").bengali)
        val strip = engine.getSuggestions("ato", 6).map { it.bengali }
        assertTrue("অতো" in strip, "অতো must ride the ato strip: $strip")
    }

    @Test
    fun bookVocabularyIsNowFirstClass() {
        // db 3.9.4 book_lexicon spot pins (S110 corpus, count >= 3 words).
        // Keys SELF-DERIVED (the utpotti lesson): hand-spellings can hit a
        // legitimate orthographic twin (songkoron -> সংকরণ, the anusvara
        // form) — the pin asserts the book form is reachable, primary or
        // top-6.
        for (word in listOf("সঙ্করণ", "প্রজাতিরা", "গৃহপালনাধীন")) {
            val key = com.banglu.engine.util.ReverseTransliterator
                .reverseWord(word).lowercase()
            val hits = listOf(engine.convertWord(key).bengali) +
                engine.getSuggestions(key, 6).map { it.bengali }
            assertTrue(word in hits, "$word (typed '$key') missing: $hits")
        }
    }
}
