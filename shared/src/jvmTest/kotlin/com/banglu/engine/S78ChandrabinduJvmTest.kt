package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S78 (tester round 2026-08-04, "চন্দ্রবিন্দুর সমস্যা হচ্ছে বেচে তে"):
 * chandrabindu words were indexed ONLY under their nasal-"n" romanization
 * (বেঁচে under "benche"), so the nasal-dropped keys casual typists actually
 * press ("beche", "pach", "dat") never reached them — 18.8K words affected,
 * `ami beche achi` committed আমি বেছে আছি. Db 3.8.8 adds the nasal-drop
 * alias seed + the S33-style nasal-twin promotion (a ঁ-twin strictly more
 * frequent than the plain canonical owner takes the key at canonical
 * priority; the plain word stays one slot down).
 * Db-gated so pre-3.8.8 checkouts skip.
 */
class S78ChandrabinduJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    private fun assertSame(expected: String, actual: String, msg: String? = null) =
        assertEquals(fold(expected), fold(actual), msg)

    private fun storeHasNasalFix(): Boolean {
        // বেঁচে on the nasal-dropped key exists only from db 3.8.8 on.
        return engine.getSuggestions("beche", 8).any { fold(it.bengali) == fold("বেঁচে") }
    }

    @Test
    fun nasalDroppedKeysSurfaceChandrabinduPrimaries() {
        if (!storeHasNasalFix()) return // pre-3.8.8 db
        // Promoted class: the ঁ-form is more frequent than the plain twin,
        // so it owns the key outright.
        assertSame("বেঁচে", engine.convertWord("beche").bengali)
        assertSame("পাঁচ", engine.convertWord("pach").bengali)
        assertSame("দাঁত", engine.convertWord("dat").bengali)
        assertSame("চাঁদ", engine.convertWord("chad").bengali)
        assertSame("কাঁচা", engine.convertWord("kacha").bengali)
    }

    @Test
    fun theTesterPhraseCommitsAliveNotChosen() {
        if (!storeHasNasalFix()) return
        assertSame("আমি বেঁচে আছি", engine.parse("ami beche achi"))
    }

    @Test
    fun plainTwinsStayReachableOneSlotDown() {
        if (!storeHasNasalFix()) return
        val beche = engine.getSuggestions("beche", 5).map { fold(it.bengali) }
        assertTrue(fold("বেচে") in beche, "বেচে (sell) must stay reachable: $beche")
        assertTrue(fold("বেছে") in beche, "বেছে (choose) must stay reachable: $beche")
        val pach = engine.getSuggestions("pach", 5).map { fold(it.bengali) }
        assertTrue(fold("পাচ") in pach, "plain পাচ must stay reachable: $pach")
    }

    @Test
    fun nasalKeysStillOwnTheirWords() {
        // The canonical nasal romanization keeps working unchanged.
        assertSame("বেঁচে", engine.convertWord("benche").bengali)
        assertSame("পাঁচ", engine.convertWord("panch").bengali)
    }

    @Test
    fun invariantGuards() {
        // Invariant 6: the dish always wins; standard orthography pin.
        assertSame("কাচ্চি", engine.convertWord("kacci").bengali)
        assertSame("কাচছি", engine.convertWord("kassi").bengali)
    }
}
