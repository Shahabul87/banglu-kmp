package com.banglu.winime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * What the WEBSITE edition specifically promises. `EditionTest` proves the two
 * editions are internally coherent; this proves this one is the right one — a
 * default build that quietly lost its updater would leave every website user
 * stranded on whatever version they first installed, and nothing else would
 * notice.
 */
class WebsiteEditionTest {
    @Test fun itIsTheWebsiteEdition() {
        assertEquals("website", Edition.id)
    }

    @Test fun itCarriesTheInAppUpdater() {
        assertEquals(true, Edition.hasUpdater)
    }

    @Test fun itTogglesStartOnLoginThroughTheRunKey() {
        assertSame(RunKeyStartOnLogin, Edition.startOnLogin)
        // A real control means there is nothing to explain away.
        assertNull(Edition.startupNote)
    }

    @Test fun theRunKeyControlRefusesToActOffWindows() {
        // The same guard StartupRegistry's own test pins, exercised through the
        // control the tray actually calls: this suite runs on a Mac and must
        // never spawn `reg`. Both directions, because the delete branch takes a
        // different path through the control.
        assertEquals(false, RunKeyStartOnLogin.set(enabled = true))
        assertEquals(false, RunKeyStartOnLogin.set(enabled = false))
    }
}
