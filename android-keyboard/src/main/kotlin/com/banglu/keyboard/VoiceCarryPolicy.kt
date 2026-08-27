package com.banglu.keyboard

/**
 * S120/S121 — the auto-committed transcript carry (tester reports: "voice
 * typing is very unstable, words are repeated", then after S120 "still
 * repeated for long time", with an on-device trace showing the smoking gun:
 * "অনেক অ১নেক অনেক রাত অনেক রাত হয়ে অনেক রাত হয়ে গেছে…" — the recognizer
 * REVISED an already-committed word ("অনেক" → "অ১নেক") between hypotheses).
 *
 * The recognizer's partial hypotheses grow over the WHOLE utterance while
 * the IME commits pieces of it early (pause commits, error-restart
 * commits). This class owns the reconciliation. Three historical bugs:
 *
 * 1. Replace-semantics carry (pre-S120): each commit REPLACED the carry —
 *    the second mid-utterance commit broke the prefix test and every later
 *    hypothesis re-appended in full.
 * 2. Unconditional reset on restart (S56): flaky-network error restarts
 *    committed the live partial, forgot it, and the re-heard speech
 *    duplicated once per error cycle.
 * 3. TEXTUAL prefix matching (S120's own gap): long dictation gets
 *    constantly revised — one changed character in an already-committed
 *    word broke the exact-prefix match and the whole transcript
 *    re-appended. S121: WITHIN a session the strip is WORD-COUNT based —
 *    the transcript is cumulative by construction, so the first N
 *    hypothesis words correspond to the N committed words regardless of
 *    how their spelling was revised. Committed text stays as committed
 *    (the same "data preservation beats transcript fidelity" law the live
 *    renderer applies); only genuinely NEW words are owed. Text matching
 *    survives solely at the cross-error-restart boundary, where the new
 *    session's audio is different and no count alignment exists —
 *    probationary, as in S120.
 *
 * Pure state machine — no Android imports — unit-pinned in
 * VoiceCarryPolicyTest, same architecture as VoiceSessionPolicy.
 */
class VoiceCarryPolicy {

    /** Committed text originating from the CURRENT recognizer session's
     *  transcript. Its WORD COUNT is the strip law within the session. */
    private var sessionCommitted: String = ""

    /** Committed text of interrupted utterance(s) carried across an error
     *  restart — textual overlap heuristics only, under probation. */
    private var restartCarry: String = ""
    private var probation: Boolean = false

    private val whitespace = Regex("\\s+")

    private fun wordCount(s: String): Int =
        if (s.isEmpty()) 0 else s.trim().split(whitespace).size

    /** A fresh user-initiated session or a cleanly-closed transcript (final
     *  result delivered): nothing left to strip. */
    fun closeTranscript() {
        sessionCommitted = ""
        restartCarry = ""
        probation = false
        lastHypothesis = emptyList()
    }

    /** S137: the previous non-empty raw hypothesis of this transcript —
     *  a recognizer restart shows up here even when nothing was committed. */
    private var lastHypothesis: List<String> = emptyList()

    /** A commit of [segment] from the CURRENT session's transcript (pause
     *  commit, error-restart commit, or S107 re-anchor closure). Extends the
     *  cumulative session prefix — never replaces it. */
    fun append(segment: String) {
        val clean = segment.trim()
        if (clean.isEmpty()) return
        sessionCommitted =
            if (sessionCommitted.isEmpty()) clean else "$sessionCommitted $clean"
    }

    /** An error restart is about to re-hear the interrupted utterance: fold
     *  the session's committed text into the textual carry (chains across
     *  repeated errors) and start the next session's count at zero. */
    fun armProbation() {
        restartCarry = listOf(restartCarry, sessionCommitted)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        sessionCommitted = ""
        probation = restartCarry.isNotEmpty()
    }

    /** Result of reconciling one recognizer hypothesis against the carry. */
    data class Reconciled(val owed: String, val recognizerReset: Boolean)

    /** Old contract kept for existing callers/tests: just the owed text. */
    fun strip(text: String): String = reconcile(text).owed

    /**
     * S137 (field trace, Google speech service 2026-07): reconcile a
     * hypothesis with everything committed so far in this transcript.
     *
     * The recognizer does NOT keep one cumulative hypothesis per session —
     * after ~3s of silence it finalizes the utterance internally and starts
     * a FRESH hypothesis while the session stays open. The old law stripped
     * the first N committed words from every hypothesis blindly and ate the
     * whole start of each new segment ("not picking up after a pause").
     *
     * Law (fuzzy word matching throughout, [VoiceWordMatch]):
     *  - hypothesis fully covered by the committed words → nothing owed
     *    (stale / shorter interim);
     *  - committed words are a prefix of the hypothesis → only the tail is
     *    owed (cumulative continuation, S121);
     *  - NO common prefix → recognizer RESET: the carry closes, the whole
     *    hypothesis is owed, and [Reconciled.recognizerReset] tells the
     *    caller to close its previous live segment first;
     *  - divergence after a partial prefix: a hypothesis at most half as
     *    long as the committed text (or any probationary post-error carry)
     *    is a fresh segment → RESET; otherwise it is a mid-transcript
     *    revision (S121) → only words beyond the committed count are owed.
     */
    /**
     * @param speechRestarted the recognizer reported a new onBeginningOfSpeech
     *   since the previous hypothesis — with it, a hypothesis that shares no
     *   first word with the previous one is a restart at ANY length (field
     *   trace 17:00:02: 'কোথায়' → new utterance 'হ্যালো', both one word).
     */
    fun reconcile(text: String, speechRestarted: Boolean = false): Reconciled {
        val clean = text.trim()
        if (clean.isEmpty()) return Reconciled("", false)
        val hypWordsRaw = clean.split(whitespace)
        // S137: restart detection against the PREVIOUS hypothesis, committed
        // or not (field trace 16:56:18 — Google finalized the utterance at
        // its end-of-speech and began a new one 0.6s later with nothing
        // committed). A new hypothesis that shares no first word with the
        // previous one AND is shorter is the recognizer starting over — a
        // same-length rewrite of the first word is a revision, not a reset.
        val previous = lastHypothesis
        lastHypothesis = hypWordsRaw
        if (previous.isNotEmpty() &&
            (hypWordsRaw.size < previous.size || speechRestarted) &&
            !VoiceWordMatch.similar(previous[0], hypWordsRaw[0])
        ) {
            closeTranscript()
            lastHypothesis = hypWordsRaw
            return Reconciled(clean, true)
        }
        val carryWords = listOf(restartCarry, sessionCommitted)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .split(whitespace)
            .filter { it.isNotEmpty() }
        if (carryWords.isEmpty()) return Reconciled(clean, false)

        val hypWords = clean.split(whitespace)
        var matched = 0
        while (matched < carryWords.size && matched < hypWords.size &&
            VoiceWordMatch.similar(carryWords[matched], hypWords[matched])
        ) {
            matched++
        }
        return when {
            matched == hypWords.size -> {
                if (matched == carryWords.size) probation = false
                Reconciled("", false)
            }
            matched == carryWords.size -> {
                probation = false
                Reconciled(hypWords.drop(matched).joinToString(" "), false)
            }
            matched == 0 || probation || hypWords.size * 2 <= carryWords.size -> {
                closeTranscript()
                lastHypothesis = hypWordsRaw
                Reconciled(clean, true)
            }
            else -> {
                // Revision inside a long transcript: committed words stay as
                // committed; only genuinely new words are owed.
                Reconciled(
                    if (hypWords.size > carryWords.size) hypWords.drop(carryWords.size).joinToString(" ") else "",
                    false
                )
            }
        }
    }
}
