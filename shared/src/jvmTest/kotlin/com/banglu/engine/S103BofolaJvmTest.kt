package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S103 (tester screenshot: ostitto -> অস্তিত তো): the ব-ফলা pronunciation
 * class. ্ব is silent — initial consonant stands alone (জ্বর "jor"), medial
 * doubles the preceding consonant (অস্তিত্ব "ostitto") — but the romanizer
 * spelled it letter-by-letter, so NO pronunciation key existed for 3,347
 * real words. Compiler bofolaPronunciationSeeds (db 3.9.1) fixed the class;
 * these pins are the round's deliberate decisions.
 */
class S103BofolaJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun medialBofolaDoublingResolvesThePronunciationKey() {
        assertEquals(fold("অস্তিত্ব"), fold(engine.convertWord("ostitto").bengali), "the tester's headline")
        assertEquals(fold("গুরুত্ব"), fold(engine.convertWord("gurutto").bengali))
        assertEquals(fold("দায়িত্ব"), fold(engine.convertWord("daitto").bengali))
        assertEquals(fold("নেতৃত্ব"), fold(engine.convertWord("netritto").bengali))
        assertEquals(fold("বন্ধুত্ব"), fold(engine.convertWord("bondhutto").bengali))
        assertEquals(fold("বিদ্বান"), fold(engine.convertWord("biddan").bengali))
    }

    @Test
    fun initialBofolaDropReachesTheWord() {
        // জোর/টক/ধনী rightly own the bare keys (canonical ownership law) —
        // the bo-fola words must be REACHABLE in the strip.
        val jor = engine.getSuggestions("jor", 6).map { fold(it.bengali) }
        assertTrue(fold("জ্বর") in jor, "জ্বর reachable for jor: $jor")
        val tok = engine.getSuggestions("tok", 6).map { fold(it.bengali) }
        assertTrue(fold("ত্বক") in tok, "ত্বক reachable for tok: $tok")
    }

    @Test
    fun theStripCarriesTheValidatedConjunctFirst() {
        // The tester's law: validated reading on top, the other reachable.
        val strip = engine.getSuggestions("ostitto", 6).map { fold(it.bengali) }
        assertEquals(fold("অস্তিত্ব"), strip.first(), "strip[0] is the commit contract: $strip")
    }

    @Test
    fun realBSoundsAreNeverCorrupted() {
        // ম্ব / র্ব / ব্ব keep their pronounced b — the gate must not touch them.
        assertEquals("লম্বা", engine.convertWord("lomba").bengali)
        assertEquals("পূর্ব", engine.convertWord("purbo").bengali)
        assertEquals("আব্বা", engine.convertWord("abba").bengali)
    }

    @Test
    fun testerTrioParticleSplitsSurvive() {
        // dekhisto/koristo/bolisto stay particle splits — their conjunct
        // readings are not real words, so the split IS the validated one.
        assertEquals("দেখিস তো", engine.convertWord("dekhisto").bengali)
        assertEquals("করিস তো", engine.convertWord("koristo").bengali)
        assertEquals("বলিস তো", engine.convertWord("bolisto").bengali)
    }

    /** Class-wide sweep — S103_STUDY=1. Derives each tier-A ্ব word's
     *  pronunciation keys straight from the store (its canonical-priority
     *  non-literal keys) and checks the word is reachable when typed. */
    @Test
    fun bofolaClassSweep() {
        if (System.getenv("S103_STUDY") != "1") return
        val db = TestDictionaryLoader.findDictionarySqlite()
        data class Case(val key: String, val bengali: String)
        val cases = ArrayList<Case>()
        java.sql.DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    """SELECT p.key, w.bengali FROM phonetic_index p
                       JOIN words w ON w.id = p.word_id
                       WHERE w.bengali LIKE '%্ব%' AND p.tier = 0 AND p.priority = 0
                         AND p.key NOT LIKE '%b%' AND p.key NOT LIKE '%w%'"""
                ).use { rs -> while (rs.next()) cases.add(Case(rs.getString(1), rs.getString(2))) }
            }
        }
        var primary = 0
        var strip = 0
        var miss = 0
        val misses = ArrayList<String>()
        for (c in cases) {
            val got = fold(engine.convertWord(c.key).bengali)
            when {
                got == fold(c.bengali) -> primary++
                fold(c.bengali) in engine.getSuggestions(c.key, 6).map { fold(it.bengali) } -> strip++
                else -> {
                    miss++
                    if (misses.size < 30) misses.add("${c.key}: want ${c.bengali} got $got")
                }
            }
        }
        println("S103 SWEEP: ${cases.size} pronunciation keys — primary=$primary strip=$strip miss=$miss")
        misses.forEach { println("S103 MISS $it") }
    }
}
