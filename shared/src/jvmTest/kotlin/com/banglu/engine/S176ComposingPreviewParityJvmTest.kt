package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S176 (tester screenshots, 2026-09-03): the editor showed হৃয্দ্রগেনের /
 * ওক্স্য্গেনের / হ্রিদজন্ত্র while the strip's blue chip read হাইড্রোজেনের /
 * অক্সিজেনের / হৃদযন্ত্র. The composing preview is a mirror of the commit
 * layers and had drifted again (the S83 lesson): it never reached the
 * productive-suffix, suffix-stripped-dictionary and root-decomposition
 * layers, fell to the rule-only floor, and the S143 rescue then rendered the
 * nearest English STEM (telephoner → টেলিফোন). Invariant 2: preview == commit.
 */
class S176ComposingPreviewParityJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun inflectedLoanwordsAndCompoundsPreviewWhatSpaceCommits() {
        for (key in listOf(
            "hydrogener", "oxygener", "nitrogener", "carboner", "computerer", "teacherer",
            "telephoner", "microphoner", "doctorer", "hridjontro", "shororipur",
        )) {
            assertEquals(
                fold(engine.convertWord(key).bengali),
                fold(engine.convertForComposing(key).bengali),
                "preview must equal commit for '$key'",
            )
        }
    }

    @Test
    fun theScreenshotWordsPreviewTheRealWord() {
        assertEquals("হাইড্রোজেনের", fold(engine.convertForComposing("hydrogener").bengali))
        assertEquals("অক্সিজেনের", fold(engine.convertForComposing("oxygener").bengali))
        assertEquals("হৃদযন্ত্র", fold(engine.convertForComposing("hridjontro").bengali))
    }

    @Test
    fun previewNeverShowsRawLatinWhileTyping() {
        // Every 4+ letter prefix of the screenshot words keeps a Bangla echo.
        for (word in listOf("hydrogener", "oxygener", "hridjontro")) {
            for (n in 4..word.length) {
                val p = engine.convertForComposing(word.substring(0, n)).bengali
                assertTrue(p.isNotEmpty() && p.none { it in 'a'..'z' }, "prefix '${word.substring(0, n)}' previewed '$p'")
            }
        }
    }
}
