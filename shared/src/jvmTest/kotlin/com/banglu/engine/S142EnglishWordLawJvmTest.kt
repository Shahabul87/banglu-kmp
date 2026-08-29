package com.banglu.engine

import com.banglu.engine.types.ResolutionSource
import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S142 (user, 2026-08-29: "if the user types an exact English word, return
 * its Bangla pronunciation, and the English word in the suggestions" —
 * "one engine behaviour, not some words yes and others no"). Real store.
 */
class S142EnglishWordLawJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)
    private fun strip(key: String, n: Int = 6) = engine.getSuggestions(key, n).map { fold(it.bengali) }

    @Test
    fun anEnglishWordReadAsARareBengaliInflectionCommitsItsPronunciation() {
        // The field case: tester used to commit টেস্টের (of the Test) with no
        // টেস্টার and no "tester" anywhere on the strip.
        val r = engine.convertWord("tester")
        assertEquals(fold("টেস্টার"), fold(r.bengali))
        val s = strip("tester")
        assertTrue(fold("টেস্টের") in s, "the Bengali reading stays one tap away: $s")
        assertTrue("tester" in s, "the English word rides the strip: $s")
        // Same law, same shape: phone -> ফোন (ফোনে@78 stays a chip), call -> কল.
        assertEquals(fold("ফোন"), fold(engine.convertWord("phone").bengali))
        assertTrue(fold("ফোনে") in strip("phone"), strip("phone").toString())
        assertEquals(fold("কল"), fold(engine.convertWord("call").bengali))
        // The vetted S24 list keeps working through the same door.
        assertEquals(fold("টাইম"), fold(engine.convertWord("time").bengali))
        assertEquals(fold("প্রিন্টার"), fold(engine.convertWord("printer").bengali))
    }

    @Test
    fun everydayBengaliWordsKeepTheirKeyWithThePronunciationAsAChip() {
        // Invariant 6: name -> নামে@89 stays Bengali; নেম is a chip.
        assertEquals(fold("নামে"), fold(engine.convertWord("name").bengali))
        assertTrue(fold("নেম") in strip("name"), strip("name").toString())
        assertEquals(fold("দিনে"), fold(engine.convertWord("dine").bengali))
    }

    @Test
    fun curatedLoanwordSeedsOutrankCrudeLexiconRenderings() {
        // engine used to flip to the CMU rendering এনজেন over the seed ইঞ্জিন.
        assertEquals(fold("ইঞ্জিন"), fold(engine.convertWord("engine").bengali))
        assertEquals(fold("সাজেশন"), fold(engine.convertWord("suggestion").bengali))
        // S131 honesty flip still owns its class.
        assertEquals(fold("রিয়েল"), fold(engine.convertWord("real").bengali))
    }

    @Test
    fun theComposingPreviewAgreesWithTheCommit() {
        for (key in listOf("tester", "phone", "engine", "suggestion", "name", "call", "time")) {
            assertEquals(
                fold(engine.convertWord(key).bengali),
                fold(engine.convertForComposing(key).bengali),
                "WYSIWYG for $key"
            )
        }
    }

    @Test
    fun dictionaryLoanwordsAreUntouched() {
        for ((key, expected) in listOf("computer" to "কম্পিউটার", "mobile" to "মোবাইল", "doctor" to "ডাক্তার", "market" to "মার্কেট", "love" to "লাভ", "hello" to "হ্যালো")) {
            val r = engine.convertWord(key)
            assertEquals(fold(expected), fold(r.bengali), key)
            assertTrue(r.source != ResolutionSource.CLEAN_TRANSLITERATION)
        }
    }
}
