package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S56 tester-report round (2026-07-20), on the real dictionary.sqlite.
 *
 * 1. likh: the composing preview promoted a stale extended-dictionary twin
 *    (লিখয়@68 > লিখ@60) that the commit path never produces (store-canonical
 *    লিখ wins there) — the tester saw "likhy words" forming while typing.
 *    Law: preview == commit (invariant #2).
 * 2. ashiko: the trailing emphatic/vocative "o" was silently dropped by the
 *    fuzzy layer (ashiko -> আশিক). The stem+o compound must produce the
 *    attested emphatic form with the other spelling as an alternative
 *    (tester: "at least in suggestion we have to have two words").
 * 3. screenshot / bisso / bissokap: db-gated pins for the 3.8.6 data round
 *    (english_lexicon screenshot row; shw->ssh habit aliases for শ্ব words).
 */
class S56TesterRoundJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun lookupEnglish(key: String): String? {
        val store = engine.javaClass.getDeclaredField("phoneticIndex")
            .also { it.isAccessible = true }.get(engine) ?: return null
        return store.javaClass.methods.first { it.name == "lookupEnglish" }
            .invoke(store, key) as String?
    }

    private fun storeHasKey(key: String): Boolean {
        val m = engine.javaClass.declaredMethods.first { it.name == "storeLookup" }
        m.isAccessible = true
        return (m.invoke(engine, key) as List<*>).isNotEmpty()
    }

    // --- 1. likh preview==commit parity -----------------------------------

    @Test
    fun likhPreviewMatchesCommit() {
        val commit = engine.convertWord("likh").bengali
        assertEquals("লিখ", commit, "commit for likh regressed")
        assertEquals(commit, engine.getCompositionPreview("likh"), "WYSIWYG: preview != commit for likh")
    }

    @Test
    fun latticePromotionStillWorksWhenStoreAgrees() {
        // Guard: the parity clamp must not kill legitimate promotions -
        // likhi/likhbo/likhe stay exactly as the commit path produces them.
        for (key in listOf("likhi", "likhbo", "likhe")) {
            assertEquals(
                engine.convertWord(key).bengali,
                engine.getCompositionPreview(key),
                "preview != commit for $key"
            )
        }
    }

    // --- 2. ashiko emphatic-o compound ------------------------------------

    @Test
    fun ashikoProducesEmphaticFormWithBothSpellings() {
        val r = engine.convertWord("ashiko")
        assertEquals("আশিকও", r.bengali, "ashiko must keep the typed o (emphatic form)")
        val all = listOf(r.bengali) + r.alternatives.map { it.bengali }
        assertTrue("আশিকো" in all, "vocative spelling আশিকো must be offered: $all")
    }

    @Test
    fun ashikoPreviewMatchesCommit() {
        assertEquals(engine.convertWord("ashiko").bengali, engine.getCompositionPreview("ashiko"))
    }

    @Test
    fun ashikoSuggestionsContainBothSpellings() {
        val sugg = engine.getSuggestions("ashiko", 6).map { it.bengali }
        assertTrue("আশিকও" in sugg, "আশিকও missing from suggestions: $sugg")
        assertTrue("আশিকো" in sugg, "আশিকো missing from suggestions: $sugg")
    }

    @Test
    fun emphaticODoesNotStealAttestedOWords() {
        // Store/dictionary-attested words ending in o must be untouched.
        assertEquals("আমিও", engine.convertWord("amio").bengali)
        assertEquals("ভালো", engine.convertWord("valo").bengali)
        assertEquals("হলো", engine.convertWord("holo").bengali)
        assertEquals("করবো", engine.convertWord("korbo").bengali)
        assertEquals("জাবো", engine.convertWord("jabo").bengali) // pre-existing j→জ default
    }

    @Test
    fun negationCompoundUnaffected() {
        assertEquals("বলবোনে", engine.convertWord("bolbone").bengali)
        assertEquals("করছিনা", engine.convertWord("korchina").bengali)
    }

    // --- 3. data-round pins (db 3.8.6; gated so pre-rebuild runs skip) ----

    @Test
    fun screenshotResolvesToLoanword() {
        val lexicon = lookupEnglish("screenshot") ?: return // pre-3.8.6 db
        assertEquals("স্ক্রিনশট", lexicon)
        assertEquals("স্ক্রিনশট", engine.convertWord("screenshot").bengali)
        assertEquals("স্ক্রিনশট", engine.getCompositionPreview("screenshot"))
    }

    @Test
    fun bissoClassResolvesToShwoWords() {
        if (!storeHasKey("bissokap")) return // pre-3.8.6 db
        assertEquals("বিশ্বকাপ", engine.convertWord("bissokap").bengali)
        assertEquals("বিশ্ব", engine.convertWord("bisso").bengali)
        assertEquals("বিশ্বাস", engine.convertWord("bissas").bengali)
    }
}
