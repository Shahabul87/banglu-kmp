package com.banglu.keyboard

/**
 * S107 (tester: "type a full message, place the cursor in the middle, start
 * voice typing again — voice typing is not working"): pure decision table for
 * how the voice insertion anchor reacts to editor selection changes
 * (VoiceSessionPolicy / VoicePartialDiff pattern, unit-pinned in
 * VoiceAnchorPolicyTest).
 *
 * The old model captured the anchor ONCE per onVoiceInput() and yanked the
 * caret back to it on every write; a user cursor move during an active
 * continuous-dictation session was invisible, so speech kept landing at the
 * stale end-of-message anchor. New law:
 *  - selection updates our own writes produced are consumed against an
 *    expected-position queue (hosts may coalesce several writes into one
 *    report — a match drops everything up to and including itself);
 *  - a collapsed selection already at the anchor is a no-op;
 *  - anything else while dictation is active is an intentional user move and
 *    RE-ANCHORS dictation at the new position (selection ranges anchor at
 *    their end, matching how onVoiceInput reads selectionEnd).
 */
object VoiceAnchorPolicy {

    sealed interface Decision {
        data object Ignore : Decision
        data class ConsumeExpected(val dropCount: Int) : Decision
        data class Reanchor(val position: Int) : Decision
    }

    fun onSelectionChanged(
        dictationActive: Boolean,
        expectedSelections: List<Int>,
        anchor: Int?,
        newSelStart: Int,
        newSelEnd: Int,
        liveSegmentActive: Boolean,
    ): Decision {
        if (!dictationActive) return Decision.Ignore
        if (newSelStart < 0 || newSelEnd < 0) return Decision.Ignore
        val matchIndex = expectedSelections.indexOf(newSelEnd)
        if (matchIndex >= 0) return Decision.ConsumeExpected(matchIndex + 1)
        if (newSelStart == newSelEnd && anchor != null && newSelEnd == anchor) {
            return Decision.Ignore
        }
        // No anchor (host without getExtractedText) mid-segment: our own
        // writes' selection reports are indistinguishable from a user move —
        // resetting the live segment on our own report would duplicate it, so
        // legacy caret-following behavior wins until the segment closes.
        if (anchor == null && liveSegmentActive) return Decision.Ignore
        return Decision.Reanchor(newSelEnd)
    }

    /** Sentence punctuation (দাঁড়ি/comma) is an end-of-message affordance —
     *  stamped into the middle of an existing sentence it corrupts it. When
     *  non-whitespace text follows the insertion point, every pause mark
     *  degrades to the plain word boundary. */
    fun punctuationForInsertion(mark: String, hasTextAfterInsertion: Boolean): String {
        return if (hasTextAfterInsertion && mark != " ") " " else mark
    }

    /** On re-anchor the current segment's on-screen words stay at the OLD
     *  anchor, but the recognizer's raw hypotheses keep the full transcript —
     *  the stored auto-committed prefix must accumulate (previously stripped
     *  prefix + live partial) or stripAutoCommittedVoicePrefix re-inserts the
     *  already-visible words at the new anchor. */
    fun cumulativeCommittedPrefix(alreadyCommitted: String?, currentPartial: String): String? {
        val already = alreadyCommitted?.trim().orEmpty()
        val partial = currentPartial.trim()
        val joined = listOf(already, partial).filter { it.isNotEmpty() }.joinToString(" ")
        return joined.ifEmpty { null }
    }
}
