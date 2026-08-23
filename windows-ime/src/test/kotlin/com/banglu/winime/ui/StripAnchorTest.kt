package com.banglu.winime.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * S132: where the suggestion strip goes (field report #3, PowerPoint
 * screenshot: the user typed at the TOP of the screen and the strip sat on
 * the TASKBAR). Two causes, one planner:
 *
 *  - the strip only re-anchored on hidden→visible, and the prediction row
 *    keeps it visible forever — so it froze at its first-ever position;
 *  - with no caret (PowerPoint, Chrome, Electron draw their own) the fallback
 *    was the raw mouse position, wherever it happened to be parked.
 *
 * The planner is pure so this contract is pinned on the Mac dev machine.
 */
class StripAnchorTest {

    private val window = AnchorRect(left = 0, top = 0, right = 2000, bottom = 1200)

    @Test
    fun aRealCaretAlwaysWins() {
        val anchor = StripAnchor.plan(
            caret = AnchorPoint(400, 300),
            mouse = AnchorPoint(1900, 1180),
            window = window,
            last = AnchorPoint(1000, 600) to window,
        )
        assertEquals(AnchorPoint(400, 300), anchor)
    }

    @Test
    fun withoutACaretTheLastAnchorInTheSameWindowHolds() {
        // Mid-word the strip must not chase the mouse across the window —
        // the user is typing, not pointing.
        val anchor = StripAnchor.plan(
            caret = null,
            mouse = AnchorPoint(1900, 1180),
            window = window,
            last = AnchorPoint(600, 250) to window,
        )
        assertEquals(AnchorPoint(600, 250), anchor)
    }

    @Test
    fun aLastAnchorFromAnotherWindowIsDead() {
        // Focus moved to a different window whose rect happens to overlap:
        // the old anchor says nothing about where text is now.
        val other = AnchorRect(100, 100, 1800, 1100)
        val anchor = StripAnchor.plan(
            caret = null,
            mouse = AnchorPoint(500, 400),
            window = window,
            last = AnchorPoint(600, 250) to other,
        )
        assertEquals(AnchorPoint(500, 400), anchor, "falls to the mouse inside the new window")
    }

    @Test
    fun theMouseCountsOnlyInsideTheFocusedWindow() {
        // The screenshot case: mouse parked over the taskbar, outside the
        // (full-screen) window — never anchor there. With no caret, no last
        // and no usable mouse, the strip sits at the window's bottom-center,
        // clear of the taskbar and inside the app being typed in.
        val anchor = StripAnchor.plan(
            caret = null,
            mouse = AnchorPoint(1000, 1250), // below the window: taskbar land
            window = window,
            last = null,
        )
        assertEquals(
            AnchorPoint(1000, 1200 - StripAnchor.WINDOW_BOTTOM_MARGIN),
            anchor,
        )
    }

    @Test
    fun aDegenerateWindowRectIsIgnored() {
        // Minimized/zero rects must not produce a corner anchor.
        val anchor = StripAnchor.plan(
            caret = null,
            mouse = AnchorPoint(700, 700),
            window = AnchorRect(0, 0, 0, 0),
            last = null,
        )
        assertEquals(AnchorPoint(700, 700), anchor, "plain mouse fallback")
    }

    @Test
    fun nothingKnownMeansNoMove() {
        assertNull(StripAnchor.plan(caret = null, mouse = null, window = null, last = null))
    }
}
