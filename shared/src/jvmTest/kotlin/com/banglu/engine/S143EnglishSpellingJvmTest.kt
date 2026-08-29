package com.banglu.engine

import com.banglu.engine.types.ResolutionSource
import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * S143 (user, 2026-08-29: "type suggention and see what the engine returns"
 * — সুজ্ঞেন্তিওন): misspelled English words resolve through the lexicon like
 * misspelled Bangla words resolve through the store. Real store.
 */
class S143EnglishSpellingJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)
    private fun strip(key: String, n: Int = 6) = engine.getSuggestions(key, n).map { fold(it.bengali) }

    @Test
    fun aMisspelledEnglishWordCommitsTheNearestWordsPronunciation() {
        assertEquals(fold("সাজেশন"), fold(engine.convertWord("suggention").bengali))
        assertTrue("suggestion" in strip("suggention"), strip("suggention").toString())
        for ((key, expected) in listOf(
            "suggetion" to "সাজেশন", "sugestion" to "সাজেশন",
            "compter" to "কম্পিউটার", "computr" to "কম্পিউটার",
            "keybord" to "কীবোর্ড", "dictonary" to "ডিকশনারি",
            "tommorow" to "টমারো", "recieve" to "রিসিভ", "adress" to "অ্যাড্রেস", "docter" to "ডাক্তার", "seperate" to "সেপারেট", "engin" to "ইঞ্জিন"
        )) {
            val r = engine.convertWord(key)
            assertEquals(ResolutionSource.ENGLISH_LEXICON, r.source, "$key -> ${r.bengali}")
            assertEquals(fold(expected), fold(r.bengali), key)
        }
    }

    @Test
    fun theComposingPreviewAgreesWithTheCommit() {
        for (key in listOf("suggention", "compter", "keybord", "recieve", "tommorow")) {
            assertEquals(fold(engine.convertWord(key).bengali), fold(engine.convertForComposing(key).bengali), key)
        }
    }

    @Test
    fun banglaNamesAndCleanReadingsAreNeverPulledIntoEnglish() {
        for (key in listOf("rafsan", "shahabul", "banglu", "tanvir", "kolomu", "sumon", "rahim", "tomra", "amaer", "bhalo")) {
            assertNotEquals(ResolutionSource.ENGLISH_LEXICON, engine.convertWord(key).source, key)
        }
        assertEquals(fold("বাংলু"), fold(engine.convertWord("banglu").bengali))
        // A Bengali typo repair that reads the key as closely keeps it (amaer, sumon).
        assertEquals(fold("সুমন"), fold(engine.convertWord("sumon").bengali))
    }
}
