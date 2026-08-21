package com.banglu.winime.composer

import com.banglu.engine.SmartEngineAdapter
import com.banglu.winime.TestEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pin tests for the Windows port of macos-ime/Sources/BangluCore/Composer.swift.
 * Runs against the real engine/dictionary via TestEngine.boot() — same gate
 * the macOS BangluCoreTestRunner uses, minus the Swift/JSC hosting.
 */
class ComposerTest {

    private object RealComposerEngine : ComposerEngine {
        override fun instant(raw: String): String =
            SmartEngineAdapter.convertForInstantPreview(raw)

        override fun convert(raw: String): String = SmartEngineAdapter.convertWord(raw).bengali
        override fun suggest(raw: String, limit: Int): List<String> =
            SmartEngineAdapter.getSuggestions(raw, limit).map { it.bengali }
    }

    private fun composer(): Composer {
        TestEngine.boot()
        return Composer(RealComposerEngine)
    }

    private fun commits(actions: List<ComposerAction>) =
        actions.filterIsInstance<ComposerAction.Commit>().joinToString("") { it.text }

    private fun previews(actions: List<ComposerAction>) =
        actions.filterIsInstance<ComposerAction.Preview>()

    private fun candidates(actions: List<ComposerAction>) =
        actions.filterIsInstance<ComposerAction.Candidates>().last().list

    private fun type(c: Composer, s: String): List<ComposerAction> =
        s.flatMap { c.handle(ComposerKey.Letter(it)) }

    /**
     * What the controller's debounce does once the user pauses. Suggestions
     * cost ~7.5 ms per call against the real dictionary, so they are NOT part
     * of the typing path any more: a test that wants candidates has to ask for
     * them, exactly as the running app does.
     */
    private fun settle(c: Composer): List<ComposerAction> = c.refineCandidates(c.generation)

    @Test
    fun spaceCommitsExactlyThePreview() {
        val c = composer()
        val typed = type(c, "ami")
        val lastPreview = previews(typed).last()
        assertEquals("আমি", lastPreview.bangla)
        assertEquals("ami", lastPreview.raw)
        // WYSIWYG law: the space commit must equal the last previewed
        // Bangla exactly — never a re-conversion of the raw buffer.
        assertEquals(lastPreview.bangla, commits(c.handle(ComposerKey.Space)))
    }

    @Test
    fun doubleSpaceMakesDari() {
        val c = composer()
        type(c, "ami")
        assertEquals("আমি", commits(c.handle(ComposerKey.Space)))
        assertEquals("। ", commits(c.handle(ComposerKey.Space)))
    }

    @Test
    fun tripleSpaceAlternates() {
        val c = composer()
        type(c, "ami")
        assertEquals("আমি", commits(c.handle(ComposerKey.Space)))
        assertEquals("। ", commits(c.handle(ComposerKey.Space)))
        // dariJustCommitted flips back off: third space is a plain space,
        // not another দাঁড়ি.
        assertEquals(" ", commits(c.handle(ComposerKey.Space)))
    }

    @Test
    fun letterAfterPendingSpaceReleasesPlainSpace() {
        val c = composer()
        type(c, "ami")
        c.handle(ComposerKey.Space) // commits আমি, holds the space
        assertTrue(c.pendingSpace)
        val actions = c.handle(ComposerKey.Letter('k'))
        assertEquals(" ", commits(actions))
        assertFalse(c.pendingSpace)
        assertTrue(c.forming)
        val preview = previews(actions).last()
        assertEquals("k", preview.raw)
    }

    @Test
    fun tightPunctuationSwallowsPendingSpace() {
        val c = composer()
        type(c, "ami")
        c.handle(ComposerKey.Space)
        assertEquals(",", commits(c.handle(ComposerKey.Punctuation(","))))
    }

    @Test
    fun periodMapsToDariAndIsTight() {
        val c = composer()
        type(c, "ami")
        c.handle(ComposerKey.Space)
        // "." maps to দাঁড়ি first, THEN the tight-punctuation check runs on
        // the mapped character — no space is inserted before it.
        assertEquals("।", commits(c.handle(ComposerKey.Punctuation("."))))
    }

    @Test
    fun digitsCommitBengali() {
        val c = composer()
        assertFalse(c.forming)
        assertEquals("৫", commits(c.handle(ComposerKey.Digit('5'))))
    }

    @Test
    fun digitPicksCandidateWhileForming() {
        val c = composer()
        var picked: Triple<String, String, Boolean>? = null
        c.onPick = { raw, bangla, wasPrimary -> picked = Triple(raw, bangla, wasPrimary) }
        type(c, "kmn") // primary কেমন, candidate[1] কেম (real-engine pin)
        settle(c)
        val actions = c.handle(ComposerKey.Digit('2'))
        assertEquals("কেম", commits(actions))
        assertEquals(Triple("kmn", "কেম", false), picked)
        assertTrue(c.pendingSpace)
        assertFalse(c.forming)
    }

    @Test
    fun theRawRomanEscapeHatchStaysWithinDigitAndChipReach() {
        val c = composer()
        type(c, "kmn")
        val list = candidates(settle(c))
        // The engine is asked for MAX_CANDIDATES - 1 precisely so the raw
        // roman appended after it is the LAST entry of at most MAX_CANDIDATES.
        // Asking for a full six put the escape hatch at index 6, where neither
        // a digit (Composer bounds them to 1..6) nor a chip (the preview strip
        // renders MAX_CANDIDATES of them) could ever reach it.
        assertEquals(6, list.size, "a word with plenty of candidates must fill the window exactly")
        assertEquals("kmn", list.last(), "the raw roman is always the last candidate")
        // …and the digit for that last position really commits it.
        assertEquals("kmn", commits(c.handle(ComposerKey.Digit('6'))))
        assertFalse(c.forming)
    }

    @Test
    fun backspaceEditsFormingBuffer() {
        val c = composer()
        type(c, "amii")
        val actions = c.handle(ComposerKey.Backspace)
        val preview = previews(actions).last()
        assertEquals("ami", preview.raw)
        assertEquals("আমি", preview.bangla)
        assertTrue(c.forming)
    }

    @Test
    fun backspaceWithPendingSpaceMaterializesIt() {
        val c = composer()
        type(c, "ami")
        c.handle(ComposerKey.Space)
        assertTrue(c.pendingSpace)
        val actions = c.handle(ComposerKey.Backspace)
        assertEquals(
            listOf(ComposerAction.Commit(" "), ComposerAction.ForwardKey(ComposerKey.Backspace)),
            actions,
        )
        assertFalse(c.pendingSpace)
    }

    @Test
    fun enterCommitsFormingThenForwards() {
        val c = composer()
        type(c, "ami")
        val actions = c.handle(ComposerKey.Enter)
        assertEquals("আমি", commits(actions))
        assertEquals(ComposerAction.ForwardKey(ComposerKey.Enter), actions.last())
        assertFalse(c.forming)
        assertFalse(c.pendingSpace)
    }

    @Test
    fun escapeCancelsToRaw() {
        val c = composer()
        type(c, "ami")
        val actions = c.handle(ComposerKey.Escape)
        // The raw roman is committed verbatim — not the converted বাংলা.
        assertEquals("ami", commits(actions))
        assertFalse(c.forming)
    }

    @Test
    fun focusLostFlushesForming() {
        val c = composer()
        type(c, "ami")
        assertEquals("আমি", commits(c.focusLost()))
        assertFalse(c.forming)

        // pendingSpace path: the held space must be dropped silently — no
        // trailing space is injected into the host app on focus loss.
        val c2 = composer()
        type(c2, "ami")
        c2.handle(ComposerKey.Space)
        assertTrue(c2.pendingSpace)
        assertTrue(c2.focusLost().isEmpty())
        assertFalse(c2.pendingSpace)
    }

    @Test
    fun pickTeachesOnlyNonPrimary() {
        val c = composer()
        var picked: Triple<String, String, Boolean>? = null
        c.onPick = { raw, bangla, wasPrimary -> picked = Triple(raw, bangla, wasPrimary) }

        type(c, "kmn")
        settle(c)
        val primaryActions = c.pick(0)
        assertEquals(Triple("kmn", "কেমন", true), picked)
        assertEquals("কেমন", commits(primaryActions))

        type(c, "kmn")
        settle(c)
        val altActions = c.pick(1)
        assertEquals(Triple("kmn", "কেম", false), picked)
        assertEquals("কেম", commits(altActions))
    }

    // MARK: - the cost split (the performance fix)

    /** Counts what the typing path actually asks the engine for. */
    private class CountingEngine : ComposerEngine {
        var instants = 0
        var converts = 0
        var suggests = 0
        override fun instant(raw: String): String {
            instants++
            return SmartEngineAdapter.convertForInstantPreview(raw)
        }

        override fun convert(raw: String): String {
            converts++
            return SmartEngineAdapter.convertWord(raw).bengali
        }

        override fun suggest(raw: String, limit: Int): List<String> {
            suggests++
            return SmartEngineAdapter.getSuggestions(raw, limit).map { it.bengali }
        }
    }

    @Test
    fun typingNeverAsksTheEngineForSuggestions() {
        TestEngine.boot()
        val engine = CountingEngine()
        val c = Composer(engine)
        type(c, "bangla")
        c.handle(ComposerKey.Backspace)
        // THE performance pin. `getSuggestions` measures ~7.5 ms per keystroke
        // against the real dictionary — 370x the conversion — and running it
        // per letter is what the user felt as lag. Conversion stays here at
        // ~17 us because seeing the true Bangla immediately beats watching a
        // rule-only guess get rewritten later.
        assertEquals(0, engine.suggests, "suggest() must never run on the typing path")
        assertEquals(7, engine.converts, "one conversion per keystroke, backspace included")
    }

    @Test
    fun candidatesArriveOnlyWithTheRefine() {
        val c = composer()
        assertTrue(candidates(type(c, "kmn")).isEmpty(), "the strip is empty while typing")
        val list = candidates(settle(c))
        assertEquals("কেমন", list.first())
        assertEquals("kmn", list.last())
        // The refine reports candidates and NOTHING else: the word on screen
        // was already the full-pipeline answer, so nothing moves under the user
        // when the strip appears.
        assertTrue(settle(c).none { it is ComposerAction.Preview })
    }

    @Test
    fun aRefineForAnOlderBufferIsDiscarded() {
        val c = composer()
        type(c, "km")
        val stale = c.generation
        type(c, "n") // the user typed on before the debounce fired
        assertTrue(c.refineCandidates(stale).isEmpty(), "a stale refine must produce nothing")
        // …and it did not poison the current buffer either.
        assertEquals("কেমন", candidates(settle(c)).first())
        // A commit also ends the generation: a refine that lands afterwards is
        // for a word that no longer exists.
        val live = c.generation
        c.handle(ComposerKey.Space)
        assertTrue(c.refineCandidates(live).isEmpty())
    }

    @Test
    fun aFailingFullPipelineDegradesToTheRuleLayer() {
        TestEngine.boot()
        val faults = mutableListOf<Throwable>()
        val engine = object : ComposerEngine {
            override fun instant(raw: String) = SmartEngineAdapter.convertForInstantPreview(raw)
            override fun convert(raw: String): String = error("store is gone")
            override fun suggest(raw: String, limit: Int) = emptyList<String>()
        }
        val c = Composer(engine)
        c.onConversionFault = { faults += it }
        // The word still appears — the rule layer needs no dictionary at all —
        // and the failure is reported rather than swallowed.
        assertEquals("আমি", previews(type(c, "ami")).last().bangla)
        assertEquals("আমি", commits(c.handle(ComposerKey.Space)))
        assertEquals(3, faults.size)
    }
}
