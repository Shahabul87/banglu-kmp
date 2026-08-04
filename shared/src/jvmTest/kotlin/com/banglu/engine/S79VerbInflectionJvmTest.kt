package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S79 (tester round 2026-08-04, "parle produces পার্লে/পারলেন — the র-family
 * verb forms don't work"): the reverse romanization emits the inherent o
 * before the -l (past/conditional) and -b (future) verb suffixes (পারলে ->
 * "parole", পারবে -> "parobe"), so the verb forms never sat on the keys
 * people type — junk conjunct/rare owners took them exactly (পার্লে@35 t1 on
 * "parle", পার্বণে@65 on "parbone", জাবনা@1 on "jabona"). Db 3.8.9 adds the
 * verb_o_drop_l / verb_o_drop_b habit rules; the engine generalizes the S8
 * evidence margin to same-tier bands and makes the negation guard follow the
 * S78 ownership law (canonical priority only) with an evidence-competitive
 * deferral for the নে softener classes.
 * Db-gated so pre-3.8.9 checkouts skip.
 */
class S79VerbInflectionJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    private fun assertSame(expected: String, actual: String, msg: String? = null) =
        assertEquals(fold(expected), fold(actual), msg)

    private fun hasVerbKeys(): Boolean =
        engine.getSuggestions("parle", 5).any { fold(it.bengali) == fold("পারলে") }

    @Test
    fun pastConditionalFormsOwnTheirTypedKeys() {
        if (!hasVerbKeys()) return // pre-3.8.9 db
        assertSame("পারলে", engine.convertWord("parle").bengali)
        assertSame("পারলেন", engine.convertWord("parlen").bengali)
        assertSame("করলে", engine.convertWord("korle").bengali)
        assertSame("ধরলে", engine.convertWord("dhorle").bengali)
    }

    @Test
    fun futureFormsOwnTheirTypedKeys() {
        if (!hasVerbKeys()) return
        assertSame("পারবো", engine.convertWord("parbo").bengali)
        assertSame("পারবে", engine.convertWord("parbe").bengali)
        assertSame("পারবেন", engine.convertWord("parben").bengali)
    }

    @Test
    fun negationAndSoftenerFamily() {
        if (!hasVerbKeys()) return
        assertSame("পারবোনা", engine.convertWord("parbona").bengali)
        assertSame("পারবোনে", engine.convertWord("parbone").bengali)
        assertSame("পারবোনানে", engine.convertWord("parbonane").bengali)
        // S79 regression pins: the new o-drop aliases put the glued formal
        // spellings (করবনা, বলবনা) on these keys at habit priority — they
        // must NOT dethrone the chat compounds (ownership law).
        assertSame("করবোনা", engine.convertWord("korbona").bengali)
        assertSame("বলবোনা", engine.convertWord("bolbona").bengali)
        assertSame("বলবোনে", engine.convertWord("bolbone").bengali)
        assertSame("করবোনে", engine.convertWord("korbone").bengali)
    }

    @Test
    fun sameTierEvidenceMarginUnseatsRareSquatters() {
        if (!hasVerbKeys()) return
        // জাবনা@1 (cattle feed) canonically owns "jabona"; the chat form
        // যাবোনা@50 sits one priority down in the same tier band — the
        // generalized margin promotes it.
        assertSame("যাবোনা", engine.convertWord("jabona").bengali)
    }

    @Test
    fun displacedRealWordsStayReachable() {
        if (!hasVerbKeys()) return
        val parle = engine.getSuggestions("parle", 6).map { fold(it.bengali) }
        val parbone = engine.getSuggestions("parbone", 4).map { fold(it.bengali) }
        val jabona = engine.getSuggestions("jabona", 4).map { fold(it.bengali) }
        val marle = engine.getSuggestions("marle", 4).map { fold(it.bengali) }
        assertTrue(fold("পারলেন") in parle, "পারলেন must stay suggested: $parle")
        assertTrue(fold("পার্বণে") in parbone, "পার্বণে must stay reachable: $parbone")
        assertTrue(fold("জাবনা") in jabona, "জাবনা must stay reachable: $jabona")
        // মার্লে@67 vs মারলে@65 is inside the evidence margin — the canonical
        // owner keeps the key (corpus-register artifact, documented) but the
        // verb must be one tap away.
        assertTrue(fold("মারলে") in marle, "মারলে must be one tap away: $marle")
    }

    @Test
    fun invariantGuards() {
        // Untouched neighbors of the changed machinery.
        assertSame("কাচ্চি", engine.convertWord("kacci").bengali)
        assertSame("পারছি", engine.convertWord("parchi").bengali)
        assertSame("পারতেছি", engine.convertWord("partesi").bengali)
        assertSame("খেলে", engine.convertWord("khele").bengali)
    }
}
