package com.banglu.keyboard

/**
 * S120 — the auto-committed transcript carry (tester report: "voice typing
 * is very unstable, words are repeated", with a WhatsApp composer showing
 * "ভালোবাসো | ভালোবাসো তুমি | ভালোবাসো তুমি | ভালোবাসো তুমি মোরে…").
 *
 * The recognizer's partial hypotheses grow over the WHOLE utterance, while
 * the IME commits pieces of it early (pause commits, error-restart commits).
 * This class owns the ONE piece of state that reconciles the two — the
 * cumulative committed prefix of the current utterance — and the rules for
 * stripping it back out of later hypotheses. Two historical bugs live here:
 *
 * 1. Replace-semantics carry (pre-S120): each commit REPLACED the carry, so
 *    after a second mid-utterance commit the prefix test failed and every
 *    later partial (and the final) re-appended in full.
 * 2. Unconditional reset on restart (S56): every error restart forgot the
 *    carry, so a flaky network committed the live partial once per error
 *    cycle while the re-heard speech stamped the same words again. The S56
 *    fear (a fresh session's genuinely-new speech beginning with the same
 *    words must not be swallowed) is honored via PROBATION: after an error
 *    restart the carry survives exactly until the first non-overlapping
 *    hypothesis, which kills it.
 *
 * Pure state machine — no Android imports — so the dedup law is unit-pinned
 * (VoiceCarryPolicyTest), same architecture as VoiceSessionPolicy.
 */
class VoiceCarryPolicy {

    private var carry: String = ""
    private var probation: Boolean = false

    val current: String get() = carry

    /** A fresh user-initiated session or a cleanly-closed transcript (final
     *  result delivered): nothing left to strip. */
    fun closeTranscript() {
        carry = ""
        probation = false
    }

    /** A commit of [segment] from the CURRENT utterance (pause commit or
     *  error-restart commit). Extends the cumulative prefix — never
     *  replaces it. */
    fun append(segment: String) {
        val clean = segment.trim()
        if (clean.isEmpty()) return
        carry = if (carry.isEmpty()) clean else "$carry $clean"
    }

    /** An error restart is about to re-hear the interrupted utterance: the
     *  carry survives, probationary. No-op when there is nothing to carry. */
    fun armProbation() {
        probation = carry.isNotEmpty()
    }

    /**
     * Strip the already-committed prefix out of a recognizer hypothesis.
     * Returns the text still owed to the editor ("" when the hypothesis is
     * entirely stale). A probationary carry that the hypothesis does not
     * overlap is genuinely new speech — the carry dies immediately so it
     * can never mis-strip a later sentence that happens to begin with the
     * same words.
     */
    fun strip(text: String): String {
        val clean = text.trim()
        if (carry.isEmpty()) return clean
        if (clean == carry) {
            probation = false
            return ""
        }
        // A late, SHORTER hypothesis of already-committed speech
        // (recognizers re-emit trimmed interims around a pause) is stale —
        // re-appending it would duplicate text the commit already wrote.
        if (carry.startsWith(clean)) return ""
        if (clean.startsWith("$carry ")) {
            probation = false
            return clean.removePrefix(carry).trimStart()
        }
        if (probation) {
            probation = false
            carry = ""
        }
        return clean
    }
}
