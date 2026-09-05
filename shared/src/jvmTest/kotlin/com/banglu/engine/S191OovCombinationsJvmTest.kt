package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S191 (user, 2026-09-05): a key the dictionary does not know must not end as
 * ONE guessed chip. The lattice's letter combinations ride the strip, the
 * user's picks rank them, and the commit never moves.
 */
class S191OovCombinationsJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)
    private fun strip(k: String) = engine.getSuggestions(k, 6)

    @Test
    fun unknownWordCarriesItsCombinations() {
        val before = fold(engine.convertWord("arpara").bengali)
        val s = strip("arpara")
        val combos = s.filter { it.source == "oov_combo" }
        assertTrue(combos.size >= 3, "arpara strip must carry combinations: ${s.map { it.bengali + ":" + it.source }}")
        assertTrue(s.any { fold(it.bengali) == fold("আরপারা") }, "the plain (no reph) reading must be offered: ${s.map { it.bengali }}")
        assertEquals(before, fold(engine.convertWord("arpara").bengali), "commit must not move")
        assertEquals(before, fold(s[0].bengali), "strip[0] is still the commit")
    }

    @Test
    fun validatedWordsAlwaysPrecedeCombinations() {
        // USER LAW: dictionary-validated words first, unvalidated combinations after them.
        for (k in listOf("banglu", "parbone", "arpara", "sorpara")) {
            val s = strip(k)
            val lastCombo = s.indexOfLast { it.source == "oov_combo" }
            val firstRealAfter = s.withIndex().drop(1).firstOrNull { (_, c) -> c.source != "oov_combo" && c.source != "typed_literal" && c.source != "english_passthrough" && engine.isKnownWordForStrip(c.bengali) }?.index
            if (lastCombo >= 0 && firstRealAfter != null) assertTrue(firstRealAfter < s.indexOfFirst { it.source == "oov_combo" }, "$k: a validated word sits below a combination: ${s.map { it.bengali + ":" + it.source }}")
        }
    }

    @Test
    fun knownWordsGetNoCombinationChips() {
        for (k in listOf("kotha", "amar", "bangladesh", "kmon", "kacci", "name")) {
            assertTrue(strip(k).none { it.source == "oov_combo" }, "$k must not carry combination chips")
        }
        assertEquals("কাচ্চি", engine.convertWord("kacci").bengali); assertEquals("কেমন", engine.convertWord("kmon").bengali)
    }

    @Test
    fun picksTeachTheLetterHabitAndReorderTheChips() {
        engine.clearAmbiguityHabit()
        val nukta = strip("arpara").firstOrNull { it.source == "oov_combo" && 'ড়' in it.bengali }?.bengali
            ?: strip("arpara").first { it.source == "oov_combo" }.bengali
        val beforeIdx = strip("arpara").indexOfFirst { it.bengali == nukta }
        repeat(3) { engine.recordAmbiguityHabit("arpara", nukta) }
        assertTrue(engine.ambiguityHabitCount("a", "আ") + engine.ambiguityHabitCount("a", "া") + engine.ambiguityHabitCount("r", "ড়") + engine.ambiguityHabitCount("r", "র") > 0, "habit must record lattice choices")
        val afterIdx = strip("arpara").indexOfFirst { it.bengali == nukta }
        assertTrue(afterIdx in 1..beforeIdx, "picked combination must move up: before=$beforeIdx after=$afterIdx strip=${strip("arpara").map { it.bengali }}")
        assertEquals(fold("অর্পারা"), fold(engine.convertWord("arpara").bengali), "the habit orders chips, never the commit")
        engine.clearAmbiguityHabit()
    }
}
