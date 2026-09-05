package com.banglu.engine

import com.banglu.engine.dictionary.CulturalPhrases
import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S184 (2026-09-04) — two user asks on the real dictionary:
 *  - chandrabindu by an explicit "^" marker (Android: long-press c) renders
 *    exactly as the preview shows it, on every path;
 *  - cultural phrases commit with their spacing and surface as an intent chip
 *    while the prefix is being typed.
 */
class S184ChandrabinduPhrasesJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun caretMarkerRendersTheChandrabinduWhereTheUserPutIt() {
        for ((key, want) in listOf("cha^d" to "চাঁদ", "ka^de" to "কাঁদে", "ha^s" to "হাঁস", "ba^dh" to "বাঁধ", "cha^" to "চাঁ", "cha^dni" to "চাঁদনি", "pa^ch" to "পাঁচ")) {
            assertEquals(want, engine.convertWord(key).bengali, key)
            assertEquals(want, engine.convertForComposing(key).bengali, "composing $key")
            assertEquals(want, engine.convertForInstantPreview(key), "instant $key")
            assertEquals(want, engine.getSuggestions(key, 6).first().bengali, "strip[0] $key")
        }
    }

    @Test
    fun everyPhraseVariantCommitsThePhrase() {
        for ((key, phrase) in CulturalPhrases.PAIRS) {
            assertEquals(fold(phrase), fold(engine.convertWord(key).bengali), key)
            assertEquals(fold(phrase), fold(engine.convertForComposing(key).bengali), "composing $key")
            assertEquals(fold(phrase), fold(engine.getSuggestions(key, 6).first().bengali), "strip[0] $key")
        }
    }

    @Test
    fun typedPrefixSurfacesThePhraseAsTheIntentChip() {
        for ((prefix, phrase) in listOf("assa" to "আসসালামু আলাইকুম", "wala" to "ওয়ালাইকুম আসসালাম", "insh" to "ইনশাআল্লাহ", "alham" to "আলহামদুলিল্লাহ", "jazak" to "জাযাকাল্লাহ খাইরান", "eidmu" to "ঈদ মুবারক", "dhonno" to "ধন্যবাদ")) {
            val strip = engine.getSuggestions(prefix, 6).map { fold(it.bengali) }
            assertEquals(fold(phrase), strip.getOrNull(1), "strip for $prefix = $strip")
        }
        // three letters are too little to guess an intent
        assertTrue(engine.getSuggestions("ass", 6).none { it.source == "phrase_completion" })
    }
}
