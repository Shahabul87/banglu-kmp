package com.banglu.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S55 (F-ANDROID-006): pins the voice error-ladder decision table so a future
 * change can't silently reintroduce the "listening chip that never delivers"
 * failure mode (voice-trace-report.md hypotheses 1-3) or the ERROR_SERVER
 * infinite-retry latent bug (see VoiceSessionPolicy's NETWORK_CLASS_ERRORS
 * comment). This class has zero Android/coroutine dependencies — it is
 * exactly what BangluIMEService's listener callbacks decide to do, tested
 * without a real SpeechRecognizer.
 */
class VoiceSessionPolicyTest {

    private val maxFruitless = 3

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
        val first = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_NETWORK,
            offlineRetryUsed = false,
            fruitlessRestarts = 0,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.RetryOffline, first)

        // Second network-class failure in the SAME session (offlineRetryUsed
        // now true, as the caller would set after acting on `first`) must be
        // terminal, not another retry — otherwise a flaky network loops the
        // listening chip forever.
        val second = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_NETWORK,
            offlineRetryUsed = true,
            fruitlessRestarts = 0,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.ERROR), second)
    }

    @Test
    fun `ERROR_SERVER also retries offline exactly once (closes the old unconditional-retry bug)`() {
        val first = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_SERVER,
            offlineRetryUsed = false,
            fruitlessRestarts = 0,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.RetryOffline, first)

        val second = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_SERVER,
            offlineRetryUsed = true,
            fruitlessRestarts = 0,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.ERROR), second)
    }

    @Test
    fun `ERROR_LANGUAGE_NOT_SUPPORTED shows the offline-pack-missing message`() {
        val action = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_LANGUAGE_NOT_SUPPORTED,
            offlineRetryUsed = true,
            fruitlessRestarts = 0,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.OFFLINE_PACK_MISSING), action)
    }

    @Test
    fun `ERROR_LANGUAGE_UNAVAILABLE also shows the offline-pack-missing message`() {
        val action = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_LANGUAGE_UNAVAILABLE,
            offlineRetryUsed = false,
            fruitlessRestarts = 0,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.OFFLINE_PACK_MISSING), action)
    }

    @Test
    fun `RECOGNIZER_BUSY destroys and recreates once, no message shown`() {
        val action = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_RECOGNIZER_BUSY,
            offlineRetryUsed = false,
            fruitlessRestarts = 0,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.RestartSameMode, action)
    }

    @Test
    fun `ERROR_CLIENT also restarts in the same mode`() {
        val action = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_CLIENT,
            offlineRetryUsed = false,
            fruitlessRestarts = 0,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.RestartSameMode, action)
    }

    @Test
    fun `SPEECH_TIMEOUT restarts while under the fruitless cap`() {
        val action = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_SPEECH_TIMEOUT,
            offlineRetryUsed = false,
            fruitlessRestarts = 0,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.RestartSameMode, action)
    }

    @Test
    fun `SPEECH_TIMEOUT ends gracefully once the fruitless cap is reached`() {
        // Two silent cycles already counted; this third one hits the cap of 3.
        val action = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_SPEECH_TIMEOUT,
            offlineRetryUsed = false,
            fruitlessRestarts = maxFruitless - 1,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.GracefulStop, action)
    }

    @Test
    fun `NO_MATCH follows the same fruitless-cap ladder as SPEECH_TIMEOUT`() {
        val underCap = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_NO_MATCH,
            offlineRetryUsed = false,
            fruitlessRestarts = 0,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.RestartSameMode, underCap)

        val atCap = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_NO_MATCH,
            offlineRetryUsed = false,
            fruitlessRestarts = maxFruitless - 1,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.GracefulStop, atCap)
    }

    @Test
    fun `ERROR_INSUFFICIENT_PERMISSIONS surfaces the permission-required state`() {
        val action = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_INSUFFICIENT_PERMISSIONS,
            offlineRetryUsed = false,
            fruitlessRestarts = 0,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.PERMISSION_REQUIRED), action)
    }

    @Test
    fun `unclassified error codes never fall through silently`() {
        // ERROR_AUDIO (3) has no dedicated branch — must still resolve to a
        // visible terminal state, never nothing (the audit's "no swallowed
        // codes" requirement).
        val action = VoiceSessionPolicy.onError(
            error = VoiceSessionPolicy.ERROR_AUDIO,
            offlineRetryUsed = false,
            fruitlessRestarts = 0,
            maxFruitlessRestarts = maxFruitless
        )
        assertEquals(VoiceSessionPolicy.VoiceAction.ShowMessage(VoiceInputState.ERROR), action)
    }
}
