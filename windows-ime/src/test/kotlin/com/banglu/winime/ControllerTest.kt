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
    data class Key(val key: RawKey) : Emitted
    data class VirtualKey(val vk: Int, val shift: Boolean) : Emitted
}

private class FakeInjector : TextInjector {
    private val calls = mutableListOf<Emitted>()
    override fun injectText(text: String) {
        synchronized(calls) { calls += Emitted.Text(text) }
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
}

private data class PreviewCall(val bangla: String, val raw: String, val candidates: List<String>)

private class FakeListener : ControllerListener {
    private val previewCalls = mutableListOf<PreviewCall>()
    private val modeCalls = mutableListOf<Mode>()
    override fun onPreview(bangla: String, raw: String, candidates: List<String>) {
        synchronized(previewCalls) { previewCalls += PreviewCall(bangla, raw, candidates) }
    }

    override fun onModeChanged(mode: Mode) {
        synchronized(modeCalls) { modeCalls += mode }
    }

    val previews: List<PreviewCall> get() = synchronized(previewCalls) { previewCalls.toList() }
    val modes: List<Mode> get() = synchronized(modeCalls) { modeCalls.toList() }
}

/** The real engine on the real dictionary; `selected` is recorded, never taught. */
private open class RecordingEngine : ComposerEngine {
    private val learned = mutableListOf<Pair<String, String>>()
    override fun convert(raw: String): String = SmartEngineAdapter.convertWord(raw).bengali
    override fun suggest(raw: String, limit: Int): List<String> =
        SmartEngineAdapter.getSuggestions(raw, limit).map { it.bengali }

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

/** Blows up inside `convert()` on demand, to prove the worker recovers. */
private class FlakyEngine : RecordingEngine() {
    @Volatile
    var failing = false

    override fun convert(raw: String): String {
        if (failing) error("engine blew up converting '$raw'")
        return super.convert(raw)
    }
}

private class Rig(val engine: RecordingEngine) {
    val injector = FakeInjector()
    val listener = FakeListener()
    val compat = AppCompat(createTempDirectory("winime-controller").toFile())
    val controller = Controller(engine, injector, compat, listener).apply { engineReady = true }

    fun key(k: RawKey, exe: String = "notepad.exe"): Boolean = controller.onKey(k, exe)

    fun type(s: String) = s.forEach {
        assertTrue(key(RawKey.Letter(it)), "letter '$it' must be swallowed in BANGLA mode")
    }

    fun idle() = controller.awaitIdle()
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
        assertTrue(r.key(RawKey.Space))
        r.idle()
        // The space is HELD (pending-space দাঁড়ি model): only the word landed.
        assertEquals(listOf("আমি"), r.injector.texts)

        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertEquals(listOf("আমি", "। "), r.injector.texts)
        assertTrue(r.injector.keys.isEmpty())
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
        assertEquals(listOf("আমি"), r.injector.texts)
        assertEquals(listOf(RawKey.Enter), r.injector.keys)
        // Ordering law: the commit reaches the app BEFORE the forwarded Enter.
        assertEquals(listOf(Emitted.Text("আমি"), Emitted.Key(RawKey.Enter)), r.injector.emitted)

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
        assertTrue(r.key(RawKey.Space)) // commits আমি, holds the space
        assertTrue(r.key(RawKey.Space)) // commits "। " — the composer is now IDLE
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
                Emitted.Text("আমি"),
                Emitted.Text("। "),
                Emitted.Text("ক"),
                Emitted.Key(RawKey.Enter),
            ),
            r.injector.emitted,
        )
    }

    @Test
    fun toggleHotkeyCyclesBanglaEnglish() {
        val r = rig()
        r.type("ami")
        assertTrue(r.key(RawKey.ToggleHotkey))
        r.idle()
        assertEquals(Mode.ENGLISH, r.controller.mode)
        assertEquals(listOf(Mode.ENGLISH), r.listener.modes)
        // Leaving BANGLA flushes the half-typed word instead of stranding it.
        assertEquals(listOf("আমি"), r.injector.texts)
        assertEquals(PreviewCall("", "", emptyList()), r.listener.previews.last())

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
        // Nothing from a passthrough app ever reached the composer.
        assertEquals(1, r.listener.previews.size)
        assertEquals("a", r.listener.previews.last().raw)
    }

    @Test
    fun focusChangeFlushesForming() {
        val r = rig()
        r.type("ami")
        assertFalse(r.key(RawKey.FocusChanged), "a focus notification is never swallowed")
        r.idle()
        assertEquals(listOf("আমি"), r.injector.texts)
        assertEquals(PreviewCall("", "", emptyList()), r.listener.previews.last())
        // Composition really ended: the next Enter is the app's again.
        assertFalse(r.key(RawKey.Enter))
    }

    @Test
    fun candidatePickInjectsAndLearns() {
        val r = rig()
        r.type("kmn")
        r.idle()
        val forming = r.listener.previews.last()
        assertEquals("কেমন", forming.bangla)
        assertTrue(forming.candidates.size > 1, "the UI needs candidates to click")

        r.controller.pickCandidate(1)
        r.idle()
        assertEquals(listOf("কেম"), r.injector.texts)
        // Non-primary choice: the engine learns it (S26 law, explicit choice).
        assertEquals(listOf("kmn" to "কেম"), r.engine.taught)

        r.type("kmn")
        r.idle()
        r.controller.pickCandidate(0)
        r.idle()
        // The pending space from the first pick, then the primary itself.
        assertEquals(listOf("কেম", " ", "কেমন"), r.injector.texts)
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
        assertTrue(r.listener.previews.isEmpty())

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
        assertEquals(listOf("আমি"), r.injector.texts)
        assertEquals(listOf(Mode.ENGLISH), r.listener.modes)
    }

    @Test
    fun eachHandledKeyProducesAtMostOnePreviewCall() {
        val r = rig()
        r.type("ami")
        r.idle()
        assertEquals(3, r.listener.previews.size, "one coalesced preview per keystroke")
        assertEquals("ami", r.listener.previews.last().raw)
        assertEquals("আমি", r.listener.previews.last().bangla)

        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertEquals(4, r.listener.previews.size)
        assertEquals(PreviewCall("", "", emptyList()), r.listener.previews.last())
    }

    @Test
    fun backspaceEditsFormingThenReturnsToTheApp() {
        val r = rig()
        r.type("amii")
        assertTrue(r.key(RawKey.Backspace))
        r.idle()
        assertEquals("ami", r.listener.previews.last().raw)
        assertTrue(r.injector.emitted.isEmpty(), "editing the buffer injects nothing")

        repeat(3) { assertTrue(r.key(RawKey.Backspace)) }
        r.idle()
        assertEquals(PreviewCall("", "", emptyList()), r.listener.previews.last())
        // Buffer empty again: the host owns backspace.
        assertFalse(r.key(RawKey.Backspace))
    }

    @Test
    fun escapeIsForwardedToTheAppWhileAPendingSpaceIsHeld() {
        val r = rig()
        r.type("ami")
        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertEquals(listOf("আমি"), r.injector.texts)

        // The pending space keeps the composer active, so Escape is ours to
        // handle — but the app MUST still get it: Escape closes dialogs, and a
        // word-then-space is the state a user spends most of their time in.
        assertTrue(r.key(RawKey.Escape))
        r.idle()
        assertEquals(listOf(Emitted.Key(RawKey.Escape)), r.injector.emitted.drop(1))

        // And it keeps working — the pending space survives, so the next Escape
        // takes the same path rather than silently dying.
        assertTrue(r.key(RawKey.Escape))
        r.idle()
        assertEquals(listOf(RawKey.Escape, RawKey.Escape), r.injector.keys)

        // While a word IS forming, Escape cancels to the raw roman instead —
        // nothing is forwarded to the app.
        r.type("kmn")
        assertTrue(r.key(RawKey.Escape))
        r.idle()
        assertEquals(listOf("আমি", " ", "kmn"), r.injector.texts)
        assertEquals(listOf(RawKey.Escape, RawKey.Escape), r.injector.keys)
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
        assertEquals(
            listOf(Emitted.Text("আমি"), Emitted.VirtualKey(0x39, true)),
            r.injector.emitted,
        )
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
    fun unmanagedKeyAfterAHeldSpaceKeepsTheSpace() {
        val r = rig()
        r.type("ami")
        assertTrue(r.key(RawKey.Space))
        r.idle()
        assertEquals(listOf("আমি"), r.injector.texts)

        // The pending space keeps the composer active, so the key is ours to
        // order — but the space the user already typed must not vanish with it.
        assertTrue(r.key(RawKey.Unmanaged(0x39, shift = true)))
        r.idle()
        assertEquals(
            listOf(Emitted.Text("আমি"), Emitted.Text(" "), Emitted.VirtualKey(0x39, true)),
            r.injector.emitted,
        )
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
        assertTrue(r.injector.emitted.isEmpty())

        engine.failing = true
        assertTrue(r.key(RawKey.Letter('k')))
        r.idle()
        // The state machine is reset instead of holding a buffer it can never
        // render: the last good Bangla is committed, not lost.
        assertEquals(listOf("আম"), r.injector.texts)
        assertEquals(PreviewCall("", "", emptyList()), r.listener.previews.last())
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
        assertEquals(listOf("আম", "আমি"), r.injector.texts)
        assertEquals(1, synchronized(errors) { errors.size })
    }

    @Test
    fun shutdownDrainsQueuedWork() {
        val r = rig()
        r.type("ami")
        assertTrue(r.key(RawKey.Space))
        r.controller.shutdown()
        assertEquals(listOf("আমি"), r.injector.texts)
        // The quit path unhooks AFTER shutdown: a key swallowed now would be
        // eaten by a worker that no longer exists.
        assertFalse(r.key(RawKey.Letter('a')))
        assertFalse(r.key(RawKey.Space))
        assertEquals(listOf("আমি"), r.injector.texts)
        r.controller.shutdown() // idempotent
    }
}
