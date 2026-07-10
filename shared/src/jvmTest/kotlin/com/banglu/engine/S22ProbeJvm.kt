package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.banglu.engine.util.ReverseTransliterator

/**
 * S22 acceptance (tester-reported round): glued two-word compounds and
 * edit-distance-1 typo correction.
 */
class GluedCompoundAndTypoJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun gluedCompounds_splitIntoRealPhrases() {
        assertEquals("বুঝতে পারছিনা", engine.convertWord("bujteparcina").bengali)
        assertEquals("করতে পারছিনা", engine.convertWord("korteparcina").bengali)
        assertEquals("যেতে পারবোনা", engine.convertWord("jeteparbona").bengali)
        assertEquals("কিছু হবেনা", engine.convertWord("kichuhobena").bengali)
        assertEquals("দেখা হবে", engine.convertWord("dekhahobe").bengali)
        assertEquals("ভালো লাগছেনা", engine.convertWord("valolagchena").bengali)
    }

    @Test
    fun typoCorrection_editDistanceOneFindsIntendedWord() {
        assertEquals("কেমন", engine.convertWord("kmon").bengali, "vowel-skip")
        assertEquals("আমাদের", engine.convertWord("amdaer").bengali, "transposition")
        assertEquals("বাংলাদেশ", engine.convertWord("bangaldesh").bengali, "transposition")
        assertEquals("বুঝতেছিনা", engine.convertWord("bujjtecina").bengali, "doubled letter")
        assertEquals("বুঝতেছিনা", engine.convertWord("bujtecnia").bengali, "tail transposition")
    }

    @Test
    fun typoCorrection_keepsOriginalReachable() {
        val alts = engine.convertWord("kmon").alternatives.map { it.bengali }
        assertTrue("ক্মন" in alts, "literal must stay reachable, got $alts")
    }

    @Test
    fun confidentWordsAreNeverReguessed() {
        assertEquals("তোমরা", engine.convertWord("tomra").bengali)
        assertEquals("ভালোবাসা", engine.convertWord("valobasha").bengali)
        assertEquals("ধন্যবাদ", engine.convertWord("dhonnobad").bengali)
        // single real word one edit away beats a two-word split
        // (nukta-folded compare: source literal and engine differ in ড়/য় encoding)
        assertEquals(
            ReverseTransliterator.foldNukta("দুনিয়াজোড়া"),
            ReverseTransliterator.foldNukta(engine.convertWord("duniajora").bengali)
        )
        // deliberate rare store row survives (conversational verb)
        assertEquals("ঘুমাচ্ছ", engine.convertWord("ghumacco").bengali)
    }
}
