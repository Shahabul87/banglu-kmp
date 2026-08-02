package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S67 regression (tester round: "typing Bengali, getting English").
 *
 * Root cause on the real store was NOT the commit path — probing showed it
 * already resolves the whole complaint class to Bangla script (name→নামে via
 * the seed layer, press→প্রেস, size→সাইজ). The engine-side bug was the
 * COMPOSING mirror order: the preview ran its corpus check before the
 * English branch, so a rare corpus word squatting on an English key won the
 * preview while Space committed the loanword (size previewed শিজে, committed
 * সাইজ; user previewed উসের, committed ইউজার) — a WYSIWYG break the tester
 * experiences as the keyboard swapping their word. Two mirrors added:
 *  1. convertForComposingCore: EnglishDetector branch (curated → lexicon)
 *     at commit precedence, before the corpus check (≥4 chars).
 *  2. convertForComposing wrapper: the S24 4x-frequency-margin loanword
 *     flip (user/উসের class), identical conditions to convertWord's wrapper.
 * Raw-Latin passthrough is deliberately NOT mirrored (V2 parity law: the
 * live preview never shows raw Latin mid-word).
 */
class S67EnglishArbitrationParityJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun commit(word: String) = engine.convertWord(word).bengali
    private fun preview(word: String) = engine.getCompositionPreview(word)

    private fun assertParity(word: String, expected: String) {
        assertEquals(expected, commit(word), "commit($word)")
        assertEquals(expected, preview(word), "preview($word) must match commit")
    }

    @Test
    fun englishLexiconWordsPreviewWhatSpaceCommits() {
        // The S67 fix class: preview used to show the corpus squatter.
        assertParity("size", "সাইজ")
        assertParity("user", "ইউজার")   // 4x-margin wrapper mirror
        assertParity("press", "প্রেস")
        assertParity("cost", "কস্ট")
        assertParity("color", "কালার")
        assertParity("guide", "গাইড")
    }

    @Test
    fun romanizedBengaliStaysBengaliOnBothPaths() {
        // name -> নামে class: the seed/dictionary layer owns these keys ahead
        // of the English branch; they must never become loanwords.
        assertParity("name", "নামে")
        assertParity("call", "কল")
        assertParity("form", "ফর্ম")
        assertParity("group", "গ্রুপ")
        assertParity("dam", "দাম")
        assertParity("chine", "চিনে")
    }

    @Test
    fun vettedLoanwordIntentUnchanged() {
        // ENGLISH_PRIMARY_INTENT pins — S24/S26/S52/S56 decisions.
        assertParity("time", "টাইম")
        assertParity("line", "লাইন")
        assertParity("screenshot", "স্ক্রিনশট")
        assertParity("hotel", "হোটেল")
        // Junk-rescue loanword class (S55) stays converged too.
        assertParity("callback", "কলব্যাক")
        assertParity("motivation", "মোটিভেশন")
    }
}
