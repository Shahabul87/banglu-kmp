package com.banglu.winime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `StartupRegistry.set` itself is Windows-only and untested here (per the
 * brief — this whole suite runs on a Mac). `isWindowsOs` is the one guard
 * standing between that suite and a real `reg` invocation, so it is tested
 * directly: pure, parameterized, no `ProcessBuilder` involved.
 */
class StartupRegistryTest {
    @Test fun recognizesRealWindowsOsNameStrings() {
        assertTrue(StartupRegistry.isWindowsOs("Windows 11"))
        assertTrue(StartupRegistry.isWindowsOs("Windows 10"))
        assertTrue(StartupRegistry.isWindowsOs("Windows Server 2022"))
    }

    @Test fun isCaseInsensitive() {
        assertTrue(StartupRegistry.isWindowsOs("WINDOWS 11"))
        assertTrue(StartupRegistry.isWindowsOs("windows 11"))
    }

    @Test fun rejectsNonWindowsOsNames() {
        // Mac OS X / macOS is what this whole test suite actually runs on.
        assertFalse(StartupRegistry.isWindowsOs("Mac OS X"))
        assertFalse(StartupRegistry.isWindowsOs("Linux"))
        assertFalse(StartupRegistry.isWindowsOs(""))
    }

    @Test fun rejectsNullOsName() {
        // System.getProperty("os.name") can return null; must degrade to
        // "not Windows", never throw or default to true.
        assertFalse(StartupRegistry.isWindowsOs(null))
    }
}
