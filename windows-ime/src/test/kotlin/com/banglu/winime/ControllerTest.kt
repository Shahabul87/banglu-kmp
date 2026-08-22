package com.banglu.winime

import com.banglu.engine.SmartEngineAdapter
import com.banglu.winime.composer.ComposerEngine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One ordered log of everything that reached the OS injection layer. */
private sealed interface Emitted {
    data class Text(val text: String) : Emitted
    data class Back(val count: Int) : Emitted
    data class Key(val key: RawKey) : Emitted
    data class VirtualKey(val vk: Int, val shift: Boolean) : Emitted
}

private class FakeInjector : TextInjector {
    private val calls = mutableListOf<Emitted>()
    override fun injectText(text: String) {
        synchronized(calls) { calls += Emitted.Text(text) }
    }

    override fun injectBackspaces(count: Int) {
        synchronized(calls) { calls += Emitted.Back(count) }
    }

    override fun injectKey(key: RawKey) {
        synchronized(calls) { calls += Emitted.Key(key) }
    }

    override fun injectVirtualKey(vk: Int, shift: Boolean) {
        synchronized(calls) { calls += Emitted.VirtualKey(vk, shift) }
    }

    val emitted: List<Emitted> get() = synchronized(calls) { calls.toList() }
    val texts: List<String> get() = emitted.filterIsInstance<Emitted.Text>().map { it.text }
    val keys: List<RawKey> get() = emitted.filterIsInstance<Emitted.Key>().map { it.key }

    /**
     * What the focused application would be holding, given everything we
     * injected in order. The space/দাঁড়ি model both writes and un-writes
     * committed text, so the emission list alone no longer says what the user
     * sees — this does.
     */
    val document: String
        get() = buildString {
            for (call in emitted) when (call) {
                is Emitted.Text -> append(call.text)
                is Emitted.Back -> repeat(minOf(call.count, length)) { deleteCharAt(length - 1) }
                else -> Unit
            }
        }

    val backspaces: List<Int> get() = emitted.filterIsInstance<Emitted.Back>().map { it.count }
}

private class FakeListener : ControllerListener {
    private val candidateCalls = mutableListOf<List<String>>()
    private val modeCalls = mutableListOf<Mode>()
    override fun onCandidates(candidates: List<String>) {
        synchronized(candidateCalls) { candidateCalls += candidates }
    }

    override fun onModeChanged(mode: Mode) {
        synchronized(modeCalls) { modeCalls += mode }
    }

    val candidates: List<List<String>> get() = synchronized(candidateCalls) { candidateCalls.toList() }
    val modes: List<Mode> get() = synchronized(modeCalls) { modeCalls.toList() }
}

/**
 * The debounce, under the test's control. Nothing here sleeps: [fire] runs
 * whatever the controller last armed, which is exactly what the real timer
 * thread does once the user stops typing.
 */
private class ManualScheduler : RefineScheduler {
    @Volatile
    private var pending: (() -> Unit)? = null

    @Volatile
    var scheduled = 0
        private set

    @Volatile
    var closed = false
        private set

    override fun schedule(task: () -> Unit) {
        scheduled++
        pending = task
    }

    override fun cancel() {
        pending = null
    }

    override fun close() {
        closed = true
        pending = null
    }

    /**
     * Takes the currently armed task so a test can fire it LATE — after the
     * user has typed on. That race is the reason the generation guard exists.
     */
    fun armed(): () -> Unit {
        val task = pending ?: error("nothing was armed")
        pending = null
        return task
    }

    /** true when there was something armed to run. */
    fun fire(): Boolean {
        val task = pending ?: return false
        pending = null
        task()
        return true
    }
}

/** The real engine on the real dictionary; `selected` is recorded, never taught. */
private open class RecordingEngine : ComposerEngine {
    private val learned = mutableListOf<Pair<String, String>>()

    /** Counts what the typing path asked for; the performance pin reads these. */
    val instants = java.util.concurrent.atomic.AtomicInteger(0)
    val converts = java.util.concurrent.atomic.AtomicInteger(0)
    val suggests = java.util.concurrent.atomic.AtomicInteger(0)

    override fun instant(raw: String): String {
        instants.incrementAndGet()
        return SmartEngineAdapter.convertForInstantPreview(raw)
    }

    override fun convert(raw: String): String {
        converts.incrementAndGet()
        return SmartEngineAdapter.convertWord(raw).bengali
    }

    override fun suggest(raw: String, limit: Int): List<String> {
        suggests.incrementAndGet()
        return SmartEngineAdapter.getSuggestions(raw, limit).map { it.bengali }
    }

    override fun selected(raw: String, bangla: String) {
        synchronized(learned) { learned += raw to bangla }
    }

    val taught: List<Pair<String, String>> get() = synchronized(learned) { learned.toList() }
}

/**
 * Parks the worker inside `convert()` of one exact raw buffer, so a test can
 * assert what the hook thread decides while a swallowed key is still queued.
 */
private class GateEngine(private val gateOn: String) : RecordingEngine() {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    override fun convert(raw: String): String {
        if (raw == gateOn) {
            entered.countDown()
            release.await(20, TimeUnit.SECONDS)
        }
        return super.convert(raw)
    }
}

/**
 * Blows up on demand, to prove the worker recovers. It fails the RULE layer as
 * well as the pipeline on purpose: a pipeline-only fault is survivable (the
 * composer falls back to rules and the word still appears), so only a total
 * engine failure reaches the controller's reset path at all.
 */
private class FlakyEngine : RecordingEngine() {
    @Volatile
    var failing = false

    override fun instant(raw: String): String {
        if (failing) error("engine blew up converting '$raw'")
        return super.instant(raw)
    }

    override fun convert(raw: String): String {
        if (failing) error("engine blew up converting '$raw'")
        return super.convert(raw)
    }
}

private class Rig(val engine: RecordingEngine) {
    val injector = FakeInjector()
    val listener = FakeListener()
    val scheduler = ManualScheduler()
    val compat = AppCompat(createTempDirectory("winime-controller").toFile())
    val controller = Controller(engine, injector, compat, listener, scheduler)
        .apply { engineReady = true }

    fun key(k: RawKey, exe: String = "notepad.exe"): Boolean = controller.onKey(k, exe)

    fun type(s: String) = s.forEach {
        assertTrue(key(RawKey.Letter(it)), "letter '$it' must be swallowed in BANGLA mode")
    }

    fun idle() = controller.awaitIdle()

    /** The user pauses: the debounce fires and the strip fills. */
    fun settle() {
        idle()
        scheduler.fire()
        idle()
    }
}

/**
 * The controller wall: swallow rules, the optimistic composer mirror, and the
 * single-worker ordering law (committed Bangla always reaches the app before a
 * key we forward on the same keystroke). Real Composer, real AppCompat, real
 * engine; only the not-yet-written OS layer is faked.
 */
class ControllerTest {
    private val rigs = mutableListOf<Rig>()

    private fun rig(engine: RecordingEngine = RecordingEngine()): Rig {
        TestEngine.boot()
        return Rig(engine).also { rigs += it }
    }

    @AfterTest
    fun stopWorkers() {
        rigs.forEach { it.controller.shutdown() }
        rigs.clear()
    }

    @Test
    fun banglaTypingEndToEnd() {
        val r = rig()
        r.type("ami")
        // Live echo: each letter reached the application as it was typed, and
        // each conversion simply extended the last, so nothing was deleted.
        r.idle()
        assertEquals(listOf(Emitted.Text("আ"), Emitted.Text("ম"), Emitted.Text("ি")), r.injector.emitted)

        assertTrue(r.key(RawKey.Space))
        r.idle()
        // THE defect this round fixes. The space appears the instant it is
        // pressed. The word itself needed no injection — it has been on screen
        // since the user typed it — so the last thing emitted is the space.
        assertEquals(
            listOf(
                Emitted.Text("আ"),
                Emitted.Text("ম"),
                Emitted.Text("ি"),
                Emitted.Text(" "),
            ),
            r.injector.emitted,
        )
        assertEquals("আমি ", r.injector.document)
        assertEquals("", r.controller.echoedText, "the word belongs to the document now")

        assertTrue(r.key(RawKey.Space))
        r.idle()
        // …and the second space converts the pair in place: exactly one
        // backspace takes the space away, then the দাঁড়ি lands.
        assertEquals(
            listOf(Emitted.Back(1), Emitted.Text("। ")),
            r.injector.emitted.drop(4),
        )
        assertEquals("আমি। ", r.injector.document)
        assertEquals(listOf(1), r.injector.backspaces, "exactly one backspace, for the space")
        assertTrue(r.injector.keys.isEmpty())

        assertTrue(r.key(RawKey.Space))
        r.idle()
        // A third space is a plain space — one দাঁড়ি per sentence break.
        assertEquals("আমি।  ", r.injector.document)
        assertEquals(listOf(1), r.injector.backspaces)
    }

    @Test
    fun tightPunctuationTakesBackTheSpaceAlreadyTyped() {
        val r = rig()
        r.type("ami")
        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertEquals("আমি ", r.injector.document)

        assertTrue(r.key(RawKey.Punct(",")))
        r.idle()
        assertEquals("আমি,", r.injector.document)
        assertEquals(listOf(1), r.injector.backspaces)
    }

    @Test
    fun enterAfterASpaceLeavesTheSpaceAlone() {
        val r = rig()
        r.type("ami")
        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertTrue(r.key(RawKey.Enter))
        r.idle()
        assertEquals("আমি ", r.injector.document, "Enter must not rewrite a space retroactively")
        assertTrue(r.injector.backspaces.isEmpty())
        assertEquals(listOf(RawKey.Enter), r.injector.keys)
        assertEquals(Emitted.Key(RawKey.Enter), r.injector.emitted.last(), "the app still gets it")
    }

    @Test
    fun backspaceAfterASpaceIsForwardedToTheHost() {
        val r = rig()
        r.type("ami")
        assertTrue(r.key(RawKey.Space))
        r.idle()
        val afterSpace = r.injector.emitted
        assertEquals("আমি ", r.injector.document, "the space is already real")

        // Claimed (the retro-space keeps the composer active) but not acted on:
        // the space is ordinary document text now, so the host deletes it. The
        // old model had to materialise the held space here first; this one has
        // nothing to do but forward the key.
        assertTrue(r.key(RawKey.Backspace))
        r.idle()
        assertEquals(listOf(Emitted.Key(RawKey.Backspace)), r.injector.emitted.drop(afterSpace.size))
        assertTrue(r.injector.backspaces.isEmpty(), "we never delete it ourselves")
        // …and composition really ended: the next backspace is the app's.
        assertFalse(r.key(RawKey.Backspace))
    }

    @Test
    fun swallowDecisions() {
        val r = rig()
        assertTrue(r.key(RawKey.Letter('a')))
        assertTrue(r.key(RawKey.Space))
        assertTrue(r.key(RawKey.Digit('5')))
        assertTrue(r.key(RawKey.Punct(",")))
        r.idle()
        // With the composer idle, everything the hook could not classify stays
        // the app's. (While a word is forming it is claimed instead, so it
        // cannot overtake the Bangla — pinned separately below.)
        assertFalse(r.key(RawKey.Unmanaged(0x11)))
        assertFalse(r.key(RawKey.Unmanaged(0x25)))

        r.controller.setModeExternal(Mode.ENGLISH)
        assertFalse(r.key(RawKey.Letter('a')))
        assertFalse(r.key(RawKey.Space))
        assertFalse(r.key(RawKey.Digit('5')))
        assertFalse(r.key(RawKey.Punct(",")))
        // English still claims the mode hotkey — otherwise there is no way back.
        assertTrue(r.key(RawKey.ToggleHotkey))
    }

    @Test
    fun enterSwallowedOnlyWhileComposerActive() {
        val r = rig()
        assertFalse(r.key(RawKey.Enter), "idle Enter belongs to the app")
        assertFalse(r.key(RawKey.Tab))
        assertFalse(r.key(RawKey.Backspace))
        assertFalse(r.key(RawKey.Escape))

        r.type("ami")
        assertTrue(r.key(RawKey.Enter))
        r.idle()
        assertEquals(listOf("আ", "ম", "ি"), r.injector.texts)
        assertEquals(listOf(RawKey.Enter), r.injector.keys)
        // Ordering law: the whole word reaches the app BEFORE the forwarded Enter.
        assertEquals(
            listOf(
                Emitted.Text("আ"),
                Emitted.Text("ম"),
                Emitted.Text("ি"),
                Emitted.Key(RawKey.Enter),
            ),
            r.injector.emitted,
        )

        // Composer is idle again, so the next Enter is the app's once more.
        assertFalse(r.key(RawKey.Enter))
    }

    @Test
    fun fastLetterThenEnterNeverRaces() {
        val r = rig()
        // The optimistic mirror: onKey(Letter) claims the composer ON THE HOOK
        // THREAD before enqueueing, so an Enter arriving before the worker has
        // processed the letter is still swallowed and ordered behind it.
        assertTrue(r.key(RawKey.Letter('a')))
        assertTrue(r.key(RawKey.Enter))
        r.idle()
        assertTrue(r.injector.texts.isNotEmpty(), "'a' must have been converted and committed")
        assertEquals(listOf(RawKey.Enter), r.injector.keys)
        assertTrue(r.injector.emitted.first() is Emitted.Text)
        assertEquals(Emitted.Key(RawKey.Enter), r.injector.emitted.last())
    }

    @Test
    fun enterIsSwallowedWhileAQueuedKeyHasNotBeenSeenYet() {
        val gate = GateEngine(gateOn = "k")
        val r = rig(gate)
        r.type("ami")
        assertTrue(r.key(RawKey.Space)) // commits আমি and a visible space
        assertTrue(r.key(RawKey.Space)) // rewrites it as "। " — the composer is now IDLE
        assertTrue(r.key(RawKey.Letter('k')))
        assertTrue(gate.entered.await(30, TimeUnit.SECONDS), "worker never reached the gate")
        try {
            // The composer's own state says idle and the worker is parked mid-key:
            // only an in-flight claim can keep this Enter from overtaking 'k'.
            assertTrue(r.key(RawKey.Enter), "Enter must never overtake a queued letter")
        } finally {
            gate.release.countDown()
        }
        r.idle()
        assertEquals(
            listOf(
                Emitted.Text("আ"),
                Emitted.Text("ম"),
                Emitted.Text("ি"),
                Emitted.Text(" "),
                Emitted.Back(1),
                Emitted.Text("। "),
                Emitted.Text("ক"),
                Emitted.Key(RawKey.Enter),
            ),
            r.injector.emitted,
        )
    }

    @Test
    fun enterIsStillSwallowedAfterEarlierForegroundChanges() {
        val gate = GateEngine(gateOn = "k")
        val r = rig(gate)
        // EVENT_SYSTEM_FOREGROUND fires on EVERY system-wide foreground
        // change — alt-tab, clicking another window, a notification stealing
        // focus — so within a minute of ordinary use a real session has seen
        // dozens. They are enqueued WITHOUT a claim; releasing one anyway
        // drove the counter permanently negative, `inFlight.get() > 0` could
        // never be true again, and the mirror collapsed to composerBusy alone.
        repeat(5) { assertFalse(r.key(RawKey.FocusChanged)) }
        r.idle()

        // From here this is exactly the race pinned above, on a rig that has
        // simply been used first.
        r.type("ami")
        assertTrue(r.key(RawKey.Space)) // commits আমি and a visible space
        assertTrue(r.key(RawKey.Space)) // rewrites it as "। " — the composer is now IDLE
        assertTrue(r.key(RawKey.Letter('k')))
        assertTrue(gate.entered.await(30, TimeUnit.SECONDS), "worker never reached the gate")
        try {
            assertTrue(r.key(RawKey.Enter), "Enter must never overtake a queued letter")
        } finally {
            gate.release.countDown()
        }
        r.idle()
        assertEquals(
            listOf(
                Emitted.Text("আ"),
                Emitted.Text("ম"),
                Emitted.Text("ি"),
                Emitted.Text(" "),
                Emitted.Back(1),
                Emitted.Text("। "),
                Emitted.Text("ক"),
                Emitted.Key(RawKey.Enter),
            ),
            r.injector.emitted,
        )
    }

    @Test
    fun everyClaimIsReleasedExactlyOnce() {
        val r = rig()
        // One line that catches the whole class of bug: after ANY mixture of
        // claimed keys and unclaimed signals, the counter must be back at zero.
        // Anything else means the mirror is either stuck on (harmless but
        // wrong) or sinking below zero (the bug that disables it forever).
        r.type("kmn")
        assertFalse(r.key(RawKey.FocusChanged))
        r.type("ami")
        assertTrue(r.key(RawKey.Space))
        assertFalse(r.key(RawKey.FocusChanged))
        r.type("kmn")
        r.settle()
        r.controller.pickCandidate(1)
        assertTrue(r.key(RawKey.ToggleHotkey)) // → English
        assertTrue(r.key(RawKey.ToggleHotkey)) // → back to বাংলা
        r.idle()
        r.type("ami")
        // Deliberately unasserted: whether this Enter is swallowed is pinned
        // by the tests above. Here it is only one more claimed key in the mix,
        // and the counter — not the swallow decision — is what is on trial.
        r.key(RawKey.Enter)
        assertFalse(r.key(RawKey.FocusChanged))
        r.idle()
        assertEquals(0, r.controller.inFlightCount, "every claim must be released exactly once")
    }

    @Test
    fun toggleHotkeyCyclesBanglaEnglish() {
        val r = rig()
        r.type("ami")
        assertTrue(r.key(RawKey.ToggleHotkey))
        r.idle()
        assertEquals(Mode.ENGLISH, r.controller.mode)
        assertEquals(listOf(Mode.ENGLISH), r.listener.modes)
        // Leaving BANGLA ends the word instead of stranding it — and, because
        // it is already on screen, without injecting or deleting anything.
        assertEquals(listOf("আ", "ম", "ি"), r.injector.texts)
        assertEquals("", r.controller.echoedText)
        assertEquals(emptyList(), r.listener.candidates.last())

        assertFalse(r.key(RawKey.Letter('a')), "English mode is pure passthrough")

        assertTrue(r.key(RawKey.ToggleHotkey))
        r.idle()
        assertEquals(Mode.BANGLA, r.controller.mode)
        assertEquals(listOf(Mode.ENGLISH, Mode.BANGLA), r.listener.modes)
        assertTrue(r.key(RawKey.Letter('a')))
    }

    @Test
    fun passthroughAppIsNeverTouched() {
        val r = rig()
        assertFalse(r.key(RawKey.Letter('a'), exe = "keepass.exe"))
        assertFalse(r.key(RawKey.Space, exe = "KeePass.exe"))
        assertFalse(r.key(RawKey.ToggleHotkey, exe = "keepass.exe"))

        r.compat.add("mybank.exe")
        assertFalse(r.key(RawKey.Letter('a'), exe = "mybank.exe"))
        assertTrue(r.key(RawKey.Letter('a'), exe = "notepad.exe"))

        r.idle()
        // Nothing from a passthrough app ever reached the composer: exactly one
        // letter — the notepad.exe one — was ever converted and echoed.
        assertEquals(listOf(Emitted.Text("আ")), r.injector.emitted)
        // …and typing never reports a candidate list at all: the strip is only
        // ever told about a list the debounce actually produced.
        assertTrue(r.listener.candidates.isEmpty())
    }

    @Test
    fun focusChangeSealsTheEchoWithoutTouchingAnyText() {
        val r = rig()
        r.type("ami")
        r.idle()
        val beforeFocusChange = r.injector.emitted
        assertFalse(r.key(RawKey.FocusChanged), "a focus notification is never swallowed")
        r.idle()
        // THE primary invariant of the live echo. The word is already in the
        // window the user typed it in; the caret is now somewhere else
        // entirely, so a backspace here would delete SOMEBODY ELSE'S text and
        // a re-injection would type the word into the wrong document.
        assertEquals(beforeFocusChange, r.injector.emitted, "focus change must inject nothing at all")
        assertEquals("", r.controller.echoedText)
        assertEquals(emptyList(), r.listener.candidates.last())
        // Composition really ended: the next Enter is the app's again.
        assertFalse(r.key(RawKey.Enter))
    }

    @Test
    fun candidatePickInjectsAndLearns() {
        val r = rig()
        r.type("kmn")
        r.settle()
        val strip = r.listener.candidates.last()
        assertEquals("কেমন", strip.first())
        assertTrue(strip.size > 1, "the UI needs candidates to click")
        // The echo trace for a word whose conversion REWRITES earlier letters:
        // ক, then কিমি (a pure extension), then কেমন — which shares only "ক",
        // so three characters come off and "েমন" goes on.
        assertEquals(
            listOf(
                Emitted.Text("ক"),
                Emitted.Text("িমি"),
                Emitted.Back(3),
                Emitted.Text("েমন"),
            ),
            r.injector.emitted,
        )

        r.controller.pickCandidate(1)
        r.idle()
        // কেমন → "কেম " in place: one backspace takes the ন off, and the space
        // that ends the word goes on. A pick ends a word the way space does.
        assertEquals(listOf(Emitted.Back(1), Emitted.Text(" ")), r.injector.emitted.drop(4))
        assertEquals("কেম ", r.injector.document)
        // Non-primary choice: the engine learns it (S26 law, explicit choice).
        assertEquals(listOf("kmn" to "কেম"), r.engine.taught)

        val afterFirstPick = r.injector.emitted.size
        r.type("kmn")
        r.settle()
        r.controller.pickCandidate(0)
        r.idle()
        // The echo again, then a primary pick that injects only its trailing
        // space: the word itself is already on screen.
        assertEquals(
            listOf(
                Emitted.Text("ক"),
                Emitted.Text("িমি"),
                Emitted.Back(3),
                Emitted.Text("েমন"),
                Emitted.Text(" "),
            ),
            r.injector.emitted.drop(afterFirstPick),
        )
        // Committing the engine's own primary must NEVER be learned.
        assertEquals(listOf("kmn" to "কেম"), r.engine.taught)
    }

    @Test
    fun offModeUnregistersEverything() {
        val r = rig()
        r.controller.setModeExternal(Mode.OFF)
        assertFalse(r.key(RawKey.Letter('a')))
        assertFalse(r.key(RawKey.Space))
        assertFalse(r.key(RawKey.Digit('5')))
        assertFalse(r.key(RawKey.Punct(".")))
        assertFalse(r.key(RawKey.Enter))
        assertFalse(r.key(RawKey.ToggleHotkey), "OFF means off — even our own hotkey")
        r.idle()
        assertTrue(r.injector.emitted.isEmpty())
        assertEquals(listOf(Mode.OFF), r.listener.modes)
    }

    @Test
    fun keysPassThroughUntilTheEngineIsReady() {
        val r = rig()
        r.controller.engineReady = false
        assertFalse(r.key(RawKey.Letter('a')), "never half-convert during boot (spec §4.8)")
        assertFalse(r.key(RawKey.Space))
        assertFalse(r.key(RawKey.ToggleHotkey))
        r.idle()
        assertTrue(r.injector.emitted.isEmpty())
        assertTrue(r.listener.candidates.isEmpty())

        r.controller.engineReady = true
        assertTrue(r.key(RawKey.Letter('a')))
    }

    @Test
    fun modeSwitchWhileFormingFlushesBeforeThePassthroughStarts() {
        val r = rig()
        r.type("ami")
        r.controller.setModeExternal(Mode.ENGLISH)
        // The mode is visible to the hook thread immediately — no worker wait.
        assertEquals(Mode.ENGLISH, r.controller.mode)
        assertFalse(r.key(RawKey.Letter('k')))
        r.idle()
        // Only the live echo — the switch itself neither retyped nor deleted.
        assertEquals(listOf("আ", "ম", "ি"), r.injector.texts)
        assertEquals("", r.controller.echoedText)
        assertEquals(listOf(Mode.ENGLISH), r.listener.modes)
    }

    @Test
    fun theStripIsToldOnlyWhenItsContentChanges() {
        val r = rig()
        r.type("ami")
        r.idle()
        // Typing does not query suggestions AND does not blank the strip, so
        // there is nothing to tell the UI: no call, no hide, no flicker.
        assertTrue(r.listener.candidates.isEmpty(), "a keystroke must not churn the popup")
        assertEquals("আমি", r.controller.echoedText)

        r.settle()
        assertEquals(1, r.listener.candidates.size)
        assertTrue(r.listener.candidates.last().isNotEmpty(), "the strip fills after the debounce")

        assertTrue(r.key(RawKey.Space))
        r.idle()
        // The word is finished: the popup has nothing left to describe.
        assertEquals(2, r.listener.candidates.size)
        assertEquals(emptyList(), r.listener.candidates.last())
    }

    /**
     * The second reported defect ("suggestion is gone"), end to end. The strip
     * must be populated for the whole time a word is being typed — not only
     * during a pause longer than the gap between two letters.
     */
    @Test
    fun theStripStaysPopulatedWhileTheWordIsTyped() {
        val r = rig()
        r.type("k")
        r.settle()
        assertTrue(r.listener.candidates.last().isNotEmpty())

        r.type("m")
        r.idle()
        assertTrue(
            r.listener.candidates.last().isNotEmpty(),
            "the strip must not blank out between keystrokes",
        )
        r.settle()
        r.type("n")
        r.idle()
        assertTrue(r.listener.candidates.last().isNotEmpty())
        r.settle()
        assertEquals("কেমন", r.listener.candidates.last().first())

        // …and it disappears the moment the word is committed.
        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertEquals(emptyList(), r.listener.candidates.last())
    }

    @Test
    fun backspaceEditsTheEchoedWordThenReturnsToTheApp() {
        val r = rig()
        r.type("amii") // আ, আম, আমি, আমিই — four pure extensions
        r.idle()
        assertEquals("আমিই", r.controller.echoedText)
        assertTrue(r.key(RawKey.Backspace))
        r.idle()
        // Shrinking the buffer goes down the SAME diff as growing it: the new
        // conversion shares "আমি", so exactly one character comes off.
        assertEquals(Emitted.Back(1), r.injector.emitted.last())
        assertEquals("আমি", r.controller.echoedText)

        repeat(3) { assertTrue(r.key(RawKey.Backspace)) }
        r.idle()
        // …down to nothing: the word the user un-typed is gone from their
        // document, one character at a time, and never more than we put there.
        assertEquals(
            listOf(Emitted.Back(1), Emitted.Back(1), Emitted.Back(1), Emitted.Back(1)),
            r.injector.emitted.drop(4),
        )
        assertEquals("", r.controller.echoedText)
        assertEquals(emptyList(), r.listener.candidates.last())
        // Buffer empty again: the host owns backspace.
        assertFalse(r.key(RawKey.Backspace))
    }

    @Test
    fun escapeAfterASpaceReachesTheAppAndDisarmsTheRetroDari() {
        val r = rig()
        r.type("ami")
        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertEquals("আমি ", r.injector.document)

        // The armed retro-space keeps the composer active, so Escape is ours to
        // order — but the app MUST still get it: Escape closes dialogs, and a
        // word-then-space is the state a user spends most of their time in.
        assertTrue(r.key(RawKey.Escape))
        r.idle()
        assertEquals(listOf(Emitted.Key(RawKey.Escape)), r.injector.emitted.drop(4))

        // Escape can have dismissed anything — a dialog, an autocomplete list —
        // so the caret is no longer reliably ours and the retro-দাঁড়ি is
        // disarmed. The composer is idle, so the NEXT Escape is the app's
        // outright: it still reaches the application, we simply stop touching it.
        assertFalse(r.key(RawKey.Escape))
        assertEquals(listOf(RawKey.Escape), r.injector.keys)
        // …and a space now writes a plain space, not a retroactive দাঁড়ি.
        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertEquals("আমি  ", r.injector.document)
        assertTrue(r.injector.backspaces.isEmpty(), "a disarmed retro-space never deletes")

        // While a word IS forming, Escape cancels to the raw roman instead —
        // nothing is forwarded to the app. The Bangla is already on screen, so
        // "cancel" means un-typing it and typing the roman over the top.
        val beforeKmn = r.injector.emitted.size
        r.type("kmn")
        assertTrue(r.key(RawKey.Escape))
        r.idle()
        assertEquals(
            listOf(
                Emitted.Text("ক"),
                Emitted.Text("িমি"),
                Emitted.Back(3),
                Emitted.Text("েমন"),
                Emitted.Back(4), // কেমন shares nothing with "kmn"
                Emitted.Text("kmn"),
            ),
            r.injector.emitted.drop(beforeKmn),
        )
        assertEquals(listOf(RawKey.Escape), r.injector.keys)
    }

    @Test
    fun unmanagedKeyWhileFormingReachesTheAppBehindTheBanglaWord() {
        val r = rig()
        r.type("ami")
        // '(' is VK 0x39 with Shift held. Passed through, it would land in the
        // app NOW and আমি after it: the user types "আমি(" and reads "(আমি".
        assertTrue(
            r.key(RawKey.Unmanaged(0x39, shift = true)),
            "an unmanaged key must not overtake a forming word",
        )
        r.idle()
        // The word was echoed as it was typed and the unmanaged key lands
        // behind it. Nothing is injected for the word itself and — the safety
        // rule — nothing is deleted either: an arrow key may already have moved
        // the caret away from our text.
        assertEquals(
            listOf(
                Emitted.Text("আ"),
                Emitted.Text("ম"),
                Emitted.Text("ি"),
                Emitted.VirtualKey(0x39, true),
            ),
            r.injector.emitted,
        )
        assertEquals("", r.controller.echoedText)
        // The flush really ended composition: the next one is the app's again.
        assertFalse(r.key(RawKey.Unmanaged(0x39, shift = true)))
    }

    @Test
    fun unmanagedKeyPassesThroughWhenNothingIsForming() {
        val r = rig()
        assertFalse(r.key(RawKey.Unmanaged(0x25)), "an arrow key with an idle composer is the app's")
        assertFalse(r.key(RawKey.Unmanaged(0x39, shift = true)))
        r.idle()
        assertTrue(r.injector.emitted.isEmpty(), "a key we let through must never also be injected")
    }

    @Test
    fun unmanagedKeyAfterASpaceKeepsTheSpaceAndDisarmsTheRetroDari() {
        val r = rig()
        r.type("ami")
        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertEquals("আমি ", r.injector.document)

        // The armed retro-space keeps the composer active, so the key is ours
        // to order — and that is exactly why it must be: an arrow key moves the
        // caret, and a retro-দাঁড়ি backspace afterwards would land on the
        // user's own text. Routing it through the worker is what disarms it.
        assertTrue(r.key(RawKey.Unmanaged(0x39, shift = true)))
        r.idle()
        assertEquals(
            listOf(
                Emitted.Text("আ"),
                Emitted.Text("ম"),
                Emitted.Text("ি"),
                Emitted.Text(" "),
                Emitted.VirtualKey(0x39, true),
            ),
            r.injector.emitted,
        )
        // Disarmed: the next space is a plain space and deletes nothing.
        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertTrue(r.injector.backspaces.isEmpty())
    }

    @Test
    fun unmanagedKeyNeverOvertakesAQueuedLetter() {
        val r = rig()
        // Same optimistic-mirror race as Enter: the letter may still be queued
        // when the unmanaged key is classified on the hook thread.
        assertTrue(r.key(RawKey.Letter('a')))
        assertTrue(r.key(RawKey.Unmanaged(0x39, shift = true)))
        r.idle()
        assertTrue(r.injector.emitted.first() is Emitted.Text, "'a' must have been committed first")
        assertEquals(Emitted.VirtualKey(0x39, true), r.injector.emitted.last())
    }

    @Test
    fun aThrowingEngineDoesNotPoisonTheComposer() {
        val engine = FlakyEngine()
        val r = rig(engine)
        val errors = mutableListOf<Throwable>()
        r.controller.onError = { t -> synchronized(errors) { errors += t } }

        r.type("am")
        r.idle()
        assertEquals(listOf("আ", "ম"), r.injector.texts)

        engine.failing = true
        assertTrue(r.key(RawKey.Letter('k')))
        r.idle()
        // The state machine is reset instead of holding a buffer it can never
        // render — and the last good Bangla is not lost, because it is already
        // in the user's document. Nothing is injected and nothing is deleted.
        assertEquals(listOf("আ", "ম"), r.injector.texts)
        assertEquals("", r.controller.echoedText)
        assertEquals(emptyList(), r.listener.candidates.last())
        // The failure is surfaced, not swallowed in silence.
        assertEquals(1, synchronized(errors) { errors.size })
        assertTrue(r.controller.lastWorkerError is IllegalStateException)
        // Mirror is clean again: Enter belongs to the app once more.
        assertFalse(r.key(RawKey.Enter))

        engine.failing = false
        // The poisoning bug: without the reset, "ami" would append to the
        // stranded "amk" buffer and every letter would vanish.
        r.type("ami")
        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertEquals(listOf("আ", "ম", "আ", "ম", "ি", " "), r.injector.texts)
        assertEquals(1, synchronized(errors) { errors.size })
    }

    @Test
    fun banglaDigitsFlagControlsCommittedDigitScript() {
        val r = rig()
        r.controller.banglaDigits = true
        assertTrue(r.key(RawKey.Digit('5')))
        r.idle()
        assertEquals(listOf("৫"), r.injector.texts)

        r.controller.banglaDigits = false
        assertTrue(r.key(RawKey.Digit('5')))
        r.idle()
        assertEquals(listOf("৫", "5"), r.injector.texts)
    }

    @Test
    fun shutdownDrainsQueuedWork() {
        val r = rig()
        r.type("ami")
        assertTrue(r.key(RawKey.Space))
        r.controller.shutdown()
        assertEquals(listOf("আ", "ম", "ি", " "), r.injector.texts)
        // Quitting must never backspace: the user's word is theirs, and the
        // process is going away with no way to put anything back.
        assertEquals("", r.controller.echoedText)
        assertTrue(r.injector.emitted.none { it is Emitted.Back })
        assertTrue(r.scheduler.closed, "the debounce timer must not outlive the worker")
        // The quit path unhooks AFTER shutdown: a key swallowed now would be
        // eaten by a worker that no longer exists.
        assertFalse(r.key(RawKey.Letter('a')))
        assertFalse(r.key(RawKey.Space))
        assertEquals(listOf("আ", "ম", "ি", " "), r.injector.texts)
        r.controller.shutdown() // idempotent
    }

    // MARK: - the performance fix (Defect 1)

    @Test
    fun theTypingPathNeverQueriesSuggestions() {
        val r = rig()
        r.type("bangla")
        assertTrue(r.key(RawKey.Backspace))
        r.idle()
        // THE pin. `getSuggestions` costs ~7.5 ms per keystroke against the
        // real dictionary — 370x the ~17 us conversion — and running it per
        // letter is exactly what the user reported as lag. It now happens only
        // behind the debounce, so a burst of typing queries it zero times.
        assertEquals(0, r.engine.suggests.get(), "suggest() must never run on a keystroke")
        assertEquals(7, r.engine.converts.get(), "conversion stays synchronous — it is free")
        assertEquals("বাংলা", r.controller.echoedText, "the live echo is the FULL conversion")
    }

    @Test
    fun theCandidateStripArrivesAfterTheDebounce() {
        val r = rig()
        r.type("kmn")
        r.idle()
        assertTrue(r.listener.candidates.isEmpty(), "no query, and no strip churn, while typing")

        val echoedBeforeStrip = r.injector.emitted
        r.settle()
        assertEquals(1, r.engine.suggests.get(), "one query for the whole word, not one per letter")
        val strip = r.listener.candidates.last()
        assertEquals("কেমন", strip.first())
        assertEquals("kmn", strip.last(), "the raw roman escape hatch stays reachable")
        // The strip appearing must not disturb the text: the word on screen was
        // already the full-pipeline answer, so the refine injects nothing.
        assertEquals(echoedBeforeStrip, r.injector.emitted)
    }

    @Test
    fun aRefineForAnOlderBufferIsDiscarded() {
        val r = rig()
        r.type("km")
        r.idle()
        // Arm the debounce for "km", then type on before it fires — the timer
        // thread can always lose this race with a fast typist.
        val stale = r.scheduler.armed()
        r.type("n")
        r.idle()
        stale()
        r.idle()
        // The generation guard threw it away: no strip for the word the user
        // has already typed past — the UI is not told anything at all.
        assertTrue(r.listener.candidates.isEmpty())

        // …and the live one still works.
        r.settle()
        assertEquals("কেমন", r.listener.candidates.last().first())
    }

    @Test
    fun aFinishedWordDropsThePendingRefine() {
        val r = rig()
        r.type("ami")
        assertTrue(r.key(RawKey.Space))
        r.idle()
        // Nothing armed: there is no word to suggest alternatives for, so the
        // engine is never asked.
        assertFalse(r.scheduler.fire())
        assertEquals(0, r.engine.suggests.get())
    }

    // MARK: - the echo safety rule (Defect 2)

    @Test
    fun aModeChangeClearsTheEchoWithoutBackspaces() {
        val r = rig()
        r.type("ami")
        r.idle()
        val before = r.injector.emitted
        // The user reached the tray or the control window to do this, so our
        // caret is not necessarily the focused one any more.
        r.controller.setModeExternal(Mode.ENGLISH)
        r.idle()
        assertEquals(before, r.injector.emitted, "a mode change must inject nothing at all")
        assertEquals("", r.controller.echoedText)
    }

    @Test
    fun everyEchoEndingPathIsBackspaceFree() {
        // One place that names the complete list. Each of these ends a word
        // WITHOUT the guarantee that the caret is still where we left it, and
        // a backspace sent in any of them deletes text the user typed
        // themselves — the one failure mode of this design that destroys work.
        //
        // Run twice: once mid-word (only the echo ledger is at stake) and once
        // with a space already written, where the retro-দাঁড়ি is armed and
        // there is a second way to send a backspace. Every path must disarm it,
        // which is also why a space keeps the composer "active" in the mirror.
        for (trailingSpace in listOf(false, true)) {
            for (ending in listOf<(Rig) -> Unit>(
                { it.key(RawKey.FocusChanged) },
                { it.controller.setModeExternal(Mode.ENGLISH) },
                { it.key(RawKey.ToggleHotkey) },
                { it.key(RawKey.Unmanaged(0x25)) }, // left arrow: the caret moves
                { it.controller.shutdown() },
            )) {
                val r = rig()
                r.type("ami")
                if (trailingSpace) r.key(RawKey.Space)
                r.idle()
                val before = r.injector.emitted
                ending(r)
                if (!r.controller.isStopped) r.idle()
                assertEquals("", r.controller.echoedText, "the ledger must be cleared")
                assertTrue(
                    r.injector.emitted.drop(before.size).none { it is Emitted.Back },
                    "no path that loses the caret may send a backspace",
                )
                if (trailingSpace && !r.controller.isStopped) {
                    // …and it stays disarmed afterwards: the space that follows
                    // must be a plain space, never a delete-and-rewrite.
                    r.key(RawKey.Space)
                    r.idle()
                    assertTrue(
                        r.injector.emitted.drop(before.size).none { it is Emitted.Back },
                        "a path that lost the caret must not leave the retro-দাঁড়ি armed",
                    )
                }
            }
        }
    }

    @Test
    fun aCommitOfTextAlreadyOnScreenInjectsOnlyWhatIsNew() {
        val r = rig()
        r.type("ami")
        r.idle()
        val before = r.injector.emitted.size
        // Space, Enter and Tab all end the word. With the conversion computed
        // synchronously, the WORD is already on screen — so the only thing a
        // commit can inject is what the key itself adds: a space, or nothing.
        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertEquals(listOf(Emitted.Text(" ")), r.injector.emitted.drop(before))

        r.type("ami")
        r.idle()
        assertTrue(r.key(RawKey.Enter))
        r.idle()
        // Enter adds no text of its own: the word costs nothing to commit.
        assertEquals(listOf(Emitted.Key(RawKey.Enter)), r.injector.emitted.drop(before + 4))
    }
}
