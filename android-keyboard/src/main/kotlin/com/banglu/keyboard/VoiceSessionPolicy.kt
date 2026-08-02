package com.banglu.keyboard

/**
 * S55 (audit F-ANDROID-006 / .superpowers/sdd/voice-trace-report.md):
 * pure decision table for the voice-dictation error ladder.
 *
 * [BangluIMEService] feeds this policy a `SpeechRecognizer.onError` code plus
 * the current session counters and gets back a [VoiceAction] describing what
 * to do; the service performs every side effect (recognizer create/destroy,
 * `voiceInputState` writes, InputConnection edits). Keeping the decision here
 * — "does ERROR_NETWORK retry exactly once", "does a silence cap end the
 * session gracefully" — means those facts are pinned by a plain unit test
 * instead of only being provable by reading 100+ lines of listener code.
 *
 * Error codes are re-declared as plain ints (not `android.speech
 * .SpeechRecognizer` references) for two reasons: (1) this file must stay
 * loadable by a JVM-only unit test with no Android runtime/Robolectric, and
 * (2) `ERROR_LANGUAGE_NOT_SUPPORTED`/`ERROR_LANGUAGE_UNAVAILABLE` are API 31+
 * symbols while minSdk is 24 — BangluIMEService already hardcodes these two
 * for the same reason (see its `VOICE_ERROR_LANGUAGE_*` constants). The
 * values below are SpeechRecognizer's stable, unchanged-since-API-8 ints.
 */
object VoiceSessionPolicy {

    const val ERROR_NETWORK_TIMEOUT = 1
    const val ERROR_NETWORK = 2
    const val ERROR_AUDIO = 3
    const val ERROR_SERVER = 4
    const val ERROR_CLIENT = 5
    const val ERROR_SPEECH_TIMEOUT = 6
    const val ERROR_NO_MATCH = 7
    const val ERROR_RECOGNIZER_BUSY = 8
    const val ERROR_INSUFFICIENT_PERMISSIONS = 9
    const val ERROR_SERVER_DISCONNECTED = 11
    const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
    const val ERROR_LANGUAGE_UNAVAILABLE = 13

    /** Errors that plausibly mean "the online recognizer can't be reached
     *  right now". S69 ladder: one PLAIN retry first (a transient server
     *  hiccup usually succeeds online, and forcing offline on the first
     *  hiccup was a trap — most devices have no bn-BD offline pack, so the
     *  forced-offline retry died with a language-pack error and voice went
     *  terminally dead, reproduced 2/2 in the 2026-07-19 on-device audit),
     *  then one offline retry, then give up. Includes ERROR_SERVER/
     *  _DISCONNECTED: the old code let those retry forever (S55). */
    private val NETWORK_CLASS_ERRORS = setOf(ERROR_NETWORK, ERROR_NETWORK_TIMEOUT, ERROR_SERVER, ERROR_SERVER_DISCONNECTED)

    /** S69: the service marks its plain network retry used via this check. */
    fun isNetworkClassError(error: Int): Boolean = error in NETWORK_CLASS_ERRORS

    /** Offline Bangla model missing / unsupported by this recognizer — no
     *  amount of retrying fixes this; only an actionable message does. */
    private val OFFLINE_PACK_MISSING_ERRORS = setOf(ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE)

    /** Nothing heard this cycle — restart, but only up to the fruitless cap. */
    private val SILENT_ERRORS = setOf(ERROR_NO_MATCH, ERROR_SPEECH_TIMEOUT)

    /** Transient recognizer hiccups — always worth a destroy+recreate retry
     *  with the SAME offline preference (not the network ladder). */
    private val BUSY_CLASS_ERRORS = setOf(ERROR_CLIENT, ERROR_RECOGNIZER_BUSY)

    /** What BangluIMEService should do in response to a classified event. */
    sealed interface VoiceAction {
        /** Commit any live partial, then destroy+recreate the recognizer and
         *  retry with EXTRA_PREFER_OFFLINE=true. Fires at most once per
         *  dictation session (guarded by the caller's `offlineRetryUsed`). */
        object RetryOffline : VoiceAction

        /** S69: the forced-offline retry hit a missing-language-pack error —
         *  drop the offline preference and retry ONLINE. Only reachable when
         *  the offline preference was auto-forced by the ladder (not chosen
         *  by the user), so it cannot loop: after this the session is online
         *  again and a further pack error is terminal. */
        object RetryOnline : VoiceAction

        /** Commit any live partial, then destroy+recreate the recognizer and
         *  retry with the SAME offline preference as before. */
        object RestartSameMode : VoiceAction

        /** Silence cap reached: end the session cleanly, no error message. */
        object GracefulStop : VoiceAction

        /** Terminal: show this UI state and let the caller schedule the
         *  auto-reset-to-IDLE timer. */
        data class ShowMessage(val state: VoiceInputState) : VoiceAction
    }

    /**
     * Classifies a `RecognitionListener.onError` callback.
     *
     * Caller contract: only invoke this when the session hasn't already been
     * torn down by an explicit user stop/cancel (BangluIMEService checks
     * `voiceStopRequested`/`voiceCancelRequested` BEFORE calling in — those
     * are user-driven outcomes, not error classifications, so they stay out
     * of this pure table).
     *
     * @param error the recognizer error code
     * @param networkRetryUsed S69: whether the plain same-mode network retry
     *   has already fired once this dictation session
     * @param offlineRetryUsed whether [RetryOffline] has already fired once
     *   this dictation session
     * @param offlineForcedBySession S69: true when the CURRENT offline
     *   preference came from the ladder's own [RetryOffline] (not from the
     *   user's "offline first" setting) — a pack-missing error then means
     *   the ladder walked into a dead end and must walk back online.
     * @param fruitlessRestarts consecutive NO_MATCH/SPEECH_TIMEOUT cycles
     *   already counted BEFORE this one
     * @param maxFruitlessRestarts session cap on consecutive silent cycles
     * @param busyRestarts consecutive ERROR_CLIENT/ERROR_RECOGNIZER_BUSY
     *   cycles already counted BEFORE this one — a device with a stolen
     *   recognition slot (trace §5) can return BUSY forever; unlike the
     *   watchdog (no callback at all), this IS a callback every time, so
     *   only a counted cap stops the "restart forever" loop.
     * @param maxBusyRestarts session cap on consecutive busy cycles
     */
    fun onError(
        error: Int,
        networkRetryUsed: Boolean,
        offlineRetryUsed: Boolean,
        offlineForcedBySession: Boolean,
        fruitlessRestarts: Int,
        maxFruitlessRestarts: Int,
        busyRestarts: Int,
        maxBusyRestarts: Int
    ): VoiceAction {
        if (error in OFFLINE_PACK_MISSING_ERRORS) {
            // S69: if WE forced offline (the user didn't ask for it), the
            // pack error is our own dead end — go back online instead of
            // showing a terminal message about an offline pack the user
            // never wanted. User-chosen offline-first keeps the honest
            // terminal message: their setting genuinely cannot work.
            return if (offlineForcedBySession) {
                VoiceAction.RetryOnline
            } else {
                VoiceAction.ShowMessage(VoiceInputState.OFFLINE_PACK_MISSING)
            }
        }
        if (error in SILENT_ERRORS) {
            val nextCount = fruitlessRestarts + 1
            return if (nextCount >= maxFruitlessRestarts) {
                VoiceAction.GracefulStop
            } else {
                VoiceAction.RestartSameMode
            }
        }
        if (error in NETWORK_CLASS_ERRORS) {
            // S69 ladder: plain online retry → offline retry → give up.
            return when {
                !networkRetryUsed -> VoiceAction.RestartSameMode
                !offlineRetryUsed -> VoiceAction.RetryOffline
                else -> VoiceAction.ShowMessage(VoiceInputState.ERROR)
            }
        }
        if (error in BUSY_CLASS_ERRORS) {
            val nextBusyCount = busyRestarts + 1
            return if (nextBusyCount > maxBusyRestarts) {
                VoiceAction.ShowMessage(VoiceInputState.BUSY_GIVEUP)
            } else {
                VoiceAction.RestartSameMode
            }
        }
        return when (error) {
            ERROR_INSUFFICIENT_PERMISSIONS -> VoiceAction.ShowMessage(VoiceInputState.PERMISSION_REQUIRED)
            else -> VoiceAction.ShowMessage(VoiceInputState.ERROR)
        }
    }

    /** The watchdog fired: `startListening()` was called but no
     *  RecognitionListener callback arrived within the timeout window. */
    fun onWatchdogTimeout(): VoiceAction = VoiceAction.ShowMessage(VoiceInputState.WATCHDOG_TIMEOUT)
}
