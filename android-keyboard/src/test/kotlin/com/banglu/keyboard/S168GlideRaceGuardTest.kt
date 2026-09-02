package com.banglu.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S168 (closed-testing audit P1-2): a glide decode that lands after the user
 * kept typing (or after the editor session changed) must be DROPPED, never
 * committed over the newer text.
 */
class S168GlideRaceGuardTest {

    @Test
    fun unchangedSessionAndBufferStillApplies() {
        assertTrue(GlideCommitPolicy.resultStillApplies(sessionThen = 4, sessionNow = 4, typedThen = "k", typedNow = "k"))
    }

    @Test
    fun keystrokeAfterLiftDropsTheResult() {
        assertFalse(GlideCommitPolicy.resultStillApplies(sessionThen = 4, sessionNow = 4, typedThen = "k", typedNow = "ka"))
    }

    @Test
    fun spaceOrCommitAfterLiftDropsTheResult() {
        assertFalse(GlideCommitPolicy.resultStillApplies(sessionThen = 4, sessionNow = 4, typedThen = "k", typedNow = ""))
    }

    @Test
    fun newEditorSessionDropsTheResult() {
        assertFalse(GlideCommitPolicy.resultStillApplies(sessionThen = 4, sessionNow = 5, typedThen = "k", typedNow = "k"))
    }
}
