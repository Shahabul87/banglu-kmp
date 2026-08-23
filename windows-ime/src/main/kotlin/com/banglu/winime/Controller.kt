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
    private val refineScheduler: RefineScheduler = DebounceScheduler(),
) : HookSink {

    private val composer = Composer(engine)

    /**
     * The text this controller has physically typed into the focused
     * application for the word currently forming — and therefore the ONLY text
     * it is ever allowed to backspace.
     *
     * Worker thread only (the @Volatile exists so tests can read it after a
     * drain). Every path that ends composition without owning the caret any
     * more clears it WITHOUT injecting backspaces; see [abandonComposition].
     */
    @Volatile
    private var echoed = ""

    /** Test-only view of the echo ledger. */
    internal val echoedText: String get() = echoed

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

        /**
         * The debounced suggestion query, posted by the scheduler thread and
         * run HERE so it shares the one engine lane. [generation] is the
         * composer's buffer version it was scheduled for; a stale one is
         * dropped by the composer rather than shown.
         */
        class Refine(val generation: Long) : Job
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

    /** Test-only: awaitIdle is illegal once the worker has been told to stop. */
    internal val isStopped: Boolean get() = stopped

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
        // A conversion that fell back to the rule layer still typed the user's
        // word, so it is not a job failure — but it must not be silent either.
        composer.onConversionFault = { t -> reportFault(t) }
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
        // Belt and braces: the worker closes it on Job.Stop, but a worker that
        // never got there would otherwise leave a live timer thread that can
        // still enqueue into a queue nobody drains.
        refineScheduler.close()
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
                        updateRefineSchedule()
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
                        updateRefineSchedule()
                    }

                    // No reschedule: the strip for this buffer has just been
                    // filled, and asking again for the same generation would
                    // loop the engine forever on a word nobody is typing.
                    is Job.Refine -> dispatch(composer.refineCandidates(job.generation))

                    is Job.ModeSwitch -> switchMode(job.from, job.to)
                    is Job.Idle -> job.latch.countDown()
                    Job.Stop -> {
                        // The app is going away with the user's word already on
                        // screen. Clearing the ledger without backspaces is the
                        // only correct end: whatever we typed is theirs now.
                        echoed = ""
                        refineScheduler.close()
                        return
                    }
                }
            } catch (t: Throwable) {
                // A dead worker is a dead keyboard: one bad key must not end it.
                // …but surviving is not enough. `Composer.handle` appends the
                // character to its buffer BEFORE it calls the engine, so a key
                // that threw leaves a buffer that was never rendered or
                // committed: every following letter would be swallowed into a
                // growing invisible word and throw again. Reset the state
                // machine.
                //
                // A failed Refine needs no reset at all: it never mutates the
                // buffer and the converted word is already on screen, so the
                // only casualty is one suggestion strip.
                if (job is Job.Key) recoverFromFailedKey()
                reportFault(t)
            }
        }
    }

    private fun recoverFromFailedKey() {
        // Abandon, not flush: the last good Bangla is ALREADY in the host
        // application (live echo), so re-injecting it would duplicate the word
        // and backspacing it would destroy it.
        runCatching { abandonComposition() }
    }

    private fun handle(key: RawKey) {
        when (key) {
            // The caret has moved to a different window. The word we echoed
            // stays where the user typed it; anything we injected or deleted
            // now would land in the NEW window, on somebody else's text.
            RawKey.FocusChanged -> abandonComposition()

            RawKey.ToggleHotkey -> {
                val from = mode
                switchMode(from, if (from == Mode.BANGLA) Mode.ENGLISH else Mode.BANGLA)
            }

            is RawKey.Unmanaged -> {
                // An unmanaged key is an arrow, Home, End, a bracket — several
                // of them MOVE THE CARET, and the ones that do not are still
                // about to. The forming word (and any space after it) is
                // already on screen, so ending composition here means sealing
                // the ledger, never editing it.
                abandonComposition()
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
            // Never strand a half-typed word — and never edit it either: the
            // user reached the tray or the control window to get here, so the
            // caret they left behind is no longer under our control.
            abandonComposition()
        }
        mode = to // idempotent when setModeExternal already published it
        listener.onModeChanged(to)
    }

    /**
     * Ends composition leaving the host application's text EXACTLY as it is.
     *
     * This is the primary safety rule of the live echo. Every caller here has
     * lost the guarantee that our caret is still the one the backspaces would
     * hit — focus moved, the user clicked our own window, a caret-moving key is
     * about to fire, or the worker is unwinding from a fault. The word the user
     * typed is already on their screen and belongs to them; the only correct
     * action is to stop tracking it.
     */
    private fun abandonComposition() {
        val ending = composer.forming || composer.retroSpaceArmed
        composer.focusLost() // discards its actions ON PURPOSE — see above
        echoed = ""
        refineScheduler.cancel()
        syncMirror()
        if (ending) listener.onCandidates(emptyList(), predictions = false)
    }

    /**
     * Injection first, UI after: the app must have the text before the popup
     * redraws. The candidate lists in a batch coalesce into ONE listener call
     * so the window never flickers through an intermediate state.
     */
    private fun dispatch(actions: List<ComposerAction>) {
        if (actions.isEmpty()) return
        var candidates: ComposerAction.Candidates? = null
        for (action in actions) {
            when (action) {
                is ComposerAction.Commit -> commitEcho(action.text)
                is ComposerAction.DeleteBack -> deleteCommitted(action.count)
                is ComposerAction.ForwardKey -> injector.injectKey(toRawKey(action.key))
                is ComposerAction.Preview -> reconcileEcho(action.bangla)
                is ComposerAction.Candidates -> candidates = action
            }
        }
        candidates?.let { listener.onCandidates(it.list, it.predictions) }
    }

    /**
     * Makes the host application read [target] for the word being typed, by
     * changing only what actually differs.
     *
     * The common-prefix diff is not an optimisation: re-typing the whole word
     * on every letter would flicker visibly, and every backspace it saves is
     * one that cannot go wrong. Typing `ami` sends `আ`, then `ম`, then `ি` —
     * three inserts and no deletions at all, because each conversion extends
     * the last. Only a conversion that REWRITES an earlier letter costs
     * deletions: `kmn` goes `ক`, `কিমি`, `কেমন`, so the third keystroke sends
     * three backspaces and types `েমন`.
     */
    private fun reconcileEcho(target: String) {
        if (target == echoed) return
        val common = commonPrefixLength(echoed, target)
        if (echoed.length > common) {
            injector.injectBackspaces(echoed.length - common)
            // Recorded BEFORE the insert: if that throws, the ledger must
            // still describe what is really on screen, or the next diff would
            // backspace text nobody typed.
            echoed = echoed.substring(0, common)
        }
        if (target.length > common) {
            injector.injectText(target.substring(common))
            echoed = target
        }
    }

    /**
     * The word is finished. It is already on screen — with the conversion
     * computed synchronously this is a no-op in the overwhelmingly common case
     * — so all that is left is to stop treating it as editable. The exception
     * is a commit that differs from what is displayed (Escape's raw roman, a
     * candidate pick), which reconciles through the same diff first.
     */
    private fun commitEcho(text: String) {
        reconcileEcho(text)
        echoed = ""
    }

    /**
     * Un-types text this controller committed on the IMMEDIATELY PRECEDING
     * keystroke — the space that a second space turns into a দাঁড়ি, or that a
     * tight punctuation mark swallows.
     *
     * This is the one deletion that reaches outside the echo ledger, and it is
     * safe for exactly one reason: the composer only arms it while the space it
     * wrote is still the last character in the document, and every path that
     * loses the caret — focus change, mode switch, an unmanaged key, fault
     * recovery, shutdown — runs [abandonComposition], which calls
     * `composer.focusLost()` and disarms it. Nothing here may ever be given a
     * count derived from anything other than that one-keystroke-old space.
     *
     * Residual risk, stated rather than hidden: a mouse click that moves the
     * caret WITHIN the same window is invisible to a keyboard hook, so a user
     * who types `word `, clicks elsewhere, and then presses space loses one
     * character at the new caret. Avro has the identical exposure; closing it
     * needs a low-level mouse hook, which is a deliberate v2 decision.
     */
    private fun deleteCommitted(count: Int) {
        if (count <= 0) return
        // The ledger must be empty here by construction (the composer arms the
        // retro-space only after a commit cleared it). If it ever is not, the
        // pending echo is the newer text and deleting behind it would corrupt
        // the word — so reconcile the ledger away first rather than guess.
        check(echoed.isEmpty()) { "a retro delete must never run while a word is echoed" }
        injector.injectBackspaces(count)
    }

    /** Arms (or drops) the debounced suggestion query for the current buffer. */
    private fun updateRefineSchedule() {
        if (!composer.forming) {
            refineScheduler.cancel()
            return
        }
        val generation = composer.generation
        // The scheduler thread ONLY enqueues: the engine has exactly one lane
        // and this is not it.
        refineScheduler.schedule { queue.offer(Job.Refine(generation)) }
    }

    private fun reportFault(t: Throwable) {
        lastWorkerError = t
        runCatching { onError?.invoke(t) }
    }

    /**
     * The retro-space counts as "busy" deliberately. It is what routes Enter,
     * Tab, Escape, Backspace and an unmanaged key through the worker after a
     * space, and each of those disarms it — without that, an arrow key would
     * pass straight through, move the caret, and leave the next space free to
     * backspace whatever now sits behind it.
     */
    private fun syncMirror() {
        composerBusy = composer.forming || composer.retroSpaceArmed
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

        /**
         * Length of the shared leading run of [a] and [b], in UTF-16 units —
         * the unit `SendInput` types and a host application's backspace
         * deletes.
         *
         * A boundary is never allowed to fall between the halves of a
         * surrogate pair: splitting one would inject a lone surrogate, which
         * renders as a replacement box and is undeletable by our own count.
         * Bengali is entirely inside the BMP, so this only ever bites on an
         * emoji or a rare-plane character reaching the buffer — which the raw
         * escape hatch can do.
         */
        fun commonPrefixLength(a: String, b: String): Int {
            var i = 0
            val limit = minOf(a.length, b.length)
            while (i < limit && a[i] == b[i]) i++
            if (i > 0 && i < a.length && a[i - 1].isHighSurrogate()) i--
            return i
        }
    }
}
