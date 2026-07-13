package com.banglu.engine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S35: chat interjections (tester report 2026-07-12) — hm/ok/hae class. */
class S35ChatInterjectionJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test fun humFamily() {
        for (w in listOf("hm", "hmm", "hmmm", "hmn")) {
            assertEquals("হুম", engine.convertWord(w).bengali, "for '$w'")
            assertEquals("হুম", engine.convertForInstantPreview(w), "instant for '$w'")
        }
        assertEquals("হুম", engine.convertWord("hum").bengali)
    }

    @Test fun okAndYesFamily() {
        assertEquals("ওকে", engine.convertWord("ok").bengali)
        assertEquals("ওকে", engine.convertWord("okay").bengali)
        assertEquals("হ্যাঁ", engine.convertWord("hae").bengali)
        assertEquals("হ্যাঁ", engine.convertWord("haa").bengali)
        assertEquals("হ্যাঁ", engine.convertWord("hya").bengali)
    }

    @Test fun pronounShorthands() {
        assertEquals("তোমরা", engine.convertWord("tmra").bengali)
        assertEquals("তোমার", engine.convertWord("tmr").bengali)
        assertEquals("তোমাদের", engine.convertWord("tmder").bengali)
        assertEquals("আমাদের", engine.convertWord("amder").bengali)
        assertEquals("আমার", engine.convertWord("amr").bengali)
        assertEquals("তুমি", engine.convertWord("tmi").bengali)
    }

    @Test fun haKeepsLaughterPrimaryButSuggestsYes() {
        assertEquals("হা", engine.convertWord("ha").bengali)
        assertTrue(engine.getSuggestions("ha", 6).any { it.bengali == "হ্যাঁ" },
            "হ্যাঁ must be suggested for 'ha'")
    }
}
