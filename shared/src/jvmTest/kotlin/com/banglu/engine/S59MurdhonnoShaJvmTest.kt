package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S59 (tester drawing 2026-07-21): the মূর্ধন্য-ষ family. The class was
 * already broadly correct (36/40 sweep both tiers); these pins cover the
 * four reproduced gaps closed by MANUAL_ALIASES in db 3.8.7, plus the
 * word-initial ষ words that must never regress.
 * Db-gated so pre-3.8.7 checkouts skip the alias pins.
 */
class S59MurdhonnoShaJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    /** Nukta-normalized equality: ড়/ঢ়/য় precomposed vs combining forms are
     *  the same text — the engine emits the folded form. */
    private fun assertSame(expected: String, actual: String, msg: String? = null) {
        assertEquals(
            ReverseTransliterator.foldNukta(expected),
            ReverseTransliterator.foldNukta(actual),
            msg
        )
    }

    private fun storeHasKey(key: String): Boolean {
        val m = engine.javaClass.declaredMethods.first { it.name == "storeLookup" }
        m.isAccessible = true
        return (m.invoke(engine, key) as List<*>).isNotEmpty()
    }

    @Test
    fun manualAliasGapsClosed() {
        if (!storeHasKey("mushkil")) return // pre-3.8.7 db
        // shar: the frequency law keeps সার@78 as the full-tier primary —
        // the contract is that ষাঁড় is on the key and one tap away.
        val sharSugg = engine.getSuggestions("shar", 6).map {
            ReverseTransliterator.foldNukta(it.bengali)
        }
        kotlin.test.assertTrue(
            ReverseTransliterator.foldNukta("ষাঁড়") in sharSugg,
            "ষাঁড় missing from shar suggestions: $sharSugg"
        )
        assertSame("মুশকিল", engine.convertWord("mushkil").bengali)
        assertSame("মুশকিল", engine.convertWord("muskil").bengali)
        assertSame("ঐতিহ্য", engine.convertWord("oitijjho").bengali)
        assertSame("ঔষুধ", engine.convertWord("oushud").bengali)
    }

    @Test
    fun wordInitialMurdhonnoSha() {
        assertSame("ষাঁড়", engine.convertWord("shaar").bengali)
        assertSame("ষাট", engine.convertWord("shat").bengali)
        assertSame("ষষ্ঠ", engine.convertWord("shoshtho").bengali)
        assertSame("ষড়যন্ত্র", engine.convertWord("shorojontro").bengali)
        assertSame("ষোলো", engine.convertWord("sholo").bengali)
    }

    @Test
    fun medialMurdhonnoShaStaysCorrect() {
        assertEquals("বিশেষ", engine.convertWord("bishesh").bengali)
        assertEquals("মানুষ", engine.convertWord("manush").bengali)
        assertEquals("ভাষা", engine.convertWord("bhasha").bengali)
        assertSame("কৃষক", engine.convertWord("krishok").bengali)
        assertEquals("বৃষ্টি", engine.convertWord("bristi").bengali)
        assertEquals("মিষ্টি", engine.convertWord("misti").bengali)
        assertEquals("চেষ্টা", engine.convertWord("chesta").bengali)
        assertEquals("ঘোষণা", engine.convertWord("ghoshona").bengali)
        assertEquals("অনুষ্ঠান", engine.convertWord("onushthan").bengali)
        assertEquals("পরিষ্কার", engine.convertWord("porishkar").bengali)
        assertEquals("স্বাস্থ্য", engine.convertWord("shastho").bengali)
        assertEquals("ভবিষ্যৎ", engine.convertWord("bhobishyot").bengali)
    }

    @Test
    fun collisionWordsKeepFrequencyPrimaryWithShaSuggested() {
        // দশ/দোষ and বিশ/বিষ: frequency picks the primary, the ষ twin must
        // stay one tap away on the strip.
        assertEquals("দশ", engine.convertWord("dosh").bengali)
        val doshSugg = engine.getSuggestions("dosh", 6).map { it.bengali }
        kotlin.test.assertTrue("দোষ" in doshSugg, "দোষ missing from dosh suggestions: $doshSugg")
        assertEquals("বিশ", engine.convertWord("bish").bengali)
        val bishSugg = engine.getSuggestions("bish", 6).map { it.bengali }
        kotlin.test.assertTrue("বিষ" in bishSugg, "বিষ missing from bish suggestions: $bishSugg")
    }
}
