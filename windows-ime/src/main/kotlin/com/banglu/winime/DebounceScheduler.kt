package com.banglu.winime

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The production [RefineScheduler]: one daemon timer thread that fires the
 * candidate refresh once the user stops typing.
 *
 * The task it runs must only ENQUEUE work for the controller's worker — the
 * engine is not thread-safe and this thread is not its lane.
 */
class DebounceScheduler(
    private val delayMs: Long = REFINE_DEBOUNCE_MS,
) : RefineScheduler {

    private val timer = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "banglu-winime-refine").apply { isDaemon = true }
    }.also {
        // Without this a superseded task stays in the queue as a tombstone
        // until its delay elapses; at one cancel per keystroke that is a
        // steadily growing queue for the lifetime of the process.
        (it as? ScheduledThreadPoolExecutor)?.removeOnCancelPolicy = true
    }

    /** Written and read only under [lock]; there is at most one live task. */
    private var pending: ScheduledFuture<*>? = null
    private val lock = Any()

    override fun schedule(task: () -> Unit) {
        synchronized(lock) {
            pending?.cancel(false)
            pending = runCatching {
                timer.schedule({ runCatching { task() } }, delayMs, TimeUnit.MILLISECONDS)
            }.getOrNull() // a rejected task after close() must not kill the worker
        }
    }

    override fun cancel() {
        synchronized(lock) {
            pending?.cancel(false)
            pending = null
        }
    }

    override fun close() {
        cancel()
        timer.shutdownNow()
    }

    companion object {
        /**
         * How long the user must pause before we ask the engine for the
         * suggestion list.
         *
         * `getSuggestions` measures ~7.5 ms per keystroke against the real
         * dictionary — three orders of magnitude more than the conversion
         * itself (~17 us) and the one call that made typing feel slow. It is
         * therefore the ONLY thing left on a timer.
         *
         * 120 ms sits in the gap between the two rhythms that matter: inside a
         * fast typist's burst the letters land 40-90 ms apart, so a whole word
         * collapses into a single engine query instead of one per letter; and
         * the pause before reaching for space, a digit or the mouse is longer
         * than this, so the strip is already up by the time the user looks for
         * it. Shorter re-introduces the per-letter query this fix removes;
         * much longer and the suggestions feel like they arrive late.
         */
        const val REFINE_DEBOUNCE_MS = 120L
    }
}
