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
        // S190 pin flip (documented): dictionary 3.9.8 carries জয়শঙ্কর as a
        // tier-B proper noun (Wikipedia people titles), so the exact word now
        // owns the commit and the SPLIT form is the strip alternative — the
        // twin mechanism's job done by data. kathomandubhittik stays OOV and
        // still exercises the joined twin.
        assertEquals(fold("জয়শঙ্কর"), commit("joyoshongkor"))
        assertEquals(fold("কাঠমান্ডুভিত্তিক"), commit("kathomandubhittik"))   // S190: news-harvest compound, now a word
        // The mechanism itself is pinned on a split that is a deliberate engine
        // decision and can never become a dictionary word (S-round law, invariant 6).
        pin("bujteparcina", "বুঝতে পারছিনা", "বুঝতেপারছিনা", besidePrimary = true)
    }

    @Test
    fun invariantsUntouched() {
        assertEquals("কাচ্চি", commit("kacci")); assertEquals("জোস", commit("jos")); assertEquals("নামে", commit("name"))
        assertEquals("কেমন", commit("kmon")); assertEquals(fold("হয়"), strip("hoi")[1])   // S151/S165 twin still beside
        assertTrue(strip("kotha").size <= 6)
    }
}
