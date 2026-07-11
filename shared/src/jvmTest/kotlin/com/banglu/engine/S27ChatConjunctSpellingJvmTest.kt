package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S27 acceptance: the ss-for-চ্ছ chat spelling family (tester report
 * 2026-07-11) — icca/issa = ইচ্ছা, acca/assa/acha = আচ্ছা, plus the
 * continuous-verb forms with the inherent-o dropped before ছ (korsi/korchi =
 * করছি). Generator: chch_ss + verb_o_drop_chh habit rules (db 3.8.3), seed
 * phonetics top-up for ইচ্ছা/ইচ্ছে/আচ্ছা.
 */
class S27ChatConjunctSpellingJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun icchaFamily() {
        assertEquals("ইচ্ছা", engine.convertWord("icca").bengali)
        assertEquals("ইচ্ছা", engine.convertWord("issa").bengali)
        assertEquals("ইচ্ছা", engine.convertWord("iccha").bengali)
        assertEquals("ইচ্ছে", engine.convertWord("icce").bengali)
        assertEquals("ইচ্ছে", engine.convertWord("isse").bengali)
        assertEquals("ইচ্ছে", engine.convertWord("icche").bengali)
    }

    @Test
    fun acchaFamily() {
        assertEquals("আচ্ছা", engine.convertWord("acca").bengali)
        assertEquals("আচ্ছা", engine.convertWord("assa").bengali)
        assertEquals("আচ্ছা", engine.convertWord("accha").bengali)
        assertEquals("আচ্ছা", engine.convertWord("acha").bengali)
    }

    @Test
    fun continuousVerbSsForms() {
        assertEquals("হচ্ছে", engine.convertWord("hosse").bengali)
        assertEquals("যাচ্ছে", engine.convertWord("jasse").bengali)
        assertEquals("খাচ্ছি", engine.convertWord("khassi").bengali)
        assertEquals("করছি", engine.convertWord("korsi").bengali)
        assertEquals("করছি", engine.convertWord("korci").bengali)
        assertEquals("করছি", engine.convertWord("korchi").bengali)
    }

    @Test
    fun realWordsUnaffected() {
        // ss/s keys that belong to real words must not be hijacked.
        assertEquals("আশা", engine.convertWord("asha").bengali)
        assertEquals("আছে", engine.convertWord("ache").bengali)
        val isaStrip = engine.getSuggestions("issa", 5).map { it.bengali }
        assertTrue("ইচ্ছা" in isaStrip)
    }
}
