package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S194 (tester: "touch sensitivity not as good as the Samsung keyboard in English
 * mode"; measured with injected touches on the S22): the spacebar committed on
 * RELEASE, so a thumb that lands the next letter before lifting from space wrote
 * "theq uick". Samsung commits the held space the moment a second finger lands.
 */
class S194SpaceRolloverPolicyTest {

    @Test
    fun secondFingerCommitsTheHeldSpaceOnceAndReleaseCommitsNothing() {
        val p = SpaceRolloverPolicy()
        p.onSpaceDown()
        assertTrue("first other pointer commits now", p.onOtherPointerDown())
        assertFalse("never twice", p.onOtherPointerDown())
        assertEquals(SpaceRolloverPolicy.Release.NOTHING, p.onSpaceUp())
    }

    @Test
    fun plainTapStillCommitsOnRelease() {
        val p = SpaceRolloverPolicy()
        p.onSpaceDown()
        assertEquals(SpaceRolloverPolicy.Release.COMMIT, p.onSpaceUp())
    }

    @Test
    fun otherPointerWhileNoSpaceIsHeldDoesNothing() {
        val p = SpaceRolloverPolicy()
        assertFalse(p.onOtherPointerDown())
        // letter first, then space: the space is not yet held when the second down arrives
        assertFalse(p.onOtherPointerDown())
        p.onSpaceDown()
        assertEquals(SpaceRolloverPolicy.Release.COMMIT, p.onSpaceUp())
    }

    @Test
    fun cursorDragNeverCommitsAndAnEarlyCommitBlocksTheDrag() {
        val p = SpaceRolloverPolicy()
        p.onSpaceDown()
        assertTrue(p.canEngageCursor)
        p.onCursorModeEngaged()
        assertFalse("a drag in progress is not a space", p.onOtherPointerDown())
        assertEquals(SpaceRolloverPolicy.Release.NOTHING, p.onSpaceUp())

        p.onSpaceDown()
        assertTrue(p.onOtherPointerDown())
        assertFalse("the space already went out; the rest of the hold is inert", p.canEngageCursor)
        assertEquals(SpaceRolloverPolicy.Release.NOTHING, p.onSpaceUp())
    }

    @Test
    fun stateResetsAfterEveryRelease() {
        val p = SpaceRolloverPolicy()
        p.onSpaceDown(); p.onOtherPointerDown(); p.onSpaceUp()
        p.onSpaceDown()
        assertTrue(p.canEngageCursor)
        assertEquals(SpaceRolloverPolicy.Release.COMMIT, p.onSpaceUp())
    }
}
