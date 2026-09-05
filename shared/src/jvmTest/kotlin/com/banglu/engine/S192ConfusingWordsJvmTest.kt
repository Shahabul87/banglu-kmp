package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S192 — the device pass over 80 confusing words (2026-09-05): three fixes, each pinned with its S22 example. */
class S192ConfusingWordsJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)
    private fun strip(k: String) = engine.getSuggestions(k, 8)

    @Test
    fun aFarOffRealWordNeverReplacesACleanNameReading() {
        val c = engine.convertWord("chorpara")
        assertTrue(fold(c.bengali) != fold("চর্চার"), "chorpara must not become চর্চার: ${c.bengali}")
        assertTrue(strip("chorpara").any { fold(it.bengali) == fold("চরপাড়া") }, "চরপাড়া must be offered: ${strip("chorpara").map { it.bengali }}")
        // transposition slips still corrected (S181 evidence)
        assertEquals("আমাদের", engine.convertWord("amdaer").bengali)
        assertEquals("বাংলাদেশ", engine.convertWord("bangaldesh").bengali)
    }

    @Test
    fun hathReadsAsTheHand() {
        assertEquals("হাত", engine.convertWord("hath").bengali)
        assertEquals("হাত", engine.convertForComposing("hath").bengali)
    }

    @Test
    fun previewAndCommitBothRefuseTheFarOffWord() {
        // The commit wrapper's typo layer may still improve the floor by ONE
        // letter (চরপারা → চরপাড়া, a real place) — the S176-documented preview
        // exception; what neither path may do is hand back the far-off word.
        for (k in listOf("chorpara", "bhorbari", "nayanpur")) {
            val commit = fold(engine.convertWord(k).bengali); val preview = fold(engine.convertForComposing(k).bengali)
            assertTrue(commit != fold("চর্চার") && preview != fold("চর্চার"), "$k: commit=$commit preview=$preview")
        }
        assertTrue(fold(engine.convertWord("chorpara").bengali) in setOf(fold("চরপারা"), fold("চরপাড়া")))
    }

    @Test
    fun englishStyleNameSpellingsAreOffered() {
        assertTrue(strip("nayanpur").any { fold(it.bengali) == fold("নয়নপুর") }, "nayanpur → নয়নপুর chip: ${strip("nayanpur").map { it.bengali }}")
        assertTrue(strip("arpara").any { fold(it.bengali) == fold("অরপারা") } || strip("arpara").any { fold(it.bengali) == fold("আরপারা") })
    }

    @Test
    fun combinationsOutrankUnrelatedPrefixCompletions() {
        val s = strip("bhorbari")
        val firstCombo = s.indexOfFirst { it.source == "oov_combo" }
        val firstPrefix = s.indexOfFirst { it.source == "dictionary_prefix" || it.source == "corpus_prefix" || it.source == "roman_prefix" }
        assertTrue(firstCombo in 1..3, "bhorbari combinations must be visible: ${s.map { it.bengali + ":" + it.source }}")
        if (firstPrefix >= 0) assertTrue(firstCombo < firstPrefix, "combinations before unrelated completions: ${s.map { it.bengali + ":" + it.source }}")
    }
}
