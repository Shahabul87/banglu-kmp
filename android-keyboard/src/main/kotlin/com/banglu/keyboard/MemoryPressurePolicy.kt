package com.banglu.keyboard

/**
 * S72 (production audit): pure decision table for memory-pressure reactions.
 *
 * Evidence from the tester Samsung (heapgrowthlimit 256m): full-dictionary
 * mode holds ~172MB Java heap, and the OS exit history shows repeated
 * reason=LOW_MEMORY kills (2026-07-26…30, one at 220MB RSS). Those kills
 * look to users like the keyboard vanishing/restarting. This policy decides
 * when to shed memory and when to degrade the dictionary profile to lite.
 *
 * Trim levels are re-declared as plain ints (ComponentCallbacks2 values,
 * stable since API 14) so this stays a JVM-only unit-testable object — the
 * VoiceSessionPolicy / VoicePartialDiff pattern.
 */
object MemoryPressurePolicy {

    const val TRIM_MEMORY_RUNNING_MODERATE = 5
    const val TRIM_MEMORY_RUNNING_LOW = 10
    const val TRIM_MEMORY_RUNNING_CRITICAL = 15
    const val TRIM_MEMORY_UI_HIDDEN = 20
    const val TRIM_MEMORY_BACKGROUND = 40
    const val TRIM_MEMORY_MODERATE = 60
    const val TRIM_MEMORY_COMPLETE = 80

    /** Cold starts that stay in lite mode after a pressure degrade before
     *  full mode is retried — self-healing without a settings toggle. */
    const val FORCED_LITE_LAUNCHES = 5

    /** Post-load heap fraction above which full mode is not sustainable.
     *  The tester Samsung sits at ~67% steady in full mode and must NOT
     *  trip this — the guard targets devices whose limit leaves no
     *  headroom at all (192m-class), not flagships under normal load. */
    const val POST_LOAD_DEGRADE_FRACTION = 0.80

    enum class Action { NONE, CLEAR_CACHES, DEGRADE_TO_LITE }

    /**
     * Reaction to [android.content.ComponentCallbacks2.onTrimMemory].
     *
     * DEGRADE only on signals that mean "this process is genuinely about to
     * be killed for memory" (RUNNING_LOW / RUNNING_CRITICAL while active,
     * COMPLETE at the tail of the cached-LRU list). Routine trims
     * (UI_HIDDEN / BACKGROUND fire on every keyboard hide) only shed caches
     * — degrading on those would put every device in lite mode within a day.
     */
    fun onTrim(level: Int, alreadyLite: Boolean): Action = when {
        level == TRIM_MEMORY_RUNNING_LOW ||
            level == TRIM_MEMORY_RUNNING_CRITICAL ||
            level == TRIM_MEMORY_COMPLETE ->
            if (alreadyLite) Action.CLEAR_CACHES else Action.DEGRADE_TO_LITE
        level == TRIM_MEMORY_RUNNING_MODERATE ||
            level == TRIM_MEMORY_UI_HIDDEN ||
            level == TRIM_MEMORY_BACKGROUND ||
            level == TRIM_MEMORY_MODERATE -> Action.CLEAR_CACHES
        else -> Action.NONE
    }

    /** Adaptive post-load guard: full dictionary just loaded — is there
     *  enough heap headroom left to actually run in it? */
    fun shouldDegradeAfterLoad(usedBytes: Long, maxBytes: Long, alreadyLite: Boolean): Boolean {
        if (alreadyLite || maxBytes <= 0L) return false
        return usedBytes.toDouble() / maxBytes.toDouble() > POST_LOAD_DEGRADE_FRACTION
    }
}
