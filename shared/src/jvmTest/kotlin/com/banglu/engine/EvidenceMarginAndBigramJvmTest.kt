package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S8/S9 acceptance (y-drop + homophone round, db 3.6.0).
 *
 * S8a — within-tier evidence margin: a rare canonical owner squatting on a
 *       common typing loses the key to a ~25x-more-used alias claimant
 *       (songkha → সংখ্যা over সংখা), while modest gaps keep the canonical
 *       owner (jon → জন, gan → গান).
 * S8b — vowel-glide y-drop aliases (phebruari → ফেব্রুয়ারি).
 * S9  — corpus bigrams give homophones real context evidence
 *       (ilish mach → মাছ, test mach → ম্যাচ).
 */
class EvidenceMarginAndBigramJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun primary(input: String): String = engine.convertWord(input).bengali

    // ── S8a evidence margin ─────────────────────────────────────────────

    @Test
    fun songkha_aliasOutranksRareCanonicalSquatter() =
        assertEquals("সংখ্যা", primary("songkha"))

    @Test
    fun madhome_yaPhalaDropReachesCommonWord() {
        val strip = engine.getSuggestions("madhome", 5).map { it.bengali }
        assertTrue("মাধ্যমে" in strip, "expected মাধ্যমে in strip, got $strip")
    }

    @Test
    fun jon_modestGapKeepsCanonicalOwner() = assertEquals("জন", primary("jon"))

    @Test
    fun gan_homophoneKeepsCanonicalWithoutContext() = assertEquals("গান", primary("gan"))

    // ── S8b vowel-glide y-drop ──────────────────────────────────────────

    @Test
    fun phebruari_glideDropReachesMonthName() {
        val strip = engine.getSuggestions("phebruari", 5).map { it.bengali }
        assertTrue("ফেব্রুয়ারি" in strip, "expected ফেব্রুয়ারি in strip, got $strip")
    }

    // ── S9 bigram context disambiguation ────────────────────────────────

    @Test
    fun mach_contextPicksFishAfterIlish() {
        val r = engine.rerankWithPreviousContext("ইলিশ", engine.convertWord("mach"))
        assertEquals("মাছ", r.bengali)
    }

    @Test
    fun mach_contextPicksMatchAfterTest() {
        val r = engine.rerankWithPreviousContext("টেস্ট", engine.convertWord("mach"))
        assertEquals("ম্যাচ", r.bengali)
    }

    // ── S9b: composing preview honors context ───────────────────────────

    @Test
    fun composingPreview_contextFlipsHomophone() {
        assertEquals("ম্যাচ", engine.convertForComposing("mach", "টেস্ট").bengali)
        assertEquals("মাছ", engine.convertForComposing("mach", "ইলিশ").bengali)
        assertEquals("মাছ", engine.convertForComposing("mach").bengali)
        // No real bigram evidence -> preview unchanged (stability guard).
        assertEquals("তৈরি", engine.convertForComposing("toiri", "আমরা").bengali)
    }

    // ── S10: fragment sanity — no ri-kar flash from fuzzy layer ─────────

    @Test
    fun fragments_neverFlashRiKarFromFuzzyMatches() {
        // Primary/composed text must never be a ri-kar stretch of a plain-ri
        // fragment. The strip may still OFFER ৃ continuations where they are
        // legitimate (prokri → প্রকৃতি typed "prokriti" is a real habit), but
        // a রি continuation must stay within the top 3.
        for (frag in listOf("poriko", "prokri", "porisheb", "korich", "tribhu")) {
            val p = engine.convertWord(frag).bengali
            assertTrue('ৃ' !in p, "fragment '$frag' primary must not be a ri-kar stretch, got $p")
            val top3 = engine.getSuggestions(frag, 4).map { it.bengali }.take(3)
            assertTrue(
                top3.any { 'ৃ' !in it && it.isNotEmpty() },
                "fragment '$frag' top-3 must keep a রি reading, got $top3"
            )
        }
    }

    // ── S12: exact-spelling fidelity + চ্ছ lazy aliases (user reports) ──

    @Test
    fun ghuma_literalSpellingBeatsSeedShortcut() {
        assertEquals("ঘুমা", engine.convertWord("ghuma").bengali)
        assertEquals("ঘুমা", engine.convertForComposing("ghuma").bengali)
    }

    @Test
    fun ghumacco_lazyConjunctReachesConversationalVerb() {
        assertEquals("ঘুমাচ্ছ", engine.convertWord("ghumacco").bengali)
        assertEquals("ঘুমাচ্ছ", engine.convertForComposing("ghumacco").bengali)
        assertEquals("ঘুমাচ্ছ", engine.convertWord("ghumaccho").bengali)
        val strip = engine.getSuggestions("ghumacco", 4).map { it.bengali }
        assertTrue(strip.none { 'ছ' in it && "ছ্ছ" in it }, "no ছ্ছ garbage, got $strip")
    }

    // ── regressions ─────────────────────────────────────────────────────

    @Test
    fun regressions() {
        assertEquals("করি", primary("kori"))
        assertEquals("তৈরি", primary("toiri"))
        assertEquals("খণ্ড", primary("khond"))
        assertEquals("স্বাস্থ্য", primary("sastho"))
        assertEquals("জন্য", primary("jonno"))
    }
}
