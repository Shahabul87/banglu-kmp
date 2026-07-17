package com.banglu.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S55 (F-ANDROID-006): pins the voice error-ladder decision table so a future
 * change can't silently reintroduce the "listening chip that never delivers"
 * failure mode (voice-trace-report.md hypotheses 1-3), the ERROR_SERVER
 * infinite-retry latent bug (see VoiceSessionPolicy's NETWORK_CLASS_ERRORS
 * comment), or the ERROR_RECOGNIZER_BUSY infinite-restart case (a device
 * with a stolen recognition slot — see BUSY_CLASS_ERRORS). This class has
 * zero Android/coroutine dependencies — it is exactly what
 * BangluIMEService's listener callbacks decide to do, tested without a real
 * SpeechRecognizer.
 */
class VoiceSessionPolicyTest {

    private val maxFruitless = 3
    private val maxBusy = 2

    private fun onError(
        error: Int,
        offlineRetryUsed: Boolean = false,
        fruitlessRestarts: Int = 0,
        busyRestarts: Int = 0
    ) = VoiceSessionPolicy.onError(
        error = error,
        offlineRetryUsed = offlineRetryUsed,
        fruitlessRestarts = fruitlessRestarts,
        maxFruitlessRestarts = maxFruitless,
        busyRestarts = busyRestarts,
        maxBusyRestarts = maxBusy
    )

    @Test
    fun `watchdog timeout with no callback shows watchdog message`() {
        val action = VoiceSessionPolicy.onWatchdogTimeout()
        assertEquals(
            VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.WATCHDOG_TIMEOUT),
            action
        )
    }

    @Test
    fun `ERROR_NETWORK retries offline exactly once, not twice`() {
        val first = onError(error = VoiceSessionPolicy.ERROR_NETWORK, offlineRetryUsed = false)
        assertEquals(VoiceSessionPolicy.VoiceAction.RetryOffline, first)

        // Second network-class failure in the SAME session (offlineRetryUsed
        // now true, as the caller would set after acting on `first`) must be
        // terminal, not another retry — otherwise a flaky network loops the
        // listening chip forever.
        val second = onError(error = VoiceSessionPolicy.ERROR_NETWORK, offlineRetryUsed = true)
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.ERROR), second)
    }

    @Test
    fun `ERROR_SERVER also retries offline exactly once (closes the old unconditional-retry bug)`() {
        val first = onError(error = VoiceSessionPolicy.ERROR_SERVER, offlineRetryUsed = false)
        assertEquals(VoiceSessionPolicy.VoiceAction.RetryOffline, first)

        val second = onError(error = VoiceSessionPolicy.ERROR_SERVER, offlineRetryUsed = true)
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.ERROR), second)
    }

    @Test
    fun `ERROR_LANGUAGE_NOT_SUPPORTED shows the offline-pack-missing message`() {
        val action = onError(error = VoiceSessionPolicy.ERROR_LANGUAGE_NOT_SUPPORTED, offlineRetryUsed = true)
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.OFFLINE_PACK_MISSING), action)
    }

    @Test
    fun `ERROR_LANGUAGE_UNAVAILABLE also shows the offline-pack-missing message`() {
        val action = onError(error = VoiceSessionPolicy.ERROR_LANGUAGE_UNAVAILABLE, offlineRetryUsed = false)
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.OFFLINE_PACK_MISSING), action)
    }

    @Test
    fun `RECOGNIZER_BUSY destroys and recreates once, no message shown`() {
        val action = onError(error = VoiceSessionPolicy.ERROR_RECOGNIZER_BUSY, busyRestarts = 0)
        assertEquals(VoiceSessionPolicy.VoiceAction.RestartSameMode, action)
    }

    @Test
    fun `ERROR_CLIENT also restarts in the same mode`() {
        val action = onError(error = VoiceSessionPolicy.ERROR_CLIENT, busyRestarts = 0)
        assertEquals(VoiceSessionPolicy.VoiceAction.RestartSameMode, action)
    }

    @Test
    fun `second consecutive RECOGNIZER_BUSY still restarts (under the cap of 2)`() {
        // One busy cycle already counted; this is the second — still under
        // maxBusy=2, so it must still restart, not give up yet.
        val action = onError(error = VoiceSessionPolicy.ERROR_RECOGNIZER_BUSY, busyRestarts = 1)
        assertEquals(VoiceSessionPolicy.VoiceAction.RestartSameMode, action)
    }

    @Test
    fun `third consecutive RECOGNIZER_BUSY gives up with an actionable message`() {
        // Two busy cycles already counted (the cap); this third one must stop
        // the destroy+recreate loop instead of restarting forever — the
        // stolen-recognition-slot device never clears this on its own.
        val action = onError(error = VoiceSessionPolicy.ERROR_RECOGNIZER_BUSY, busyRestarts = maxBusy)
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.BUSY_GIVEUP), action)
    }

    @Test
    fun `SPEECH_TIMEOUT restarts while under the fruitless cap`() {
        val action = onError(error = VoiceSessionPolicy.ERROR_SPEECH_TIMEOUT, fruitlessRestarts = 0)
        assertEquals(VoiceSessionPolicy.VoiceAction.RestartSameMode, action)
    }

    @Test
    fun `SPEECH_TIMEOUT ends gracefully once the fruitless cap is reached`() {
        // Two silent cycles already counted; this third one hits the cap of 3.
        val action = onError(error = VoiceSessionPolicy.ERROR_SPEECH_TIMEOUT, fruitlessRestarts = maxFruitless - 1)
        assertEquals(VoiceSessionPolicy.VoiceAction.GracefulStop, action)
    }

    @Test
    fun `NO_MATCH follows the same fruitless-cap ladder as SPEECH_TIMEOUT`() {
        val underCap = onError(error = VoiceSessionPolicy.ERROR_NO_MATCH, fruitlessRestarts = 0)
        assertEquals(VoiceSessionPolicy.VoiceAction.RestartSameMode, underCap)

        val atCap = onError(error = VoiceSessionPolicy.ERROR_NO_MATCH, fruitlessRestarts = maxFruitless - 1)
        assertEquals(VoiceSessionPolicy.VoiceAction.GracefulStop, atCap)
    }

    @Test
    fun `ERROR_INSUFFICIENT_PERMISSIONS surfaces the permission-required state`() {
        val action = onError(error = VoiceSessionPolicy.ERROR_INSUFFICIENT_PERMISSIONS)
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.PERMISSION_REQUIRED), action)
    }

    @Test
    fun `unclassified error codes never fall through silently`() {
        // ERROR_AUDIO (3) has no dedicated branch — must still resolve to a
        // visible terminal state, never nothing (the audit's "no swallowed
        // codes" requirement).
        val action = onError(error = VoiceSessionPolicy.ERROR_AUDIO)
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.ERROR), action)
    }
}
