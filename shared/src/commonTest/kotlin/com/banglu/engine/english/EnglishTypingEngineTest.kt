package com.banglu.engine.english

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** S96: the English typing suite, pinned. */
class EnglishTypingEngineTest {

    private fun fresh() = EnglishTypingEngine()

    // ── completion ───────────────────────────────────────────────────────

    @Test
    fun commonWordsCompleteByFrequency() {
        val e = fresh()
        val th = e.completions("th", 3)
        assertTrue(th.isNotEmpty(), "th must complete")
        assertTrue("the" in th, "the (top word) must lead: $th")
        assertTrue(e.completions("beca", 3).contains("because"), "beca→because")
        assertTrue(e.completions("peop", 3).contains("people"), "peop→people")
    }

    @Test
    fun typedWordItselfIsNeverEchoedAsACompletion() {
        val e = fresh()
        assertTrue(e.completions("the", 3).none { it.equals("the", ignoreCase = true) })
        assertFalse(e.completions("you", 3).any { it.equals("you", ignoreCase = true) })
    }

    @Test
    fun caseFollowsTheTypedPrefix() {
        val e = fresh()
        val cap = e.completions("Th", 3)
        assertTrue(cap.all { it.first().isUpperCase() }, "First-cap prefix → capitalized: $cap")
        val caps = e.completions("TH", 3)
        assertTrue(
            caps.all { w -> w.filter { it.isLetter() }.all { it.isUpperCase() } },
            "ALL-CAPS prefix → uppercase: $caps"
        )
    }

    @Test
    fun iAlwaysDisplaysCapitalized() {
        val e = fresh()
        assertTrue(e.predictions(null, 5).contains("I"), "sentence starters include I")
        val completions = e.completions("i'", 3)
        assertTrue(completions.all { it.startsWith("I'") }, "i-contractions capitalize: $completions")
    }

    @Test
    fun contractionsAreSuggestable() {
        val e = fresh()
        assertTrue(e.completions("don", 3).any { it == "don't" }, "don→don't")
        assertTrue(e.completions("can", 4).any { it == "can't" }, "can→can't")
    }

    @Test
    fun junkPrefixesAreRejected() {
        val e = fresh()
        assertTrue(e.completions("", 3).isEmpty())
        assertTrue(e.completions("123", 3).isEmpty())
        assertTrue(e.completions("থাকে", 3).isEmpty())
        assertTrue(e.completions("a b", 3).isEmpty())
    }

    // ── learning / saved words ───────────────────────────────────────────

    @Test
    fun repeatedCommitsBoostAWordAboveTheGlobalRanking() {
        val e = fresh()
        // "thermos" is deep in the list; enough personal commits must lift it
        // into the top completions for "th".
        repeat(8) { e.recordCommit("thermos") }
        assertTrue(
            e.completions("th", 3).contains("thermos"),
            "personal usage must outrank the global list: ${e.completions("th", 3)}"
        )
    }

    @Test
    fun unknownWordsBecomeSuggestableAfterTwoUses() {
        val e = fresh()
        e.recordCommit("shahabul")
        assertTrue(
            e.completions("shah", 3, personalDictionary = false).isEmpty(),
            "one use is not yet a saved word without personal dictionary"
        )
        e.recordCommit("shahabul")
        assertTrue(
            e.completions("shah", 3, personalDictionary = false).contains("shahabul"),
            "second use saves the word"
        )
    }

    @Test
    fun personalDictionarySavesUnknownWordsImmediately() {
        val e = fresh()
        e.recordCommit("banglu")
        assertTrue(e.completions("bang", 3, personalDictionary = true).contains("banglu"))
    }

    @Test
    fun learningIgnoresJunk() {
        val e = fresh()
        e.recordCommit("x")            // single letters other than a/i
        e.recordCommit("hello123")
        e.recordCommit("থাকে")
        e.recordCommit("'quote")
        assertEquals(0, e.userWordCount("x"))
        assertEquals(0, e.userWordCount("hello123"))
    }

    // ── next-word prediction ─────────────────────────────────────────────

    @Test
    fun bigramsPredictTheUsersNextWord() {
        val e = fresh()
        repeat(3) { e.recordCommit("morning", prevRaw = "good") }
        assertEquals("morning", e.predictions("good", 3).first())
    }

    @Test
    fun predictionsFallBackToCommonStarters() {
        val e = fresh()
        val p = e.predictions(null, 3)
        assertEquals(3, p.size)
        assertTrue(p.any { it == "I" || it == "you" || it == "the" }, "starters: $p")
    }

    // ── autocorrect (S97) ────────────────────────────────────────────────

    @Test
    fun classicTyposAutocorrect() {
        val e = fresh()
        assertEquals("the", e.autocorrect("teh"))
        assertEquals("this", e.autocorrect("thsi"))
        assertEquals("hello", e.autocorrect("helo"))
        assertEquals("world", e.autocorrect("wrold"))
    }

    @Test
    fun apostropheTyposAutocorrect() {
        val e = fresh()
        // doesnt/wasnt are NOT wordlist tokens — the apostrophe edit fixes them.
        assertEquals("doesn't", e.autocorrect("doesnt"))
        assertEquals("wasn't", e.autocorrect("wasnt"))
        // dont/cant ARE wordlist tokens (subtitle register) — the known-word
        // guard leaves the informal spelling alone, deliberately.
        assertEquals(null, e.autocorrect("dont"))
        assertEquals(null, e.autocorrect("cant"))
    }

    @Test
    fun caseMirrorsTheTypedWord() {
        val e = fresh()
        assertEquals("The", e.autocorrect("Teh"))
    }

    @Test
    fun knownWordsAreNeverCorrected() {
        val e = fresh()
        assertEquals(null, e.autocorrect("hello"))
        assertEquals(null, e.autocorrect("because"))
        assertEquals(null, e.autocorrect("don't"))
    }

    @Test
    fun usersOwnWordsAreNeverCorrected() {
        val e = fresh()
        // One use — exactly what the undo chip records — is enough.
        e.recordCommit("sami")
        assertEquals(null, e.autocorrect("sami"))
    }

    @Test
    fun acronymsShortAndFarWordsAreLeftAlone() {
        val e = fresh()
        assertEquals(null, e.autocorrect("TEH"), "ALL-CAPS is deliberate")
        assertEquals(null, e.autocorrect("te"), "too short to judge")
        assertEquals(null, e.autocorrect("shahabul"), "no edit-1 common word")
        assertEquals(null, e.autocorrect("xqzvk"), "junk with no near word")
    }

    // ── persistence ──────────────────────────────────────────────────────

    @Test
    fun serializeLoadRoundTripsLearning() {
        val e = fresh()
        repeat(3) { e.recordCommit("banglu") }
        repeat(2) { e.recordCommit("keyboard", prevRaw = "banglu") }
        val data = e.serialize()

        val e2 = fresh()
        e2.load(data)
        assertEquals(3, e2.userWordCount("banglu"))
        assertEquals("keyboard", e2.predictions("banglu", 1).first())
    }

    @Test
    fun loadToleratesGarbage() {
        val e = fresh()
        e.load("w\tok\t3\nnot-a-line\nb\tonly\nw\tbad\tNaN\nb\ta\tb\t2\n")
        assertEquals(3, e.userWordCount("ok"))
        assertEquals("b", e.predictions("a", 1).first())
    }

    @Test
    fun userWordStoreStaysBounded() {
        val e = fresh()
        repeat(2600) { i -> e.recordCommit("wordnumber$i") }
        val data = e.serialize()
        val rows = data.lineSequence().count { it.startsWith("w\t") }
        assertTrue(rows <= 2000, "user words must stay capped, got $rows")
    }
}
