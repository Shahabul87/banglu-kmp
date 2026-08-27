package com.banglu.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S137 (field trace 2026-08-26, SM-S901W, Google speech service 20260720):
 * the recognizer does NOT keep one cumulative hypothesis per session. After
 * ~3s of silence it finalizes the utterance internally and starts a FRESH
 * hypothesis while the session stays open. The old word-count strip assumed
 * cumulative hypotheses and ate the first N words of every new segment:
 *
 *   pause commit 'ভয়েস টাইপিং ঠিকমতো ... করতেছে না' (14 words)
 *   raw partial 'বারবার'                      -> owed ''   (dropped)
 *   raw partial 'বারবার রিপিট ওয়ার্ড পিক'      -> owed ''   (dropped)
 *   ...twelve partials later...
 *   raw partial '... ভয়েস টাইপিং হ্যালো'      -> owed 'টাইপিং হ্যালো'  (garbage tail)
 *
 * New law: overlap is decided by fuzzy word matching, a hypothesis that
 * shares no prefix with the committed words (or is far shorter than them
 * while diverging) is a recognizer RESET — the carry closes and the whole
 * hypothesis is owed — and the caller is told, so it can close the previous
 * live segment instead of diffing the new segment against it (the growing-
 * repeat pattern in the WhatsApp screenshot).
 */
class S137RecognizerResetTest {

    private val committed = "ভয়েস টাইপিং ঠিকমতো ওয়ার্ড পিক করতেছে না ভয়েস টাইপিং ঠিকঠাক ওয়ার্ড পিক করতেছে না"

    @Test
    fun freshHypothesisAfterPauseCommitIsOwedInFullAndFlagsReset() {
        val c = VoiceCarryPolicy()
        c.append(committed)
        val first = c.reconcile("বারবার")
        assertTrue(first.recognizerReset, "a hypothesis sharing no prefix with 14 committed words is a reset")
        assertEquals("বারবার", first.owed)
        // The carry is closed now: the new segment grows normally.
        val second = c.reconcile("বারবার রিপিট ওয়ার্ড পিক")
        assertFalse(second.recognizerReset)
        assertEquals("বারবার রিপিট ওয়ার্ড পিক", second.owed)
    }

    @Test
    fun cumulativeContinuationStillOwesOnlyTheTail() {
        val c = VoiceCarryPolicy()
        c.append("আমি ভালো আছি")
        val r = c.reconcile("আমি ভালো আছি তুমি কেমন")
        assertFalse(r.recognizerReset)
        assertEquals("তুমি কেমন", r.owed)
    }

    @Test
    fun respelledCommittedWordsAreStillAContinuation() {
        val c = VoiceCarryPolicy()
        c.append("অনেক রাত হয়ে গেছে")
        val r = c.reconcile("অ১নেক রাত হয়ে গেছে এখন")
        assertFalse(r.recognizerReset)
        assertEquals("এখন", r.owed)
    }

    @Test
    fun shorterInterimCoveredByCommittedTextOwesNothing() {
        val c = VoiceCarryPolicy()
        c.append(committed)
        val r = c.reconcile("ভয়েস টাইপিং ঠিকমতো")
        assertFalse(r.recognizerReset)
        assertEquals("", r.owed)
    }

    @Test
    fun newSegmentStartingWithTheSameWordButMuchShorterIsAReset() {
        val c = VoiceCarryPolicy()
        c.append(committed) // 14 words
        val r = c.reconcile("ভয়েস কল আসছে") // shares 'ভয়েস', then diverges, 3 words
        assertTrue(r.recognizerReset)
        assertEquals("ভয়েস কল আসছে", r.owed)
    }

    @Test
    fun midHypothesisRevisionOfALongTranscriptIsNotAReset() {
        // S121's case: the recognizer rewrites a committed word beyond fuzzy
        // tolerance while the hypothesis is still the whole transcript.
        val c = VoiceCarryPolicy()
        c.append("আমি আজ সকালে বাজারে গিয়ে অনেক কিছু কিনেছি")
        val r = c.reconcile("আমি আজ সকালে বাজারে যেয়ে অনেক কিছু কিনেছি তারপর")
        assertFalse(r.recognizerReset)
        assertEquals("তারপর", r.owed)
    }

    @Test
    fun errorRestartCarryStillDiesOnGenuinelyNewSpeech() {
        val c = VoiceCarryPolicy()
        c.append("ভালোবাসো তুমি")
        c.armProbation()
        val r = c.reconcile("কাল দেখা হবে")
        assertTrue(r.recognizerReset)
        assertEquals("কাল দেখা হবে", r.owed)
        assertEquals("আবার", c.reconcile("আবার").owed)
    }

    @Test
    fun freshHypothesisWithNothingCommittedIsAResetWhenItShrinks() {
        // Field trace 16:56:18: Google finalized 'আচ্ছা এটা কি স্লো কাজ করতেছে'
        // at its end-of-speech and started 'এটা', 'এটা স্লো', ... 0.6s later —
        // nothing had been committed yet, so the old check never ran and the
        // new words were appended after the old sentence on every partial.
        val c = VoiceCarryPolicy()
        assertFalse(c.reconcile("আচ্ছা এটা কি স্লো কাজ করতেছে").recognizerReset)
        val r = c.reconcile("এটা")
        assertTrue(r.recognizerReset, "shorter hypothesis with no shared prefix = recognizer restarted")
        assertEquals("এটা", r.owed)
        val grow = c.reconcile("এটা স্লো যদি")
        assertFalse(grow.recognizerReset)
        assertEquals("এটা স্লো যদি", grow.owed)
    }

    @Test
    fun firstWordRevisionOfTheSameLengthIsNotAReset() {
        val c = VoiceCarryPolicy()
        c.reconcile("আমি ভালো আছি")
        val r = c.reconcile("আমরা ভালো আছি")
        assertFalse(r.recognizerReset, "same-length hypothesis with a rewritten first word is a revision")
    }

    @Test
    fun sameLengthNewUtteranceAfterANewBeginningIsAReset() {
        // Field trace 17:00:02: Google caught only 'কোথায়' of one utterance,
        // then a fresh 'হ্যালো' after onBeginningOfSpeech — one word each.
        val c = VoiceCarryPolicy()
        c.reconcile("কোথায়")
        assertFalse(c.reconcile("কোথাও").recognizerReset, "a respelling is a revision")
        val r = c.reconcile("হ্যালো", speechRestarted = true)
        assertTrue(r.recognizerReset)
        assertEquals("হ্যালো", r.owed)
    }

    @Test
    fun closingTheTranscriptForgetsTheLastHypothesis() {
        val c = VoiceCarryPolicy()
        c.reconcile("আমি ভালো আছি")
        c.closeTranscript()
        assertFalse(c.reconcile("তুমি").recognizerReset, "a new session's first word is not a reset")
    }

    @Test
    fun stripKeepsItsOldContractForExistingCallers() {
        val c = VoiceCarryPolicy()
        c.append("আমি ভালো")
        assertEquals("", c.strip("আমি"))
        assertEquals("আছি", c.strip("আমি ভালো আছি"))
    }
}
