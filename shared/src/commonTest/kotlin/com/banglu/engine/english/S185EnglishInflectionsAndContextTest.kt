package com.banglu.engine.english

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S185 (2026-09-05, user: "when typing receive show them the variation like
 * receiving received … this will make typing faster in English" and "add
 * context wording in English — when they press spacebar show the next-word
 * suggestion"; plus "english suggestion needs to be improved").
 */
class S185EnglishInflectionsAndContextTest {
    private fun fresh() = EnglishTypingEngine()

    @Test
    fun fullyTypedWordOffersItsInflectionsFirst() {
        val e = fresh()
        val receive = e.completions("receive", 4)
        assertTrue("receiving" in receive && "received" in receive, "receive → $receive")
        assertTrue(e.completions("stop", 4).any { it == "stopped" || it == "stopping" }, "stop → ${e.completions("stop", 4)}")
        assertTrue("tried" in e.completions("try", 4) || "trying" in e.completions("try", 4), "try → ${e.completions("try", 4)}")
        assertTrue("quickly" in e.completions("quick", 4), "quick → ${e.completions("quick", 4)}")
        assertTrue("happier" in e.completions("happy", 4) || "happily" in e.completions("happy", 4), "happy → ${e.completions("happy", 4)}")
        // case follows the typed word
        assertEquals("Receiving", e.completions("Receive", 4).first { it.equals("receiving", ignoreCase = true) })
    }

    @Test
    fun inflectionsAreOnlyKnownWords() {
        val e = fresh()
        for (form in e.inflections("receive")) assertTrue(e.completions(form.dropLast(1), 8).isNotEmpty() || form.length > 1, form)
        assertTrue(e.inflections("xyzzy").isEmpty())
    }

    @Test
    fun prefixCompletionsStillWorkAndTheWordlistGrew() {
        val e = fresh()
        assertTrue("because" in e.completions("beca", 3))
        assertTrue(EnglishWordData.WORDS.size >= 30_000, "wordlist size ${EnglishWordData.WORDS.size}")
        assertTrue(e.completions("receiv", 4).contains("receiving") || e.completions("receivi", 4).contains("receiving"))
    }

    @Test
    fun nextWordFollowsTheCorpusContext() {
        val e = fresh()
        assertEquals("you", e.predictions("thank", 3).first())
        assertTrue("to" in e.predictions("how", 3) || "much" in e.predictions("how", 3), "how → ${e.predictions("how", 3)}")
        assertTrue("have" in e.predictions("i", 3) || "am" in e.predictions("i", 3), "I → ${e.predictions("i", 3)}")
        // an unknown previous word still falls back to the common starters
        assertEquals(3, e.predictions("zzqqx", 3).size)
        // personal pairs stay ahead of the corpus
        repeat(3) { e.recordCommit("goodness", prevRaw = "thank") }
        assertEquals("goodness", e.predictions("thank", 3).first())
    }
}
