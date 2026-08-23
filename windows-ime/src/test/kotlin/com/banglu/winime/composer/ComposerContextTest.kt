package com.banglu.winime.composer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S130: the context lane. Android and banglu-web convert with the last two
 * committed words (trigram/bigram rerank), learn adjacent commit pairs, and
 * predict the next word after a commit; the Windows lane called the engine
 * bare, which is why the same engine produced different Bangla here. These
 * tests pin the Composer's context ledger against a spy engine — the real-
 * dictionary parity wall lives in S130ContextParityTest.
 */
class ComposerContextTest {

    private class SpyEngine : ComposerEngine {
        val convertCalls = mutableListOf<Triple<String, String?, String?>>()
        val suggestCalls = mutableListOf<Triple<String, String?, String?>>()
        val recorded = mutableListOf<Pair<String, String>>()
        val predictCalls = mutableListOf<Pair<String?, String>>()
        var predictAnswer: List<String> = emptyList()

        private val table = mapOf(
            "ami" to "আমি", "kmon" to "কেমন", "acho" to "আছো", "valo" to "ভালো",
        )

        // Prefixes convert through the same table-less fallback the letters
        // build up through; only the full words matter to these tests.
        override fun instant(raw: String): String = table[raw] ?: raw

        override fun convert(raw: String, prev1: String?, prev2: String?): String {
            convertCalls += Triple(raw, prev1, prev2)
            return table[raw] ?: raw
        }

        override fun suggest(raw: String, limit: Int, prev1: String?, prev2: String?): List<String> {
            suggestCalls += Triple(raw, prev1, prev2)
            return listOfNotNull(table[raw])
        }

        override fun recordCommitPair(prev: String, next: String) {
            recorded += prev to next
        }

        override fun predictNext(prev2: String?, prev1: String, limit: Int): List<String> {
            predictCalls += prev2 to prev1
            return predictAnswer
        }
    }

    private fun type(c: Composer, s: String): List<ComposerAction> =
        s.flatMap { c.handle(ComposerKey.Letter(it)) }

    private fun word(c: Composer, s: String): List<ComposerAction> =
        type(c, s) + c.handle(ComposerKey.Space)

    private fun lastCandidates(actions: List<ComposerAction>): ComposerAction.Candidates =
        actions.filterIsInstance<ComposerAction.Candidates>().last()

    // ── the conversion context ────────────────────────────────────────────

    @Test
    fun theFirstWordConvertsWithNoContext() {
        val engine = SpyEngine()
        type(Composer(engine), "ami")
        assertEquals(Triple("ami", null, null), engine.convertCalls.last())
    }

    @Test
    fun liveConversionCarriesTheLastTwoCommittedWords() {
        val engine = SpyEngine()
        val c = Composer(engine)
        word(c, "ami")
        word(c, "kmon")
        type(c, "acho")
        // The third word is converted knowing the two Bengali words already
        // in the document — the same call shape as Android's
        // convertWordWithContext(prev2 = আমি, prev1 = কেমন).
        assertEquals(Triple("acho", "কেমন", "আমি"), engine.convertCalls.last())
    }

    @Test
    fun suggestionsCarryTheSameContext() {
        val engine = SpyEngine()
        val c = Composer(engine)
        word(c, "ami")
        type(c, "kmon")
        c.refineCandidates(c.generation)
        assertEquals(Triple("kmon", "আমি", null), engine.suggestCalls.last())
    }

    @Test
    fun aCandidatePickEntersTheContextLedger() {
        val engine = SpyEngine()
        val c = Composer(engine)
        type(c, "kmon")
        c.refineCandidates(c.generation)
        c.pick(0) // কেমন — commits word + space, exactly like Space
        type(c, "acho")
        assertEquals(Triple("acho", "কেমন", null), engine.convertCalls.last())
    }

    // ── recording adjacent pairs ──────────────────────────────────────────

    @Test
    fun adjacentCommitsRecordTheirPair() {
        val engine = SpyEngine()
        val c = Composer(engine)
        word(c, "ami")
        word(c, "kmon")
        assertEquals(listOf("আমি" to "কেমন"), engine.recorded)
    }

    @Test
    fun punctuationBetweenWordsBreaksRecordingButKeepsContext() {
        val engine = SpyEngine()
        val c = Composer(engine)
        word(c, "ami")
        c.handle(ComposerKey.Punctuation(",")) // আমি, — swallows the space
        word(c, "kmon")
        // Android's adjacency check ("আমি, কেমন") refuses this pair too…
        assertTrue(engine.recorded.isEmpty(), "no pair across punctuation, got ${engine.recorded}")
        // …but its conversion ledger is untouched by punctuation, so the
        // rerank context survives (parity with the Android IME).
        assertEquals(Triple("kmon", "আমি", null), engine.convertCalls.last())
    }

    @Test
    fun aDariBreaksRecording() {
        val engine = SpyEngine()
        val c = Composer(engine)
        word(c, "ami")
        c.handle(ComposerKey.Space) // দাঁড়ি — sentence over
        word(c, "kmon")
        assertTrue(engine.recorded.isEmpty(), "no pair across a দাঁড়ি, got ${engine.recorded}")
    }

    @Test
    fun enterBetweenWordsBreaksRecordingForTheNextPairOnly() {
        val engine = SpyEngine()
        val c = Composer(engine)
        word(c, "ami")
        type(c, "kmon")
        c.handle(ComposerKey.Enter) // commits কেমন, then forwards Enter
        word(c, "acho")
        // (আমি, কেমন) was space-separated — recorded. (কেমন, আছো) straddles
        // the Enter — on Windows that may be a new field entirely, so it is not.
        assertEquals(listOf("আমি" to "কেমন"), engine.recorded)
    }

    @Test
    fun anEscapedRawWordIsNeverRecordedButBecomesTheContext() {
        val engine = SpyEngine()
        val c = Composer(engine)
        word(c, "ami")
        type(c, "fb")
        c.handle(ComposerKey.Escape) // keeps "fb" as typed
        word(c, "kmon")
        // Nothing recorded: fb is not Bengali on either side of a pair.
        assertTrue(engine.recorded.isEmpty(), "got ${engine.recorded}")
        // But the word before কেমন in the document IS "fb" — the ledger says so.
        assertEquals(Triple("kmon", "fb", "আমি"), engine.convertCalls.last())
    }

    @Test
    fun focusLossClearsTheContext() {
        val engine = SpyEngine()
        val c = Composer(engine)
        word(c, "ami")
        c.focusLost() // new window — the old document's words prove nothing here
        type(c, "kmon")
        assertEquals(Triple("kmon", null, null), engine.convertCalls.last())
    }

    @Test
    fun aForwardedBackspaceClearsTheContext() {
        val engine = SpyEngine()
        val c = Composer(engine)
        word(c, "ami")
        c.handle(ComposerKey.Letter('k'))
        assertEquals(Triple("k", "আমি", null), engine.convertCalls.last())
        c.handle(ComposerKey.Backspace) // empties the forming word — still ours
        c.handle(ComposerKey.Backspace) // not forming: edits COMMITTED text
        type(c, "kmon")
        // The user reached into text we no longer track; আমি may be gone.
        assertEquals(Triple("kmon", null, null), engine.convertCalls.last())
        assertTrue(engine.recorded.isEmpty(), "got ${engine.recorded}")
    }

    // ── next-word predictions ─────────────────────────────────────────────

    @Test
    fun aSpaceCommitShowsNextWordPredictions() {
        val engine = SpyEngine()
        engine.predictAnswer = listOf("ভালো", "আছো")
        val c = Composer(engine)
        val actions = word(c, "ami")
        assertEquals(null to "আমি", engine.predictCalls.last())
        val strip = lastCandidates(actions)
        assertEquals(listOf("ভালো", "আছো"), strip.list)
        assertTrue(strip.predictions, "the strip must know these are predictions, not candidates")
    }

    @Test
    fun pickingAPredictionCommitsWordAndSpaceAndRecordsThePair() {
        val engine = SpyEngine()
        engine.predictAnswer = listOf("ভালো", "আছো")
        val c = Composer(engine)
        word(c, "ami")
        val actions = c.pick(0)
        val committed = actions.filterIsInstance<ComposerAction.Commit>().joinToString("") { it.text }
        assertEquals("ভালো ", committed)
        assertTrue("আমি" to "ভালো" in engine.recorded, "got ${engine.recorded}")
        assertTrue(c.retroSpaceArmed, "a prediction commit ends in our space — দাঁড়ি must work after it")
        // The chain continues: predictions for the NEXT word, with the ledger shifted.
        assertEquals("আমি" to "ভালো", engine.predictCalls.last())
    }

    @Test
    fun digitsAfterASpaceStillTypeDigits() {
        val engine = SpyEngine()
        engine.predictAnswer = listOf("ভালো")
        val c = Composer(engine)
        word(c, "ami")
        val actions = c.handle(ComposerKey.Digit('1'))
        val committed = actions.filterIsInstance<ComposerAction.Commit>().joinToString("") { it.text }
        assertEquals("১", committed) // NOT a pick of ভালো
        assertTrue(lastCandidates(actions).list.isEmpty(), "a digit ends the prediction strip")
    }

    @Test
    fun aLetterAfterPredictionsStartsTheNextWordAndAStalePickIsInert() {
        val engine = SpyEngine()
        engine.predictAnswer = listOf("ভালো")
        val c = Composer(engine)
        word(c, "ami")
        type(c, "k")
        // The strip may still be painting predictions for one debounce beat; a
        // click on it must not commit ভালো into the middle of the new word.
        assertTrue(c.pick(0).isEmpty())
    }

    @Test
    fun punctuationAfterPredictionsHidesTheStrip() {
        val engine = SpyEngine()
        engine.predictAnswer = listOf("ভালো")
        val c = Composer(engine)
        word(c, "ami")
        val actions = c.handle(ComposerKey.Punctuation("."))
        assertTrue(lastCandidates(actions).list.isEmpty(), "sentence over — no stale predictions")
    }

    @Test
    fun escapeAfterPredictionsHidesTheStrip() {
        val engine = SpyEngine()
        engine.predictAnswer = listOf("ভালো")
        val c = Composer(engine)
        word(c, "ami")
        val actions = c.handle(ComposerKey.Escape)
        assertTrue(lastCandidates(actions).list.isEmpty())
    }
}
