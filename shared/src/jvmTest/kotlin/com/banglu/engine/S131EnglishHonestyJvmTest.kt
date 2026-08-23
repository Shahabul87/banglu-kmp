package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S131: English-word honesty (Windows field report, 2026-08-23 — "i type real
 * the engine return horrific result").
 *
 * Root cause, traced to the byte: banglu-web's dictionary-extended.json
 * carries poisoned rows — {"bengali": "রোল", "phonetics": ["real"]}@68 — and
 * the compiler bakes them into extended_phonetics, so the dictionary layer
 * "exactly" matches real -> রোল at 0.97 on EVERY surface (Android and the web
 * editor included; the user just met it on Windows first).
 *
 * The law this round adds: a REAL English word (english_lexicon key) may only
 * be claimed by a Bengali result that can actually be READ BACK as that key.
 * Genuine collisions all can: reverse(নামে)="name", reverse(টিমে)="time",
 * reverse(প্রিন্টের)="printer" — overlap 1.0. The poisoned class cannot:
 * reverse(রোল)="rol" vs "real" scores 0.554. Below the 0.75 ownership floor,
 * and only when the lexicon's own rendering reads better than the squatter,
 * the lexicon rendering wins and the squatter drops to an alternative.
 */
class S131EnglishHonestyJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun fold(s: String) = com.banglu.engine.util.ReverseTransliterator.foldNukta(s)

    @Test
    fun aPoisonedAliasCannotSwallowARealEnglishWord() {
        // The field case itself. english_lexicon: real -> রিয়েল.
        assertEquals(fold("রিয়েল"), fold(engine.convertWord("real").bengali))
    }

    @Test
    fun theSquatterSurvivesAsAnAlternative() {
        // The user who genuinely wanted রোল still gets it as a chip.
        val result = engine.convertWord("real")
        assertTrue(
            result.alternatives.any { fold(it.bengali) == fold("রোল") },
            "রোল must remain reachable, got ${result.alternatives}"
        )
        assertEquals(fold("রিয়েল"), fold(result.bengali))
    }

    @Test
    fun theComposingPreviewAgreesWithTheCommit() {
        // WYSIWYG (invariant 2): the live echo must not show রোল while Space
        // commits রিয়েল.
        assertEquals(
            fold(engine.convertWord("real").bengali),
            fold(engine.convertForComposing("real").bengali),
        )
    }

    @Test
    fun theStripLeadsWithTheHonestPrimary() {
        val strip = engine.getSuggestions("real", 5).map { fold(it.bengali) }
        assertEquals(fold("রিয়েল"), strip.first(), "strip[0] is the commit contract (S19), got $strip")
        assertTrue(fold("রোল") in strip, "the old primary stays pickable, got $strip")
    }

    @Test
    fun genuineEnglishBengaliCollisionsAreUntouched() {
        // নামে really does read as the English spelling "name" — the
        // ownership floor (overlap 1.0) protects it, exactly the S24 danger
        // case the 4x rule was built around. printer already resolves to the
        // lexicon loanword today; the guard must not disturb it.
        assertEquals(fold("নামে"), fold(engine.convertWord("name").bengali), "name -> নামে stays Bengali (S24)")
        assertEquals(fold("প্রিন্টার"), fold(engine.convertWord("printer").bengali), "loanword stays")
    }

    @Test
    fun curatedLoanwordsAreUntouched() {
        // college: the seed কলেজ@0.998 beats the lexicon's cruder কলিজ — both
        // the confidence exemption and the reads-better condition protect it.
        assertEquals(fold("কলেজ"), fold(engine.convertWord("college").bengali))
        // roll: the lexicon agrees with the pipeline (রোল IS roll) — no flip.
        assertEquals(fold("রোল"), fold(engine.convertWord("roll").bengali))
        // The vetted intent list keeps its S24/S26 behavior.
        assertEquals(fold("টাইম"), fold(engine.convertWord("time").bengali))
    }

    @Test
    fun establishedPinsSurvive() {
        assertEquals(fold("কাচ্চি"), fold(engine.convertWord("kacci").bengali), "invariant 6")
        assertEquals(fold("কেমন"), fold(engine.convertWord("kmon").bengali))
        assertEquals(fold("রোল"), fold(engine.convertWord("rol").bengali), "the canonical owner keeps its own key")
    }
}
