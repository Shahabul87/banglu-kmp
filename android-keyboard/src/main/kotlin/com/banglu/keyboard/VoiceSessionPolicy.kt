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
     *  right now" — worth exactly ONE retry forcing the offline-capable
     *  path before giving up. Includes ERROR_SERVER/_DISCONNECTED: the old
     *  code let those retry forever (a latent bug — the guard only fired for
     *  ERROR_NETWORK/_TIMEOUT), which is exactly the "listening chip that
     *  can never deliver" failure mode this round closes. */
    private val NETWORK_CLASS_ERRORS = setOf(ERROR_NETWORK, ERROR_NETWORK_TIMEOUT, ERROR_SERVER, ERROR_SERVER_DISCONNECTED)

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
     * @param offlineRetryUsed whether [RetryOffline] has already fired once
     *   this dictation session
     * @param fruitlessRestarts consecutive NO_MATCH/SPEECH_TIMEOUT cycles
     *   already counted BEFORE this one
     * @param maxFruitlessRestarts session cap on consecutive silent cycles
     */
    fun onError(
        error: Int,
        offlineRetryUsed: Boolean,
        fruitlessRestarts: Int,
        maxFruitlessRestarts: Int
    ): VoiceAction {
        if (error in OFFLINE_PACK_MISSING_ERRORS) {
            return VoiceAction.ShowMessage(VoiceInputState.OFFLINE_PACK_MISSING)
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
            return if (!offlineRetryUsed) {
                VoiceAction.RetryOffline
            } else {
                VoiceAction.ShowMessage(VoiceInputState.ERROR)
            }
        }
        if (error in BUSY_CLASS_ERRORS) {
            return VoiceAction.RestartSameMode
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
