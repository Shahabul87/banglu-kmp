package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S189 — strip twins from the S188 real-world study. Every case pins BOTH
 * halves of the contract: the twin is on the strip, and the commit is exactly
 * what it was before this round (the user typed it; the engine's answer
 * stays; the alternative rides along).
 */
class S189StripTwinsJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)
    private fun strip(k: String) = engine.getSuggestions(k, 6).map { fold(it.bengali) }
    private fun commit(k: String) = fold(engine.convertWord(k).bengali)

    private fun pin(key: String, commitStays: String, twin: String, besidePrimary: Boolean = false) {
        assertEquals(fold(commitStays), commit(key), "commit for $key must not move")
        val s = strip(key)
        assertTrue(fold(twin) in s, "$key strip must carry $twin: $s")
        if (besidePrimary) assertEquals(fold(twin), s[1], "$key: $twin beside the primary: $s")
    }

    @Test
    fun chandrabinduTheTypistOmitsRidesTheStrip() {
        pin("tara", "তারা", "তাঁরা")
        pin("ba", "বা", "বাঁ")
        pin("jader", "যাদের", "যাঁদের")
    }

    @Test
    fun sibilantFoldTwinRidesTheStrip() {
        pin("pulis", "পুলিস", "পুলিশ", besidePrimary = true)
        pin("des", "দেস", "দেশ", besidePrimary = true)
        pin("asa", "আসা", "আশা")
        pin("sekh", "সেখ", "শেখ", besidePrimary = true)
        pin("notis", "নোটিস", "নোটিশ", besidePrimary = true)
        pin("chas", "চাস", "চাষ", besidePrimary = true)
    }

    @Test
    fun longVowelAndVowelInitialTwins() {
        pin("bhuutta", "ভূতটা", "ভুট্টা", besidePrimary = true)
        pin("ai", "এই", "আই")
        pin("ata", "এটা", "আটা")
    }

    @Test
    fun joinedFormWhenTheSplitterWon() {
        pin("joyoshongkor", "জয় শঙ্কর", "জয়শঙ্কর", besidePrimary = true)
        pin("kathomandubhittik", "কাঠমান্ডু ভিত্তিক", "কাঠমান্ডুভিত্তিক", besidePrimary = true)
        // the split that is wanted keeps its commit; the joined form is only a chip
        assertEquals(fold("বুঝতে পারছিনা"), commit("bujteparcina"))
    }

    @Test
    fun invariantsUntouched() {
        assertEquals("কাচ্চি", commit("kacci")); assertEquals("জোস", commit("jos")); assertEquals("নামে", commit("name"))
        assertEquals("কেমন", commit("kmon")); assertEquals(fold("হয়"), strip("hoi")[1])   // S151/S165 twin still beside
        assertTrue(strip("kotha").size <= 6)
    }
}
