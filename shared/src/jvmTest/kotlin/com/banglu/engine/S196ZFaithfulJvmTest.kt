package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S196 (user's hand note, 2026-09-06): z maps to য, j maps to জ — the typed letter wins for names. */
class S196ZFaithfulJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)
    private fun commit(k: String) = fold(engine.convertWord(k).bengali)
    private fun preview(k: String) = fold(engine.convertForComposing(k).bengali)
    private fun strip(k: String) = engine.getSuggestions(k, 8).map { fold(it.bengali) }

    @Test
    fun theUsersFourSpellingsOfAziz() {
        assertEquals(fold("আজিজ"), commit("ajij"))
        assertEquals(fold("আযিয"), commit("aziz"))
        assertTrue(fold("আজিজ") in strip("aziz"), "the dictionary's word stays a tap away: ${strip("aziz")}")
        assertEquals(fold("আজিয"), commit("ajiz"))
        assertEquals(fold("আযিজ"), commit("azij"))
        for (k in listOf("aziz", "ajiz", "azij")) assertEquals(commit(k), preview(k), "$k preview = commit")
    }

    @Test
    fun namesTypedWithZKeepTheZ() {
        for ((k, w) in listOf("zakir" to "যাকির", "zahid" to "যাহিদ", "zaman" to "যামান", "hamza" to "হামযা")) {
            assertEquals(fold(w), commit(k), k)
        }
    }

    @Test
    fun everydayWordsAndTheJHabitAreUntouched() {
        for ((k, w) in listOf("kaz" to "কাজ", "zonno" to "জন্য", "zibon" to "জীবন", "zodi" to "যদি", "jodi" to "যদি", "jabo" to "যাবো", "jokhon" to "যখন", "jamai" to "জামাই", "jara" to "যারা", "zara" to "যারা", "nize" to "নিজে", "zati" to "জাতি", "zene" to "জেনে", "citizen" to "সিটিজেন")) {
            assertEquals(fold(w), commit(k), k)
        }
    }
}
