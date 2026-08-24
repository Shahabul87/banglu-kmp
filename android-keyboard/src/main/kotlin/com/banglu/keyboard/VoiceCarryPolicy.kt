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
    }

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

    /**
     * Strip everything already committed out of a recognizer hypothesis.
     * Returns the text still owed to the editor ("" when the hypothesis is
     * entirely covered by prior commits).
     */
    fun strip(text: String): String {
        var clean = text.trim()
        if (clean.isEmpty()) return clean

        // 1. Cross-restart overlap (probationary). S133: WORD-based with
        //    fuzzy equality — the re-heard utterance comes back with revised
        //    spellings ("অনেক" → "অ১নেক"), and the old exact startsWith test
        //    died on one changed character, re-appending the whole committed
        //    transcript once per error restart (field report round three of
        //    this bug). A probationary carry the hypothesis does not overlap
        //    is still genuinely new speech (the S56 contract) — the carry
        //    dies immediately so it can never mis-strip a later sentence
        //    starting with the same words.
        if (restartCarry.isNotEmpty()) {
            val carryWords = restartCarry.split(whitespace)
            val hypWords = clean.split(whitespace)
            var matched = 0
            while (matched < carryWords.size && matched < hypWords.size &&
                VoiceWordMatch.similar(carryWords[matched], hypWords[matched])
            ) {
                matched++
            }
            clean = when {
                // Fully covered (equal or a late, shorter re-hear): stale —
                // re-appending it would duplicate committed text. An exact-
                // length match confirms the overlap and ends probation.
                matched == hypWords.size -> {
                    if (matched == carryWords.size) probation = false
                    ""
                }
                // Carry fully covered with words to spare: the overlap is
                // confirmed; only the extra words are owed.
                matched == carryWords.size -> {
                    probation = false
                    hypWords.drop(matched).joinToString(" ")
                }
                else -> {
                    if (probation) {
                        probation = false
                        restartCarry = ""
                    }
                    clean
                }
            }
            if (clean.isEmpty()) return ""
        }

        // 2. Within-session strip — WORD COUNT, not text (S121): the session
        //    transcript is cumulative, so the first N words are the already-
        //    committed audio in whatever spelling the recognizer currently
        //    prefers. A hypothesis no longer than the committed count owes
        //    nothing (stale or pure revision of committed words).
        val committedWords = wordCount(sessionCommitted)
        if (committedWords == 0) return clean
        val words = clean.split(whitespace)
        return if (words.size > committedWords) {
            words.drop(committedWords).joinToString(" ")
        } else {
            ""
        }
    }
}
