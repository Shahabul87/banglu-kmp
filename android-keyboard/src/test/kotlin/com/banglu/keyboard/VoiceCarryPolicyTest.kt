package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S120 pins — the voice dedup law (tester: "voice typing is very unstable,
 * words are repeated"). The two historical duplication mechanisms are
 * replayed exactly as they happened on device.
 */
class VoiceCarryPolicyTest {

    // ── mechanism 1: within-utterance double pause-commit ────────────────

    @Test
    fun cumulativeCarrySurvivesTwoPauseCommits() {
        val c = VoiceCarryPolicy()
        // partial 1 rendered, pause commit
        assertEquals("ভালোবাসো", c.strip("ভালোবাসো"))
        c.append("ভালোবাসো")
        // partial 2 extends the transcript — only the tail is owed
        assertEquals("তুমি", c.strip("ভালোবাসো তুমি"))
        // SECOND pause commit (the old replace-semantics bug point)
        c.append("তুমি")
        // partial 3: the whole transcript again — must strip to the new tail,
        // never re-append "ভালোবাসো তুমি"
        assertEquals("মোরে", c.strip("ভালোবাসো তুমি মোরে"))
        // final result: same rule
        c.append("মোরে")
        assertEquals("", c.strip("ভালোবাসো তুমি মোরে"))
    }

    @Test
    fun staleShorterHypothesisStaysStale() {
        val c = VoiceCarryPolicy()
        c.append("আমি ভালো")
        assertEquals("", c.strip("আমি"))
        assertEquals("", c.strip("আমি ভালো"))
        assertEquals("আছি", c.strip("আমি ভালো আছি"))
    }

    // ── mechanism 2: error-restart re-hearing (the WhatsApp screenshot) ──

    @Test
    fun errorRestartOverlapStripsInsteadOfDuplicating() {
        val c = VoiceCarryPolicy()
        // session A: partial committed by the error-restart path
        assertEquals("ভালোবাসো", c.strip("ভালোবাসো"))
        c.append("ভালোবাসো")
        c.armProbation()
        // session B re-hears the utterance from the top — the overlap strips
        assertEquals("তুমি", c.strip("ভালোবাসো তুমি"))
        c.append("তুমি")
        c.armProbation()
        // session C again
        assertEquals("মোরে", c.strip("ভালোবাসো তুমি মোরে"))
    }

    @Test
    fun probationDiesOnGenuinelyNewSpeech() {
        val c = VoiceCarryPolicy()
        c.append("ভালোবাসো")
        c.armProbation()
        // the restarted session starts a NEW sentence — no overlap: full text
        // through, carry dead (S56 contract)
        assertEquals("কেমন আছো", c.strip("কেমন আছো"))
        // and a LATER hypothesis that happens to begin with the old words is
        // never mis-stripped
        assertEquals("ভালোবাসো সবাইকে", c.strip("ভালোবাসো সবাইকে"))
    }

    // ── mechanism 3 (S121): recognizer REVISIONS during long dictation ──

    @Test
    fun revisionOfCommittedWordsNeverReappends() {
        // The exact on-device trace (2026-08-19, 11:29 PM): the recognizer
        // revised "অনেক" to "অ১নেক" between hypotheses — S120's textual
        // prefix match broke and the whole transcript re-appended:
        // "অনেক অ১নেক অনেক রাত অনেক রাত হয়ে অনেক রাত হয়ে গেছে…".
        // Word-count stripping is revision-proof.
        val c = VoiceCarryPolicy()
        assertEquals("অনেক", c.strip("অনেক"))
        c.append("অনেক")
        // committed word revised — only the NEW word is owed
        assertEquals("রাত", c.strip("অ১নেক রাত"))
        c.append("রাত")
        assertEquals("হয়ে", c.strip("অনেক রাত হয়ে"))
        c.append("হয়ে")
        assertEquals("গেছে", c.strip("অনেক রাত হয়ে গেছে"))
        c.append("গেছে")
        // final result — fully covered, nothing owed
        assertEquals("", c.strip("অনেক রাত হয়ে গেছে"))
    }

    @Test
    fun zeroOverlapHypothesisIsARecognizerReset() {
        // DECISION (S137, 2026-08-26): this pin is flipped. S121 treated a
        // same-length hypothesis with NO overlap as a pure revision and owed
        // nothing. The field trace on the Google speech service (2026-07)
        // proved that shape is the recognizer starting a FRESH hypothesis
        // after a pause — S121's rule dropped every new segment ("not
        // picking up after I pause"). Mechanism 3 (re-append after a one-
        // character respelling) is prevented by fuzzy word matching, not by
        // this rule, so zero overlap now means RESET: owe the hypothesis in
        // full and close the carry.
        val c = VoiceCarryPolicy()
        c.append("আমি ভালো")
        val r = c.reconcile("অন্য কথা")
        assertEquals(true, r.recognizerReset)
        assertEquals("অন্য কথা", r.owed)
    }

    @Test
    fun closeTranscriptForgetsEverything() {
        val c = VoiceCarryPolicy()
        c.append("ভালোবাসো")
        c.armProbation()
        c.closeTranscript()
        assertEquals("ভালোবাসো", c.strip("ভালোবাসো"))
    }

    @Test
    fun armProbationWithEmptyCarryIsInert() {
        val c = VoiceCarryPolicy()
        c.armProbation()
        assertEquals("যাই হোক", c.strip("যাই হোক"))
    }
}

// ── S133: the restart carry must survive recognizer respelling ───────────
// The within-session strip went word-count-based in S121, but the CROSS-
// RESTART carry stayed exact-text: one revised character in the re-heard
// hypothesis's first word killed the probationary carry and the entire
// committed transcript re-appended — once per error restart.

class S133RestartCarryRevisionTest {

    @Test
    fun aRevisedRehearStripsByFuzzyWordPrefix() {
        val c = VoiceCarryPolicy()
        c.append("অনেক রাত হয়ে গেছে")
        c.armProbation()
        // The new session re-hears the interrupted utterance; the recognizer
        // respells the first word and continues with new speech.
        assertEquals("তাই ঘুমাবো", c.strip("অ১নেক রাত হয়ে গেছে তাই ঘুমাবো"))
    }

    @Test
    fun aRevisedShorterRehearOwesNothing() {
        val c = VoiceCarryPolicy()
        c.append("অনেক রাত হয়ে গেছে")
        c.armProbation()
        assertEquals("", c.strip("অ১নেক রাত হয়ে"))
    }

    @Test
    fun genuinelyNewSpeechStillKillsTheCarry() {
        // The S56 contract survives: divergence = new speech, carry dies so a
        // later sentence starting with the same words is never mis-stripped.
        val c = VoiceCarryPolicy()
        c.append("অনেক রাত")
        c.armProbation()
        assertEquals("এখন সকাল হলো", c.strip("এখন সকাল হলো"))
        assertEquals("অনেক রাত ভালো", c.strip("অনেক রাত ভালো"))
    }
}
