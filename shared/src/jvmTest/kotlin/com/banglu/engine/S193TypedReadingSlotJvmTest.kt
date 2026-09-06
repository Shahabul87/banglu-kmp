package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S193 (user screenshot 2026-09-06: typed boddhota, got অবদ্ধতা, no বদ্ধতা on the strip). */
class S193TypedReadingSlotJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)
    private fun strip(k: String) = engine.getSuggestions(k, 8)

    @Test
    fun anAliasRowNeverAddsALetterTheUserDidNotType() {
        // USER LAW (2026-09-06): the engine must not insert a letter that was not typed.
        assertEquals(fold("বদ্ধতা"), fold(engine.convertWord("boddhota").bengali), "the typed reading commits")
        assertEquals(fold("বদ্ধতা"), fold(engine.convertForComposing("boddhota").bengali), "and the preview agrees")
        val s = strip("boddhota").map { fold(it.bengali) }
        assertTrue(fold("অবদ্ধতা") in s, "the dictionary's longer word stays one tap away: $s")
        // a canonical owner is never touched even when its reading is longer
        assertEquals(fold("স্থিরতা"), fold(engine.convertWord("sthirota").bengali))
    }

    @Test
    fun onlyAnEvidencedTypedReadingBeatsTheLeadingVowelAlias() {
        // A validated word the user typed wins over the অ-alias (the alias stays a chip).
        assertEquals(fold("ফিসের"), fold(engine.convertWord("phiser").bengali))
        assertTrue(fold("অফিসের") in strip("phiser").map { fold(it.bengali) })
        assertEquals(fold("বৈতনিক"), fold(engine.convertWord("boitonik").bengali))
        // A clean but UNEVIDENCED reading is a slip: S181's single-slip law keeps the attested word.
        assertEquals(fold("অবস্থা"), fold(engine.convertWord("bostha").bengali))
        assertEquals(fold("অফিসে"), fold(engine.convertWord("phise").bengali))
    }

    @Test
    fun conjunctFoldingChatAliasesAreNotLetterAdditions() {
        // DECISION (2026-09-06): a gate on "the alias reads longer than the key" flipped 386
        // keys of the 132K dump — the chat register itself. Only the leading-vowel shape is
        // an untyped letter; internal conjunct folds stay habit aliases.
        for ((k, w) in listOf(
            "shikha" to "শিক্ষা", "dhonobad" to "ধন্যবাদ", "modhe" to "মধ্যে", "porikha" to "পরীক্ষা",
            "bujote" to "বুঝতে", "songkha" to "সংখ্যা", "oboshoi" to "অবশ্যই", "sopne" to "স্বপ্নে",
        )) {
            assertEquals(fold(w), fold(engine.convertWord(k).bengali), k)
            assertEquals(fold(w), fold(engine.convertForComposing(k).bengali), "$k preview")
        }
    }

    @Test
    fun combinationChipsAreBengaliOnly() {
        for (k in listOf("boddhotar", "arpara", "chorpara", "nayanpur")) {
            val bad = strip(k).filter { c -> c.source == "oov_combo" && !c.bengali.all { it in 'ঀ'..'৿' } }
            assertTrue(bad.isEmpty(), "$k: Latin in combination chips: ${bad.map { it.bengali }}")
        }
    }

    @Test
    fun exactOwnersAreUntouched() {
        for ((k, w) in listOf("kotha" to "কথা", "kmon" to "কেমন", "kacci" to "কাচ্চি", "name" to "নামে", "sthirota" to "স্থিরতা")) {
            assertEquals(fold(w), fold(engine.convertWord(k).bengali))
        }
        assertTrue(strip("kmon").none { it.source == "typed_literal" }, "a shorthand owner gets no literal chip: ${strip("kmon").map { it.bengali }}")
    }
}
