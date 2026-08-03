package com.banglu.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S72: pins the memory-pressure reactions. The dangerous mistakes this
 * guards against: degrading to lite on ROUTINE trims (UI_HIDDEN/BACKGROUND
 * fire on every keyboard hide — that would put every phone in lite mode
 * within a day), and NOT degrading on the genuinely-about-to-be-killed
 * signals that produced the tester Samsung's LOW_MEMORY exit history.
 */
class MemoryPressurePolicyTest {

    @Test
    fun `imminent-kill signals degrade full mode to lite`() {
        for (level in listOf(
            MemoryPressurePolicy.TRIM_MEMORY_RUNNING_LOW,
            MemoryPressurePolicy.TRIM_MEMORY_RUNNING_CRITICAL,
            MemoryPressurePolicy.TRIM_MEMORY_COMPLETE
        )) {
            assertEquals(
                MemoryPressurePolicy.Action.DEGRADE_TO_LITE,
                MemoryPressurePolicy.onTrim(level, alreadyLite = false),
                "level=$level"
            )
        }
    }

    @Test
    fun `already-lite devices only shed caches on imminent-kill signals`() {
        assertEquals(
            MemoryPressurePolicy.Action.CLEAR_CACHES,
            MemoryPressurePolicy.onTrim(MemoryPressurePolicy.TRIM_MEMORY_RUNNING_CRITICAL, alreadyLite = true)
        )
    }

    @Test
    fun `routine trims never degrade - they fire on every keyboard hide`() {
        for (level in listOf(
            MemoryPressurePolicy.TRIM_MEMORY_RUNNING_MODERATE,
            MemoryPressurePolicy.TRIM_MEMORY_UI_HIDDEN,
            MemoryPressurePolicy.TRIM_MEMORY_BACKGROUND,
            MemoryPressurePolicy.TRIM_MEMORY_MODERATE
        )) {
            assertEquals(
                MemoryPressurePolicy.Action.CLEAR_CACHES,
                MemoryPressurePolicy.onTrim(level, alreadyLite = false),
                "level=$level"
            )
        }
    }

    @Test
    fun `unknown levels are ignored`() {
        assertEquals(MemoryPressurePolicy.Action.NONE, MemoryPressurePolicy.onTrim(3, alreadyLite = false))
    }

    @Test
    fun `S76 - range comparisons cover future intermediate and above-COMPLETE levels`() {
        // Platform guidance: new levels may be added — 12 sits between
        // RUNNING_LOW and RUNNING_CRITICAL, 90 above COMPLETE.
        assertEquals(
            MemoryPressurePolicy.Action.DEGRADE_TO_LITE,
            MemoryPressurePolicy.onTrim(12, alreadyLite = false)
        )
        assertEquals(
            MemoryPressurePolicy.Action.DEGRADE_TO_LITE,
            MemoryPressurePolicy.onTrim(90, alreadyLite = false)
        )
        assertEquals(
            MemoryPressurePolicy.Action.CLEAR_CACHES,
            MemoryPressurePolicy.onTrim(25, alreadyLite = false)
        )
    }

    @Test
    fun `S76 - exit-history trigger fires only for recent unhandled LOW_MEMORY exits`() {
        val now = 1_000_000_000_000L
        val hour = 60L * 60 * 1000
        // Recent LOW_MEMORY kill, never handled → fire.
        assertTrue(
            MemoryPressurePolicy.isRecentLowMemoryExit(
                MemoryPressurePolicy.EXIT_REASON_LOW_MEMORY, now - hour, 0L, now
            )
        )
        // Same exit already handled → never fire twice.
        assertFalse(
            MemoryPressurePolicy.isRecentLowMemoryExit(
                MemoryPressurePolicy.EXIT_REASON_LOW_MEMORY, now - hour, now - hour, now
            )
        )
        // Old kill outside the 72h lookback → ignore.
        assertFalse(
            MemoryPressurePolicy.isRecentLowMemoryExit(
                MemoryPressurePolicy.EXIT_REASON_LOW_MEMORY, now - 80 * hour, 0L, now
            )
        )
        // Other exit reasons (crash/update/user) → ignore.
        assertFalse(
            MemoryPressurePolicy.isRecentLowMemoryExit(16, now - hour, 0L, now)
        )
    }

    @Test
    fun `post-load guard - tester Samsung at 67 percent stays in full mode`() {
        // 172MB used of a 256MB limit — the observed steady state must NOT
        // trip the guard (that would be a blanket flagship quality loss).
        assertFalse(
            MemoryPressurePolicy.shouldDegradeAfterLoad(
                usedBytes = 172L * 1024 * 1024,
                maxBytes = 256L * 1024 * 1024,
                alreadyLite = false
            )
        )
    }

    @Test
    fun `post-load guard - a 192m device with the same load degrades`() {
        assertTrue(
            MemoryPressurePolicy.shouldDegradeAfterLoad(
                usedBytes = 172L * 1024 * 1024,
                maxBytes = 192L * 1024 * 1024,
                alreadyLite = false
            )
        )
    }

    @Test
    fun `post-load guard never fires in lite mode or with bogus max`() {
        assertFalse(MemoryPressurePolicy.shouldDegradeAfterLoad(100, 100, alreadyLite = true))
        assertFalse(MemoryPressurePolicy.shouldDegradeAfterLoad(100, 0, alreadyLite = false))
    }
}
