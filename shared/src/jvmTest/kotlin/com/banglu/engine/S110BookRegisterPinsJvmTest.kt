package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S110 pins: literary/book register (source: user-collected book corpus,
 * test-bangla.md — the Bengali Origin of Species translation). The study
 * measured in-dictionary book words at 97.3% primary / 99.9% top-6; these
 * pins freeze that behavior for the words a book reader is most likely to
 * type, spanning the structural classes (conjuncts, vowel-initial,
 * chandrabindu, long tatsama).
 *
 * The typed key is DERIVED from the word (canonical romanization) so the
 * pin covers ReverseTransliterator + the full conversion pipeline together
 * — if either drifts, the round trip breaks here.
 */
class S110BookRegisterPinsJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private val primaryPins = listOf(
        // high-frequency book prose
        "এবং", "প্রজাতি", "প্রাকৃতিক", "নির্বাচন", "বৈশিষ্ট্য",
        "পরিবর্তন", "সংরক্ষণ", "সংগ্রাম", "উদ্ভিদ", "পতঙ্গ",
        "বিজ্ঞান", "ইতিহাস", "সাধারণ", "বিভিন্ন", "গুরুত্বপূর্ণ",
        // conjunct-heavy tatsama
        "সম্পূর্ণ", "প্রশ্ন", "উত্তর", "প্রকৃতি", "স্বাভাবিক",
        "সম্ভাবনা", "প্রতিষ্ঠান", "সংখ্যা", "ব্যক্তি", "শক্তিশালী",
        // vowel-initial literary
        "অবস্থা", "অনুবাদ", "উৎপত্তি", "আবিষ্কার", "উপস্থিত",
        // long derivations
        "পরিস্থিতি", "প্রয়োজনীয়", "কালানুক্রমিক",
    )

    @Test
    fun bookRegisterRoundTripsToPrimary() {
        val failures = mutableListOf<String>()
        for (word in primaryPins) {
            val key = ReverseTransliterator.reverseWord(word).lowercase()
            val got = engine.convertWord(key).bengali
            if (ReverseTransliterator.foldNukta(got) != ReverseTransliterator.foldNukta(word)) {
                failures += "$word (typed '$key') -> $got"
            }
        }
        assertTrue(failures.isEmpty(), "book-register primary regressions:\n" + failures.joinToString("\n"))
    }

    @Test
    fun chandrabinduBookWordsReachTop6() {
        for (word in listOf("দাঁড়িয়ে", "কাঁচা", "পাঁচ")) {
            val key = ReverseTransliterator.reverseWord(word.replace("ঁ", "")).lowercase()
            val hits = (listOf(engine.convertWord(key).bengali) +
                engine.getSuggestions(key, 6).map { it.bengali })
                .map { ReverseTransliterator.foldNukta(it) }
            assertTrue(
                ReverseTransliterator.foldNukta(word) in hits,
                "$word (typed nasal-dropped '$key') missing from top-6: $hits"
            )
        }
    }

    @Test
    fun sentenceParseHoldsOnBookProse() {
        // Whole-sentence path (parse preserves whitespace, converts tokens).
        // Keys derived like the word pins. NOTE (S110 study finding): the
        // hand-spelling "utpotti" resolves to the corpus twin উত্পত্তি
        // (virama-ta) rather than উৎপত্তি (khanda-ta) — the khanda-ta twin
        // class is documented in the study report as a future fold/promote
        // candidate, so উৎপত্তি deliberately stays out of this sentence.
        val words = listOf("প্রাকৃতিক", "নির্বাচন", "এবং", "প্রজাতির", "সংরক্ষণ")
        val typed = words.joinToString(" ") {
            ReverseTransliterator.reverseWord(it).lowercase()
        }
        assertEquals(words.joinToString(" "), engine.parse(typed))
    }
}
