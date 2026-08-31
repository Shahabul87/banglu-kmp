package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S150 (from the S149 Banglish-corpus study, docs/engine-banglish-study-
 * 2026-08-30.md): the chat-register round — the টা clitic, the a→এ
 * deictics, vowel-less shorthand, tech initialisms, corrected loan
 * renderings, and the attached emphatic ই. Real store on ./dictionary.sqlite.
 */
class S150ChatRegisterJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)
    private fun primary(k: String) = fold(engine.convertWord(k).bengali)
    private fun strip(k: String) = engine.getSuggestions(k, 6).map { fold(it.bengali) }

    @Test
    fun theTaCliticOwnsItsKeyWithTaAsTheTwin() {
        assertEquals("টা", primary("ta"))
        assertTrue("তা" in strip("ta"), "তা must stay one tap away: ${strip("ta")}")
    }

    @Test
    fun aToEDeicticsReadTheChatWay() {
        assertEquals("এটা", primary("ata"))
        assertTrue(fold("আটা") in strip("ata"), "আটা keeps its S109 strip slot: ${strip("ata")}")
        assertEquals("এইটা", primary("aita"))
        assertEquals("এই", primary("ai"))
    }

    @Test
    fun vowellessShorthandJoinsTheKmonClass() {
        assertEquals(fold("অনেক"), primary("onk"))
        assertEquals(fold("ভালো"), primary("vlo"))
        assertEquals(fold("একটু"), primary("aktu"))
        assertEquals(fold("কেন"), primary("kno"))
        assertEquals(fold("থ্যাংকস"), primary("tnx"))
    }

    @Test
    fun loanRenderingsMatchTheRegister() {
        assertEquals(fold("ইউজ"), primary("use"))
        assertEquals(fold("নাইস"), primary("nice"))
        assertEquals(fold("নিউ"), primary("new"))
        assertEquals(fold("হেল্প"), primary("help"))
    }

    @Test
    fun techInitialismsGetLetterNames() {
        // "id" deliberately stays Tier S: ঈদ/ইদ keeps the key (the corpus
        // skews tech-forum; the greeting register is real) — আইডি rides the
        // strip via the S52 acronym chip instead.
        assertTrue(primary("id") in setOf("ইদ", fold("ঈদ")), "id primary: ${primary("id")}")
        assertTrue(fold("আইডি") in strip("id"), "আইডি chip must ride the strip: ${strip("id")}")
        assertEquals(fold("এফবি"), primary("fb"))
        assertEquals(fold("এমবি"), primary("mb"))
        assertEquals(fold("জিবি"), primary("gb"))
        assertEquals(fold("পিসি"), primary("pc"))
        assertEquals(fold("টিভি"), primary("tv"))
    }

    @Test
    fun attachedEmphaticIComposesOnAttestedStems() {
        // khubi is genuinely OWNED: খুবি is a canonical tier-A store row
        // (freq 69, corpus-attested) — the layer defers by design and the
        // formal খুবই stays one tap away on the strip.
        assertEquals(fold("খুবি"), primary("khubi"))
        assertTrue(fold("খুবই") in strip("khubi"), "খুবই must ride the strip: ${strip("khubi")}")
        assertEquals(fold("একদমই"), primary("ekdomi"))
        // Real owners still win their keys against the composition
        // (the trailing i IS the word's own vowel on these keys).
        // NOTE: kothai -> কথাই predates this layer (root decomposition);
        // logged as a future ranking item, deliberately not pinned here.
        assertEquals(fold("শান্তি"), primary("shanti"))
        assertEquals(fold("ঘড়ি"), primary("ghori"))
    }

    @Test
    fun neighboursAreUntouched() {
        assertEquals(fold("কেমন"), primary("kmon"))
        assertEquals(fold("আমি"), primary("ami"))
        assertEquals(fold("তুমি"), primary("tumi"))
        assertEquals(fold("টাকা"), primary("taka"))
        assertEquals(fold("নিচে"), primary("niche"))
        assertEquals(fold("তার"), primary("tar"))
        assertEquals(fold("বাংলু"), primary("banglu"))
        assertEquals(fold("করি"), primary("kori"))
    }
}
