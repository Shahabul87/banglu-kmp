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
        // The guard, not the machine. This asserted that the control does
        // nothing — true on the developer's Mac, false on a Windows CI runner
        // where the guard rightly passes and `reg` really runs, which failed
        // the release build and would have spawned a process (and written a
        // Run key) inside a test. Off Windows the no-op is still pinned
        // end-to-end through the control the tray calls; on Windows only the
        // pure predicate is exercised, so no test ever mutates the registry.
        if (StartupRegistry.isWindowsOs(System.getProperty("os.name"))) {
            assertEquals(true, StartupRegistry.isWindowsOs("Windows 11"))
            assertEquals(false, StartupRegistry.isWindowsOs("Mac OS X"))
        } else {
            assertEquals(false, RunKeyStartOnLogin.set(enabled = true))
            assertEquals(false, RunKeyStartOnLogin.set(enabled = false))
        }
    }
}
