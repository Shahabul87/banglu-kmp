package com.banglu.winime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tray announcement for a found update (user request, 2026-08-23: "how do
 * I know the update is there" — before this, the offer lived only inside the
 * control window). The gate is once-per-version, PERSISTED: the app restarts
 * with Windows every login, and a balloon on every restart for the same
 * version would be the nagging the updater's failure posture forbids.
 */
class UpdateNoticeTest {

    @Test fun aFreshOfferIsAnnounced() {
        assertTrue(UpdateNotice.shouldAnnounce(offered = "1.0.4", alreadyAnnounced = ""))
        assertTrue(UpdateNotice.shouldAnnounce(offered = "1.0.4", alreadyAnnounced = "1.0.3"))
    }

    @Test fun theSameVersionIsAnnouncedExactlyOnce() {
        assertFalse(UpdateNotice.shouldAnnounce(offered = "1.0.4", alreadyAnnounced = "1.0.4"))
    }

    @Test fun noOfferAnnouncesNothing() {
        assertFalse(UpdateNotice.shouldAnnounce(offered = null, alreadyAnnounced = ""))
        assertFalse(UpdateNotice.shouldAnnounce(offered = "", alreadyAnnounced = ""))
        assertFalse(UpdateNotice.shouldAnnounce(offered = " ", alreadyAnnounced = ""))
    }

    @Test fun theMessageNamesTheVersionAndTheOneClick() {
        val message = UpdateNotice.message("1.0.4")
        assertTrue("1.0.4" in message, message)
        // The balloon cannot be clicked through to the installer, so the text
        // itself must say where the one-click install lives.
        assertTrue("উইন্ডো" in message, message)
    }

    @Test fun statusCarriesTheOfferedVersionOnlyWhenActionable() {
        // The UpdateStatus contract the announcement reads: a plain progress
        // or failure line never carries a version, so it can never re-trigger
        // the balloon.
        assertEquals(null, UpdateStatus().offeredVersion)
        assertEquals(null, UpdateStatus(line = "x", busy = true).offeredVersion)
        assertEquals("1.0.4", UpdateStatus(line = "x", actionable = true, offeredVersion = "1.0.4").offeredVersion)
    }
}
