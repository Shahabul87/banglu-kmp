package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S33 acceptance (tester report 2026-07-12, db 3.8.4):
 *
 * 1. sya_cc habit rule — the স্য gemination class typed with cc:
 *    "somocca" must reach সমস্যা (was সমচ্চা raw transliteration, সমস্যা
 *    absent even from suggestions).
 * 2. chh-promote pass — archaic চ্চ literary forms (হচ্চে, পাচ্চি, যাচ্চে)
 *    romanize straight to "cc" keys and owned them at canonical priority,
 *    shadowing the modern চ্ছ word everyone means. The modern word must now
 *    come first; the archaic form stays reachable in suggestions.
 * 3. কাচ্চি guard — the dish outranks কাচছি on frequency, so the promote
 *    pass must NOT touch "kacci".
 * 4. Regression locks for the rest of the tester's list, which already
 *    worked in 3.8.3 (final-cluster o-drop class and the S27 ss family).
 */
class S33ChatSpellingGapsJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun somoccaReachesSomossya() {
        assertEquals("সমস্যা", engine.convertWord("somocca").bengali)
        assertEquals("সমস্যা", engine.convertWord("somossa").bengali)
        assertEquals("সমস্যা", engine.convertWord("somosya").bengali)
    }

    @Test
    fun modernChhFormsBeatArchaicCcOwners() {
        assertEquals("পাচ্ছি", engine.convertWord("pacci").bengali)
        assertEquals("হচ্ছে", engine.convertWord("hocce").bengali)
        assertEquals("যাচ্ছে", engine.convertWord("jacce").bengali)
        // The archaic form is demoted, not erased.
        assertTrue(engine.getSuggestions("pacci", 5).any { it.bengali == "পাচ্চি" })
    }

    @Test
    fun kacchiDishStillOwnsItsKey() {
        assertEquals("কাচ্চি", engine.convertWord("kacci").bengali)
    }

    @Test
    fun finalClusterODropRegressionLocks() {
        // Tester: "কিছু শব্দ লিখতে গেলে o দিতে হচ্ছে" — already handled;
        // these locks keep it that way.
        assertEquals("গল্প", engine.convertWord("golp").bengali)
        assertEquals("গল্প", engine.convertWord("golpo").bengali)
        assertEquals("শব্দ", engine.convertWord("shobd").bengali)
        assertEquals("শব্দ", engine.convertWord("sobdo").bengali)
        assertEquals("তর্ক", engine.convertWord("tork").bengali)
        assertEquals("স্বপ্ন", engine.convertWord("sopn").bengali)
        assertEquals("বন্ধ", engine.convertWord("bondh").bengali)
        assertEquals("রক্ত", engine.convertWord("rokt").bengali)
    }

    @Test
    fun s27FamilyRegressionLocks() {
        assertEquals("আচ্ছা", engine.convertWord("acca").bengali)
        assertEquals("আচ্ছা", engine.convertWord("assa").bengali)
        assertEquals("যাচ্ছি", engine.convertWord("jacci").bengali)
        assertEquals("যাচ্ছি", engine.convertWord("jassi").bengali)
        assertEquals("পাচ্ছি", engine.convertWord("passi").bengali)
    }
}
