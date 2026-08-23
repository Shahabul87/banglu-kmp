package com.banglu.winime.composer

import com.banglu.engine.SmartEngineAdapter
import com.banglu.winime.AdapterComposerEngine
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

    private fun composer(): Composer {
        TestEngine.boot()
        // The production seam (S130): the same context-aware object Main wires.
        return Composer(AdapterComposerEngine)
    }

    private fun commits(actions: List<ComposerAction>) =
        actions.filterIsInstance<ComposerAction.Commit>().joinToString("") { it.text }

    private fun previews(actions: List<ComposerAction>) =
        actions.filterIsInstance<ComposerAction.Preview>()

    private fun candidates(actions: List<ComposerAction>) =
        actions.filterIsInstance<ComposerAction.Candidates>().last().list

    /**
     * What the focused application would end up holding, applied in order —
     * the only honest way to assert a model that BOTH writes and un-writes
     * committed text.
     */
    private fun document(actions: List<ComposerAction>): String {
        val sb = StringBuilder()
        for (action in actions) when (action) {
            is ComposerAction.Commit -> sb.append(action.text)
            is ComposerAction.DeleteBack -> repeat(minOf(action.count, sb.length)) {
                sb.deleteCharAt(sb.length - 1)
            }
            else -> Unit
        }
        return sb.toString()
    }

    private fun deletions(actions: List<ComposerAction>) =
        actions.filterIsInstance<ComposerAction.DeleteBack>()

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
    fun spaceCommitsThePreviewAndAVisibleSpaceAtOnce() {
        val c = composer()
        val typed = type(c, "ami")
        val lastPreview = previews(typed).last()
        assertEquals("আমি", lastPreview.bangla)
        assertEquals("ami", lastPreview.raw)
        // WYSIWYG law: the commit must contain the last previewed Bangla
        // exactly — never a re-conversion of the raw buffer — and the space
        // the user pressed must be part of the SAME commit, so it reaches the
        // document on this keystroke rather than the next one.
        val actions = c.handle(ComposerKey.Space)
        assertEquals("${lastPreview.bangla} ", commits(actions))
        assertTrue(deletions(actions).isEmpty(), "the first space deletes nothing")
        assertTrue(c.retroSpaceArmed)
    }

    @Test
    fun doubleSpaceRewritesTheSpaceAsDari() {
        val c = composer()
        type(c, "ami")
        val first = c.handle(ComposerKey.Space)
        val second = c.handle(ComposerKey.Space)
        // The space is one keystroke old and still the last character, so it
        // comes back out and the দাঁড়ি takes its place — visibly, now.
        assertEquals(listOf(ComposerAction.DeleteBack(1)), deletions(second))
        assertEquals("আমি। ", document(first + second))
        assertFalse(c.retroSpaceArmed, "the দাঁড়ি is not itself retro-convertible")
    }

    @Test
    fun tripleSpaceAddsAPlainSpaceNotASecondDari() {
        val c = composer()
        type(c, "ami")
        val all = c.handle(ComposerKey.Space) +
            c.handle(ComposerKey.Space) +
            c.handle(ComposerKey.Space)
        assertEquals("আমি।  ", document(all))
        assertEquals(1, deletions(all).size, "one দাঁড়ি per sentence break, one deletion")
    }

    @Test
    fun letterAfterASpaceJustStartsTheNextWord() {
        val c = composer()
        type(c, "ami")
        val space = c.handle(ComposerKey.Space)
        assertTrue(c.retroSpaceArmed)
        val actions = c.handle(ComposerKey.Letter('k'))
        // Nothing to release: the space was written when it was pressed.
        assertEquals("", commits(actions))
        assertTrue(deletions(actions).isEmpty())
        assertFalse(c.retroSpaceArmed)
        assertTrue(c.forming)
        assertEquals("k", previews(actions).last().raw)
        assertEquals("আমি ", document(space + actions))
    }

    @Test
    fun tightPunctuationTakesBackTheSpaceAlreadyTyped() {
        val c = composer()
        type(c, "ami")
        val space = c.handle(ComposerKey.Space)
        val comma = c.handle(ComposerKey.Punctuation(","))
        assertEquals(listOf(ComposerAction.DeleteBack(1)), deletions(comma))
        assertEquals("আমি,", document(space + comma))
    }

    @Test
    fun periodMapsToDariAndIsTight() {
        val c = composer()
        type(c, "ami")
        val space = c.handle(ComposerKey.Space)
        // "." maps to দাঁড়ি first, THEN the tight-punctuation check runs on
        // the mapped character — the space in front of it is taken back.
        val dot = c.handle(ComposerKey.Punctuation("."))
        assertEquals("আমি।", document(space + dot))
        assertFalse(c.retroSpaceArmed)
    }

    @Test
    fun punctuationWithNoSpaceInFrontDeletesNothing() {
        val c = composer()
        type(c, "ami")
        val actions = c.handle(ComposerKey.Punctuation(","))
        assertEquals("আমি,", document(actions))
        assertTrue(deletions(actions).isEmpty(), "there is no space of ours to take back")
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
        // The pick ends the word exactly the way space does, space included.
        assertEquals("কেম ", commits(actions))
        assertEquals(Triple("kmn", "কেম", false), picked)
        assertTrue(c.retroSpaceArmed)
        assertFalse(c.forming)
    }

    @Test
    fun aPickFromAStaleStripIsCommittedButNeverLearned() {
        val c = composer()
        var picked: Triple<String, String, Boolean>? = null
        c.onPick = { raw, bangla, wasPrimary -> picked = Triple(raw, bangla, wasPrimary) }
        type(c, "km")
        val stripForKm = candidates(settle(c)) // the strip describes "km"
        assertTrue(stripForKm.size > 1)
        type(c, "n") // the user typed on; the strip has not caught up yet
        val choice = stripForKm[1]
        val actions = c.pick(1)
        // WYSIWYG for the strip: they clicked it, they get it…
        assertEquals("$choice ", commits(actions))
        // …but `kmn -> <a candidate ranked for km>` is a mapping the user never
        // made, and learned.json is forever.
        assertEquals(null, picked, "a stale strip must never teach")
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
        assertEquals(
            Composer.MAX_CANDIDATES,
            list.size,
            "a word with plenty of candidates must fill the window exactly",
        )
        assertEquals("kmn", list.last(), "the raw roman is always the last candidate")
        // …and the digit for that last position really commits it.
        val lastDigit = '0' + Composer.MAX_CANDIDATES
        assertEquals("kmn ", commits(c.handle(ComposerKey.Digit(lastDigit))))
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
    fun backspaceAfterASpaceIsTheHostsToHandle() {
        val c = composer()
        type(c, "ami")
        c.handle(ComposerKey.Space)
        assertTrue(c.retroSpaceArmed)
        val actions = c.handle(ComposerKey.Backspace)
        // The space is already ordinary document text — nothing to materialise
        // and nothing of ours to delete. The host's own backspace removes it.
        // (S130 pin update: the backspace also hides the prediction strip the
        // space put up, in the same batch.)
        assertEquals(ComposerAction.ForwardKey(ComposerKey.Backspace), actions.first())
        assertTrue(deletions(actions).isEmpty(), "nothing of ours to delete")
        assertEquals(emptyList(), candidates(actions))
        assertFalse(c.retroSpaceArmed)
    }

    @Test
    fun enterCommitsFormingThenForwards() {
        val c = composer()
        type(c, "ami")
        val actions = c.handle(ComposerKey.Enter)
        assertEquals("আমি", commits(actions))
        assertEquals(ComposerAction.ForwardKey(ComposerKey.Enter), actions.last())
        assertFalse(c.forming)
        assertFalse(c.retroSpaceArmed)
    }

    @Test
    fun enterAfterASpaceLeavesTheSpaceAlone() {
        val c = composer()
        type(c, "ami")
        val space = c.handle(ComposerKey.Space)
        val enter = c.handle(ComposerKey.Enter)
        // Pressing Enter says nothing about ending a sentence: no retroactive
        // দাঁড়ি, no deletion, and the key still reaches the application.
        assertTrue(deletions(enter).isEmpty())
        assertEquals("আমি ", document(space + enter))
        assertEquals(ComposerAction.ForwardKey(ComposerKey.Enter), enter.last())
        assertFalse(c.retroSpaceArmed)
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

        // After a space there is nothing left to flush — it is already in the
        // document — and focus loss must only DISARM the retro-দাঁড়ি, never
        // inject or delete anything at the caret it no longer owns.
        val c2 = composer()
        type(c2, "ami")
        c2.handle(ComposerKey.Space)
        assertTrue(c2.retroSpaceArmed)
        assertTrue(c2.focusLost().isEmpty())
        assertFalse(c2.retroSpaceArmed)
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
        assertEquals("কেমন ", commits(primaryActions))

        type(c, "kmn")
        settle(c)
        val altActions = c.pick(1)
        assertEquals(Triple("kmn", "কেম", false), picked)
        assertEquals("কেম ", commits(altActions))
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

        override fun convert(raw: String, prev1: String?, prev2: String?): String {
            converts++
            return SmartEngineAdapter.convertWord(raw).bengali
        }

        override fun suggest(raw: String, limit: Int, prev1: String?, prev2: String?): List<String> {
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
        assertTrue(
            type(c, "kmn").none { it is ComposerAction.Candidates },
            "a keystroke never reports a candidate list — it does not have one",
        )
        val list = candidates(settle(c))
        assertEquals("কেমন", list.first())
        assertEquals("kmn", list.last())
        // The refine reports candidates and NOTHING else: the word on screen
        // was already the full-pipeline answer, so nothing moves under the user
        // when the strip appears.
        assertTrue(settle(c).none { it is ComposerAction.Preview })
    }

    /**
     * The second reported defect, at its source. Blanking the strip on every
     * keystroke meant it could only be on screen during a pause LONGER than the
     * gap between two letters — so someone typing normally saw no suggestions
     * at all. A keystroke must leave the strip alone.
     */
    @Test
    fun aKeystrokeDoesNotBlankTheStrip() {
        val c = composer()
        type(c, "km")
        val forKm = candidates(settle(c))
        assertTrue(forKm.isNotEmpty())

        val next = type(c, "n")
        assertTrue(
            next.none { it is ComposerAction.Candidates },
            "the strip must survive the keystroke rather than being hidden and re-shown",
        )
        // …and the fresh list replaces it as soon as the debounce lands.
        assertEquals("কেমন", candidates(settle(c)).first())
    }

    /**
     * S130 pin update (documented decision): Space no longer leaves the strip
     * empty — it hands it to the NEXT-WORD PREDICTIONS, the same row Android
     * and banglu-web show after a commit. Enter still empties it (the key may
     * have sent the message or left the field), and so does un-typing the
     * buffer.
     */
    @Test
    fun finishingAWordHandsTheStripToPredictionsOrEmptiesIt() {
        val c = composer()
        type(c, "kmn")
        assertTrue(candidates(settle(c)).isNotEmpty())
        val afterSpace = c.handle(ComposerKey.Space)
            .filterIsInstance<ComposerAction.Candidates>().last()
        assertTrue(afterSpace.predictions, "after a space the strip is the prediction row")
        assertEquals(
            SmartEngineAdapter.getNextWordPredictions(null, "কেমন", 5).map { it.bengali },
            afterSpace.list,
        )

        c.focusLost() // reset the ledger so each leg starts clean
        type(c, "kmn")
        assertTrue(candidates(settle(c)).isNotEmpty())
        assertEquals(emptyList(), candidates(c.handle(ComposerKey.Enter)))

        // Un-typing the whole buffer empties it too.
        type(c, "kmn")
        assertTrue(candidates(settle(c)).isNotEmpty())
        repeat(2) { c.handle(ComposerKey.Backspace) }
        assertEquals(emptyList(), candidates(c.handle(ComposerKey.Backspace)))
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
            override fun convert(raw: String, prev1: String?, prev2: String?): String =
                error("store is gone")

            override fun suggest(raw: String, limit: Int, prev1: String?, prev2: String?) =
                emptyList<String>()
        }
        val c = Composer(engine)
        c.onConversionFault = { faults += it }
        // The word still appears — the rule layer needs no dictionary at all —
        // and the failure is reported rather than swallowed.
        assertEquals("আমি", previews(type(c, "ami")).last().bangla)
        assertEquals("আমি ", commits(c.handle(ComposerKey.Space)))
        assertEquals(3, faults.size)
    }
}
