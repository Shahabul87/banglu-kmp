package com.banglu.winime

import com.banglu.winime.composer.Composer
import com.banglu.winime.composer.ComposerAction
import com.banglu.winime.composer.ComposerEngine
import com.banglu.winime.composer.ComposerKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The mode machine between the keyboard hook and the [Composer].
 *
 * Two threads, and only two:
 *
 * 1. **The hook thread** calls [onKey] for every keystroke and gets back the
 *    swallow decision. Windows silently unregisters a low-level hook whose
 *    callback is slow, so [onKey] does nothing but classify, read volatile
 *    state, enqueue, and return — no engine, no I/O, no logging, no blocking.
 * 2. **One worker thread** drains the queue and is the ONLY thread that
 *    touches the composer, the engine, the injector, or the listener.
 *    `SmartEngine` is not thread-safe, and a single FIFO lane is what
 *    guarantees the ordering law: when a keystroke both commits text and
 *    forwards a key (Enter while a word is forming), the committed Bangla
 *    reaches the app BEFORE the forwarded key.
 *
 * The link between them is the composer mirror ([composerActive]): the hook
 * thread must decide "is a word being composed?" without waiting for the
 * worker, or typing `a` then Enter faster than the worker drains would let
 * the Enter escape to the app ahead of the Bangla.
 */
class Controller(
    private val engine: ComposerEngine,
    private val injector: TextInjector,
    private val compat: AppCompat,
    private val listener: ControllerListener,
) : HookSink {

    private val composer = Composer(engine)

    /** Task 8 tray toggle, passed straight through to the composer's own @Volatile field. */
    var banglaDigits: Boolean
        get() = composer.banglaDigits
        set(value) { composer.banglaDigits = value }

    /** Work items for the worker. FIFO order across all of them IS the ordering law. */
    private sealed interface Job {
        /**
         * [claimed] says whether [claim] incremented [inFlight] for this key,
         * and therefore whether the worker owes a decrement. FocusChanged and
         * the mode hotkey are enqueued unclaimed — the hook does not swallow
         * anything on their behalf — and releasing a claim that was never
         * taken is what drove the counter permanently negative.
         */
        class Key(val key: RawKey, val claimed: Boolean) : Job
        class Pick(val index: Int) : Job
        class ModeSwitch(val from: Mode, val to: Mode) : Job
        class Idle(val latch: CountDownLatch) : Job
        data object Stop : Job
    }

    private val queue = LinkedBlockingQueue<Job>()

    /**
     * Keys the hook has claimed but the worker has not finished. The composer's
     * own state cannot answer "is composition active?" for the hook thread: it
     * lags by exactly these keys, and a word can end (leaving the composer
     * idle) while the next letter is already queued.
     */
    private val inFlight = AtomicInteger(0)

    /** The worker's answer to the same question, refreshed after every key. */
    @Volatile
    private var composerBusy = false

    /** Stale-true is harmless (we swallow a key we would have passed); stale-false is a bug. */
    private val composerActive: Boolean get() = composerBusy || inFlight.get() > 0

    /**
     * Test-only view of the claim counter. Every claim must be matched by
     * exactly one release: a counter that drifts below zero can never satisfy
     * `> 0` again, which silently disables the whole mirror and lets Enter
     * overtake a pending commit. Pinned in ControllerTest.
     */
    internal val inFlightCount: Int get() = inFlight.get()

    @Volatile
    var mode: Mode = Mode.BANGLA
        private set

    /**
     * False until the full dictionary is attached (spec §4.8): while the store
     * loads off-thread every key passes through untouched rather than being
     * half-converted. Task 7 sets it on boot success.
     */
    @Volatile
    var engineReady: Boolean = false

    @Volatile
    private var stopped = false

    /** Last throwable a job threw; kept for diagnostics, never printed. */
    @Volatile
    internal var lastWorkerError: Throwable? = null
        private set

    /**
     * Push sink for a job that threw — the app logs it (and can show a tray
     * warning). Invoked on the worker thread, after the composer has been
     * reset, and never allowed to throw back into the loop. Without it a
     * failing engine is completely silent to the user.
     */
    @Volatile
    var onError: ((Throwable) -> Unit)? = null

    init {
        // Assigned before the worker exists so no job can ever observe a null hook.
        composer.onPick = { raw, bangla, wasPrimary ->
            // S26 law: an explicit alternative teaches; the engine's own
            // primary never does.
            if (!wasPrimary) engine.selected(raw, bangla)
        }
    }

    private val worker = Thread({ runLoop() }, "banglu-winime-worker").apply {
        isDaemon = true
        start()
    }

    // MARK: - hook thread

    override fun onKey(key: RawKey, foregroundExe: String): Boolean {
        // Quit unhooks after shutdown(): a key swallowed once the worker is
        // gone would be eaten, never injected. Cheapest possible guard first.
        if (stopped) return false
        // A focus notification is never swallowed, but must always reach the
        // worker: it is what flushes a word stranded in the old window.
        if (key === RawKey.FocusChanged) {
            queue.offer(Job.Key(key, claimed = false))
            return false
        }
        if (!engineReady) return false
        val m = mode
        if (m == Mode.OFF) return false
        // A passthrough app (password managers) sees every key untouched —
        // including our own hotkey. Nothing of it reaches the composer.
        if (compat.isPassthrough(foregroundExe)) return false
        if (key === RawKey.ToggleHotkey) {
            queue.offer(Job.Key(key, claimed = false))
            return true
        }
        // English mode is full passthrough; only the hotkey above stays claimed.
        if (m == Mode.ENGLISH) return false
        return when (key) {
            is RawKey.Letter, is RawKey.Digit, is RawKey.Punct, RawKey.Space -> {
                claim(key)
                true
            }

            // Unmanaged belongs here for the same reason Enter does. Left to
            // pass through, `(` would land in the app NOW while the Bangla for
            // the word still forming lands after it — the user types "আমি(" and
            // reads "(আমি". Claimed, it goes down the one FIFO lane and the
            // worker re-injects it behind the commit.
            RawKey.Backspace, RawKey.Enter, RawKey.Tab, RawKey.Escape, is RawKey.Unmanaged ->
                if (composerActive) {
                    claim(key)
                    true
                } else {
                    false
                }

            else -> false
        }
    }

    /** Hook thread only: claim the key BEFORE it becomes visible to the worker. */
    private fun claim(key: RawKey) {
        inFlight.incrementAndGet()
        queue.offer(Job.Key(key, claimed = true))
    }

    // MARK: - other threads (tray, preview window)

    fun setModeExternal(m: Mode) {
        val from = mode
        if (from == m) return
        // Published eagerly: the hook must stop (or start) swallowing at the
        // click, not when the worker catches up. The flush that belongs to the
        // transition still runs in queue order.
        mode = m
        queue.offer(Job.ModeSwitch(from, m))
    }

    fun pickCandidate(index: Int) {
        queue.offer(Job.Pick(index))
    }

    /** Drains what is queued, then stops the worker. Idempotent. */
    fun shutdown() {
        if (stopped) return
        stopped = true
        queue.offer(Job.Stop)
        worker.join(SHUTDOWN_JOIN_MS)
    }

    /**
     * Blocks until every job enqueued before this call has been fully
     * dispatched. One worker plus FIFO means "my marker came out" is a
     * complete drain — tests never need to sleep or poll.
     */
    internal fun awaitIdle(timeoutMs: Long = AWAIT_IDLE_MS) {
        check(!stopped) { "controller is shut down" }
        val latch = CountDownLatch(1)
        queue.offer(Job.Idle(latch))
        check(latch.await(timeoutMs, TimeUnit.MILLISECONDS)) { "worker did not drain within ${timeoutMs}ms" }
    }

    // MARK: - worker thread

    private fun runLoop() {
        while (true) {
            val job = try {
                queue.take()
            } catch (_: InterruptedException) {
                return
            }
            try {
                when (job) {
                    is Job.Key -> try {
                        handle(job.key)
                    } finally {
                        // Exactly one release per claim. An unclaimed signal
                        // (FocusChanged, the hotkey) never incremented, and
                        // decrementing for it would sink the counter below
                        // zero for the rest of the session — after which
                        // `inFlight.get() > 0` is unreachable and the mirror
                        // is dead.
                        if (job.claimed) inFlight.decrementAndGet()
                    }

                    is Job.Pick -> {
                        dispatch(composer.pick(job.index))
                        syncMirror()
                    }

                    is Job.ModeSwitch -> switchMode(job.from, job.to)
                    is Job.Idle -> job.latch.countDown()
                    Job.Stop -> return
                }
            } catch (t: Throwable) {
                // A dead worker is a dead keyboard: one bad key must not end it.
                lastWorkerError = t
                // …but surviving is not enough. `Composer.handle` appends the
                // character to its buffer BEFORE it calls the engine, so a key
                // that threw leaves a buffer that was never rendered or
                // committed: every following letter would be swallowed into a
                // growing invisible word and throw again. Reset the state
                // machine — focusLost() flushes the last good Bangla and never
                // touches the engine.
                if (job is Job.Key) recoverFromFailedKey()
                runCatching { onError?.invoke(t) }
            }
        }
    }

    private fun recoverFromFailedKey() {
        try {
            dispatch(composer.focusLost())
        } catch (t: Throwable) {
            // The injector itself is failing. The composer's state was already
            // cleared by focusLost(), so the mirror below is still the truth.
            lastWorkerError = t
        } finally {
            syncMirror()
        }
    }

    private fun handle(key: RawKey) {
        when (key) {
            RawKey.FocusChanged -> {
                dispatch(composer.focusLost())
                syncMirror()
            }

            RawKey.ToggleHotkey -> {
                val from = mode
                switchMode(from, if (from == Mode.BANGLA) Mode.ENGLISH else Mode.BANGLA)
            }

            is RawKey.Unmanaged -> {
                // A held space is one the user already typed and can see the
                // effect of; focusLost() discards it, which is right when focus
                // really left and wrong here, where the next thing they typed
                // belongs after that space.
                val heldSpace = composer.pendingSpace && !composer.forming
                // Commit first, key second — the same ordering as ForwardKey,
                // on the same single lane. focusLost() never calls the engine.
                dispatch(composer.focusLost())
                if (heldSpace) injector.injectText(" ")
                syncMirror()
                injector.injectVirtualKey(key.vk, key.shift)
            }

            else -> {
                val composerKey = toComposerKey(key) ?: return
                dispatch(composer.handle(composerKey))
                syncMirror()
            }
        }
    }

    private fun switchMode(from: Mode, to: Mode) {
        if (from == Mode.BANGLA) {
            // Never strand a half-typed word in the app we are leaving.
            dispatch(composer.focusLost())
            syncMirror()
        }
        mode = to // idempotent when setModeExternal already published it
        listener.onModeChanged(to)
    }

    /**
     * Injection first, UI after: the app must have the text before the preview
     * window redraws. Preview/Candidates coalesce into ONE listener call per
     * handled key so the window never flickers through an intermediate state.
     */
    private fun dispatch(actions: List<ComposerAction>) {
        if (actions.isEmpty()) return
        var previewBangla: String? = null
        var previewRaw = ""
        var candidates: List<String>? = null
        for (action in actions) {
            when (action) {
                is ComposerAction.Commit -> injector.injectText(action.text)
                is ComposerAction.ForwardKey -> injector.injectKey(toRawKey(action.key))
                is ComposerAction.Preview -> {
                    previewBangla = action.bangla
                    previewRaw = action.raw
                }

                is ComposerAction.Candidates -> candidates = action.list
            }
        }
        if (previewBangla != null || candidates != null) {
            listener.onPreview(previewBangla ?: "", previewRaw, candidates ?: emptyList())
        }
    }

    private fun syncMirror() {
        composerBusy = composer.forming || composer.pendingSpace
    }

    private fun toComposerKey(key: RawKey): ComposerKey? = when (key) {
        is RawKey.Letter -> ComposerKey.Letter(key.c)
        is RawKey.Digit -> ComposerKey.Digit(key.c)
        is RawKey.Punct -> ComposerKey.Punctuation(key.p)
        RawKey.Space -> ComposerKey.Space
        RawKey.Backspace -> ComposerKey.Backspace
        RawKey.Enter -> ComposerKey.Enter
        RawKey.Tab -> ComposerKey.Tab
        RawKey.Escape -> ComposerKey.Escape
        RawKey.ToggleHotkey, RawKey.FocusChanged, is RawKey.Unmanaged -> null
    }

    private fun toRawKey(key: ComposerKey): RawKey = when (key) {
        is ComposerKey.Letter -> RawKey.Letter(key.c)
        is ComposerKey.Digit -> RawKey.Digit(key.c)
        is ComposerKey.Punctuation -> RawKey.Punct(key.p)
        ComposerKey.Space -> RawKey.Space
        ComposerKey.Backspace -> RawKey.Backspace
        ComposerKey.Enter -> RawKey.Enter
        ComposerKey.Tab -> RawKey.Tab
        ComposerKey.Escape -> RawKey.Escape
    }

    private companion object {
        const val SHUTDOWN_JOIN_MS = 2_000L
        const val AWAIT_IDLE_MS = 30_000L
    }
}
