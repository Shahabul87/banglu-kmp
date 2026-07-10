package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S23 acceptance: English words typed in English spelling produce the
 * conventional Bengali loanword rendering (tester-reported class), with
 * curated overrides beating CMU schwa artifacts (db 3.8.1).
 */
class EnglishLoanwordJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun testerReportedWordsRenderConventionally() {
        assertEquals("অবিশ্বাস্য", engine.convertWord("obisasso").bengali)
        assertEquals("এপ্লিকেশন", engine.convertWord("application").bengali)
        assertEquals("মোবাইল", engine.convertWord("mobile").bengali)
        assertEquals("হানিমুন", engine.convertWord("honeymoon").bengali)
        assertEquals("ডিলিট", engine.convertWord("delete").bengali)
        assertEquals("ইন্টারেস্টিং", engine.convertWord("interesting").bengali)
    }

    @Test
    fun curatedOverridesBeatCmuArtifacts() {
        assertEquals("গভর্নমেন্ট", engine.convertWord("government").bengali)
        assertEquals("সোসাইটি", engine.convertWord("society").bengali)
        assertEquals("বিউটিফুল", engine.convertWord("beautiful").bengali)
        assertEquals("ব্রিলিয়ান্ট", engine.convertWord("brilliant").bengali)
        assertEquals("ওয়ান্ডারফুল", engine.convertWord("wonderful").bengali)
        assertEquals("সাপোর্ট", engine.convertWord("support").bengali)
        assertEquals("টেকনোলজি", engine.convertWord("technology").bengali)
    }

    @Test
    fun englishSpellingStaysReachableInStrip() {
        val strip = engine.getSuggestions("mobile", 5).map { it.bengali }
        assertTrue("mobile" in strip, "Latin passthrough must stay tappable: $strip")
    }
}
