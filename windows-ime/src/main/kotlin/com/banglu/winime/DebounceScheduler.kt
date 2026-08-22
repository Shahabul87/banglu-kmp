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
         * `getSuggestions` measured 2.3 ms per call against the real dictionary
         * (warmed, over a 22-prefix keystroke workload; 2.25 ms at limit 3 and
         * 2.65 ms at limit 6, so the count is not the lever — the cost is the
         * lookup). That is still far more than the conversion beside it, which
         * is why it is the only thing left on a timer.
         *
         * **45 ms, down from 120 ms.** 120 ms was chosen to collapse a typing
         * burst into one query, and it did — but combined with a strip that was
         * blanked on every keystroke it meant a user typing at a normal 40-90 ms
         * per letter never saw suggestions at all, which is exactly what they
         * reported. Two things changed together: the strip now survives a
         * keystroke (`Composer.refresh` no longer clears it), and this dropped
         * to 45 ms so the list is at most one short pause behind the buffer.
         *
         * 45 ms is under the 60-90 ms rhythm of ordinary typing, so the list
         * refreshes between most keystrokes and the strip is populated the
         * whole time a word is being typed. The cost is bounded and off the
         * hook thread: at worst one 2.3 ms query per letter on the worker lane,
         * which can delay the next letter's echo by that much and nothing more.
         * Going much lower buys freshness nobody can perceive; going back up
         * re-creates the "suggestion is gone" report.
         */
        const val REFINE_DEBOUNCE_MS = 45L
    }
}
