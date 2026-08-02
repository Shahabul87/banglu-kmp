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
        networkRetryUsed: Boolean = false,
        offlineRetryUsed: Boolean = false,
        offlineForcedBySession: Boolean = false,
        fruitlessRestarts: Int = 0,
        busyRestarts: Int = 0
    ) = VoiceSessionPolicy.onError(
        error = error,
        networkRetryUsed = networkRetryUsed,
        offlineRetryUsed = offlineRetryUsed,
        offlineForcedBySession = offlineForcedBySession,
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
    fun `S69 network ladder - plain online retry first, then offline, then terminal`() {
        // 1st network error: plain same-mode (online) retry — a transient
        // server hiccup usually succeeds online, and forcing offline first
        // was a trap on devices with no bn-BD offline pack.
        val first = onError(error = VoiceSessionPolicy.ERROR_NETWORK)
        assertEquals(VoiceSessionPolicy.VoiceAction.RestartSameMode, first)

        // 2nd: now the offline-capable step.
        val second = onError(error = VoiceSessionPolicy.ERROR_NETWORK, networkRetryUsed = true)
        assertEquals(VoiceSessionPolicy.VoiceAction.RetryOffline, second)

        // 3rd: terminal — otherwise a flaky network loops the chip forever.
        val third = onError(
            error = VoiceSessionPolicy.ERROR_NETWORK,
            networkRetryUsed = true,
            offlineRetryUsed = true
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.ERROR), third)
    }

    @Test
    fun `ERROR_SERVER walks the same S69 ladder (closes the old unconditional-retry bug)`() {
        assertEquals(
            VoiceSessionPolicy.VoiceAction.RestartSameMode,
            onError(error = VoiceSessionPolicy.ERROR_SERVER)
        )
        assertEquals(
            VoiceSessionPolicy.VoiceAction.RetryOffline,
            onError(error = VoiceSessionPolicy.ERROR_SERVER, networkRetryUsed = true)
        )
        assertEquals(
            VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.ERROR),
            onError(error = VoiceSessionPolicy.ERROR_SERVER, networkRetryUsed = true, offlineRetryUsed = true)
        )
    }

    @Test
    fun `S69 - pack-missing during LADDER-forced offline walks back online instead of dead-ending`() {
        // The 2026-07-19 on-device audit: server hiccup → forced offline →
        // code 12 (no bn-BD pack) → terminal message, even though online
        // recognition worked. Now: back to online.
        val action = onError(
            error = VoiceSessionPolicy.ERROR_LANGUAGE_NOT_SUPPORTED,
            networkRetryUsed = true,
            offlineRetryUsed = true,
            offlineForcedBySession = true
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.RetryOnline, action)
    }

    @Test
    fun `pack-missing under the USER's offline-first setting stays an honest terminal message`() {
        val action = onError(
            error = VoiceSessionPolicy.ERROR_LANGUAGE_NOT_SUPPORTED,
            offlineForcedBySession = false
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.OFFLINE_PACK_MISSING), action)
    }

    @Test
    fun `ERROR_LANGUAGE_UNAVAILABLE follows the same forced-vs-chosen split`() {
        assertEquals(
            VoiceSessionPolicy.VoiceAction.RetryOnline,
            onError(error = VoiceSessionPolicy.ERROR_LANGUAGE_UNAVAILABLE, offlineForcedBySession = true)
        )
        assertEquals(
            VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.OFFLINE_PACK_MISSING),
            onError(error = VoiceSessionPolicy.ERROR_LANGUAGE_UNAVAILABLE, offlineForcedBySession = false)
        )
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
