package com.banglu.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * S107: pins the voice insertion re-anchor law (see VoiceAnchorPolicy).
 *
 * Tester report: "type a full message, place the cursor in the middle, start
 * voice typing again — voice typing is not working." Root cause: the voice
 * insertion anchor was captured once per session and every write yanked the
 * caret back to it; a user cursor move during an active dictation session was
 * never observed. The law pinned here: selection updates caused by our own
 * writes are consumed against an expected-position queue; anything else while
 * dictation is active is an intentional user move and re-anchors dictation.
 */
class VoiceAnchorPolicyTest {

    // ── onSelectionChanged ──────────────────────────────────────────────

    @Test
    fun inactiveSessionIgnoresSelectionChanges() {
        val d = VoiceAnchorPolicy.onSelectionChanged(
            dictationActive = false,
            expectedSelections = emptyList(),
            anchor = 20,
            newSelStart = 7,
            newSelEnd = 7,
            liveSegmentActive = true,
        )
        assertEquals(VoiceAnchorPolicy.Decision.Ignore, d)
    }

    @Test
    fun ownWriteSelectionConsumedFromExpectedQueue() {
        val d = VoiceAnchorPolicy.onSelectionChanged(
            dictationActive = true,
            expectedSelections = listOf(5, 9),
            anchor = 9,
            newSelStart = 5,
            newSelEnd = 5,
            liveSegmentActive = true,
        )
        assertEquals(VoiceAnchorPolicy.Decision.ConsumeExpected(1), d)
    }

    @Test
    fun coalescedHostUpdateConsumesThroughTheMatch() {
        // Hosts batch-edit and only report the final caret — the earlier
        // expected positions must be dropped together with the match.
        val d = VoiceAnchorPolicy.onSelectionChanged(
            dictationActive = true,
            expectedSelections = listOf(5, 9, 12),
            anchor = 12,
            newSelStart = 12,
            newSelEnd = 12,
            liveSegmentActive = true,
        )
        assertEquals(VoiceAnchorPolicy.Decision.ConsumeExpected(3), d)
    }

    @Test
    fun userCursorTapReanchorsDictation() {
        val d = VoiceAnchorPolicy.onSelectionChanged(
            dictationActive = true,
            expectedSelections = emptyList(),
            anchor = 20,
            newSelStart = 7,
            newSelEnd = 7,
            liveSegmentActive = true,
        )
        assertEquals(VoiceAnchorPolicy.Decision.Reanchor(7), d)
    }

    @Test
    fun selectionAlreadyAtAnchorIgnored() {
        val d = VoiceAnchorPolicy.onSelectionChanged(
            dictationActive = true,
            expectedSelections = emptyList(),
            anchor = 20,
            newSelStart = 20,
            newSelEnd = 20,
            liveSegmentActive = true,
        )
        assertEquals(VoiceAnchorPolicy.Decision.Ignore, d)
    }

    @Test
    fun rangeSelectionReanchorsToSelectionEnd() {
        val d = VoiceAnchorPolicy.onSelectionChanged(
            dictationActive = true,
            expectedSelections = emptyList(),
            anchor = 20,
            newSelStart = 3,
            newSelEnd = 7,
            liveSegmentActive = true,
        )
        assertEquals(VoiceAnchorPolicy.Decision.Reanchor(7), d)
    }

    @Test
    fun unknownSelectionIgnored() {
        val d = VoiceAnchorPolicy.onSelectionChanged(
            dictationActive = true,
            expectedSelections = emptyList(),
            anchor = 20,
            newSelStart = -1,
            newSelEnd = -1,
            liveSegmentActive = true,
        )
        assertEquals(VoiceAnchorPolicy.Decision.Ignore, d)
    }

    @Test
    fun expectedMatchWinsOverAnchorEquality() {
        // The final write of a commit sequence lands ON the updated anchor —
        // it must consume its queue entry, not leak it via the anchor branch.
        val d = VoiceAnchorPolicy.onSelectionChanged(
            dictationActive = true,
            expectedSelections = listOf(20),
            anchor = 20,
            newSelStart = 20,
            newSelEnd = 20,
            liveSegmentActive = true,
        )
        assertEquals(VoiceAnchorPolicy.Decision.ConsumeExpected(1), d)
    }

    @Test
    fun nullAnchorAdoptsHostSelectionBetweenSegments() {
        // Hosts where getExtractedText returns null never produced an anchor;
        // between segments onUpdateSelection's absolute position is the
        // recovery path that finally gives dictation an anchor.
        val d = VoiceAnchorPolicy.onSelectionChanged(
            dictationActive = true,
            expectedSelections = emptyList(),
            anchor = null,
            newSelStart = 4,
            newSelEnd = 4,
            liveSegmentActive = false,
        )
        assertEquals(VoiceAnchorPolicy.Decision.Reanchor(4), d)
    }

    @Test
    fun nullAnchorMidSegmentStaysConservative() {
        // With no anchor we cannot tell our own writes' selection reports
        // from a user move — resetting the live segment on our own report
        // would duplicate the partial. Legacy caret-following behavior wins.
        val d = VoiceAnchorPolicy.onSelectionChanged(
            dictationActive = true,
            expectedSelections = emptyList(),
            anchor = null,
            newSelStart = 4,
            newSelEnd = 4,
            liveSegmentActive = true,
        )
        assertEquals(VoiceAnchorPolicy.Decision.Ignore, d)
    }

    // ── punctuationForInsertion ─────────────────────────────────────────

    @Test
    fun dariSuppressedWhenTextFollowsInsertionPoint() {
        assertEquals(" ", VoiceAnchorPolicy.punctuationForInsertion("।", hasTextAfterInsertion = true))
    }

    @Test
    fun commaSuppressedWhenTextFollowsInsertionPoint() {
        assertEquals(" ", VoiceAnchorPolicy.punctuationForInsertion(",", hasTextAfterInsertion = true))
    }

    @Test
    fun dariKeptAtEndOfText() {
        assertEquals("।", VoiceAnchorPolicy.punctuationForInsertion("।", hasTextAfterInsertion = false))
    }

    @Test
    fun plainSpaceUnaffectedMidText() {
        assertEquals(" ", VoiceAnchorPolicy.punctuationForInsertion(" ", hasTextAfterInsertion = true))
    }

    // ── cumulativeCommittedPrefix ───────────────────────────────────────

    @Test
    fun firstReanchorRecordsCurrentPartialAsCommittedPrefix() {
        assertEquals("আমি ভাত", VoiceAnchorPolicy.cumulativeCommittedPrefix(null, "আমি ভাত"))
    }

    @Test
    fun reanchorAfterPauseCommitAccumulatesBothPrefixes() {
        // A pause commit already stripped "আমি ভাত"; the live partial since
        // then is "খাই". Raw hypotheses keep the full transcript, so the
        // stored prefix must be the concatenation or stripping breaks and the
        // already-committed words duplicate at the new anchor.
        assertEquals(
            "আমি ভাত খাই",
            VoiceAnchorPolicy.cumulativeCommittedPrefix("আমি ভাত", "খাই"),
        )
    }

    @Test
    fun reanchorWithNoLivePartialKeepsExistingPrefix() {
        assertEquals("আমি ভাত", VoiceAnchorPolicy.cumulativeCommittedPrefix("আমি ভাত", "   "))
    }

    @Test
    fun reanchorWithNothingSpokenYieldsNoPrefix() {
        assertNull(VoiceAnchorPolicy.cumulativeCommittedPrefix(null, ""))
    }
}
