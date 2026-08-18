package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S109 pins (full store): word-initial অ chat onsets. Casual typists write
 * the অ sound as "a" (anek) or "aw" (awnek/awto); before db 3.9.3 the index
 * had ZERO aw keys (awto -> আওতা, awshadharon -> raw garbage) and junk
 * আ-spellings owned several a-keys (আনেক@64 beat অনেক@93). Corpus study:
 * aw-onset top6 22.9% -> 97.7%, a-onset top6 75.7% -> 99.7%, canonical
 * vowel-initial unchanged at 99.6/100 (build/reports/s109-study).
 */
class S109VowelOnsetJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun awOnsetReachesTheOWord() {
        assertEquals("অটো", engine.convertWord("awto").bengali)
        assertEquals("অনেক", engine.convertWord("awnek").bengali)
        assertEquals("অসাধারণ", engine.convertWord("awshadharon").bengali)
        assertEquals("অসম্ভব", engine.convertWord("awsombhob").bengali)
    }

    @Test
    fun aOnsetJunkTwinsAreHealed() {
        // initial-o-promote: the অ word outranks the junk আ spelling when
        // strictly more frequent (আনেক@64 vs অনেক@93 class).
        assertEquals("অনেক", engine.convertWord("anek").bengali)
        assertEquals("অপরাধ", engine.convertWord("aporadh").bengali)
        assertEquals("অসাধারণ", engine.convertWord("ashadharon").bengali)
    }

    @Test
    fun legitAWordsKeepTheirKeys() {
        // The promote pass is frequency-gated suffix-equality only — real
        // আ words must never lose their canonical keys to it.
        assertEquals("আবার", engine.convertWord("abar").bengali)
        assertEquals("আমার", engine.convertWord("amar").bengali)
        assertEquals("আদর", engine.convertWord("aador").bengali)
        // আটা (flour) stays reachable on its own key.
        assertTrue(engine.getSuggestions("ata", 6).any { it.bengali == "আটা" })
    }

    @Test
    fun bareAOffersBothFirstVowels() {
        // Image-2 class ("a could be অ or আ"): আ leads (WYSIWYG with the
        // instant preview), longer input then disambiguates via the store.
        assertEquals("আ", engine.convertWord("a").bengali)
        assertEquals("আ", engine.convertForInstantPreview("a"))
    }
}
