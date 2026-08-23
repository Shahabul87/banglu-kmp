package com.banglu.winime

import kotlin.io.path.createTempDirectory
import kotlin.test.*

class WinPrefsTest {
    // createTempDir() is deprecated; kotlin.io.path.createTempDirectory keeps
    // test output free of deprecation warnings.
    private fun tempDir() = createTempDirectory(prefix = "wp").toFile()
    private fun fresh(dir: java.io.File = tempDir()) = WinPrefsStore(dir)

    @Test fun defaultsOnMissingFile() {
        val prefs = fresh().load()
        assertEquals(WinPrefs(), prefs)
        assertTrue(prefs.banglaDigits)
        assertTrue(prefs.startOnLogin)
        assertEquals("BANGLA", prefs.mode)
        // Automatic update checking is ON out of the box: a keyboard that
        // stays broken because nobody went looking for the fix is the failure
        // this defaults against.
        assertTrue(prefs.autoUpdate)
    }

    @Test fun roundTripsAllFields() {
        val dir = tempDir()
        val custom = WinPrefs(
            banglaDigits = false,
            startOnLogin = false,
            mode = "ENGLISH",
            autoUpdate = false,
            updateNoticeVersion = "1.0.4",
        )
        fresh(dir).save(custom)
        assertEquals(custom, fresh(dir).load())
    }

    @Test fun theUpdateNoticeLedgerStartsEmpty() {
        // Empty = "never announced anything": the first offer a fresh install
        // ever sees gets its one balloon.
        assertEquals("", fresh().load().updateNoticeVersion)
    }

    @Test fun autoUpdateRoundTripsBothWays() {
        val dir = tempDir()
        val store = fresh(dir)
        store.save(store.load().copy(autoUpdate = false))
        assertFalse(fresh(dir).load().autoUpdate)
        store.save(store.load().copy(autoUpdate = true))
        assertTrue(fresh(dir).load().autoUpdate)
    }

    @Test fun aPrefsFileWrittenBeforeAutoUpdateExistedOptsIn() {
        // Upgrading from 1.0.0 must not leave the user with update checking
        // silently off — a missing field reads as the default, which is ON.
        val dir = tempDir()
        java.io.File(dir, "winime-prefs.json")
            .writeText("""{"banglaDigits":false,"startOnLogin":true,"mode":"OFF"}""")
        val loaded = fresh(dir).load()
        assertTrue(loaded.autoUpdate)
        assertEquals("OFF", loaded.mode)
        assertFalse(loaded.banglaDigits)
    }

    @Test fun roundTripsOffMode() {
        val dir = tempDir()
        val custom = WinPrefs(mode = "OFF")
        fresh(dir).save(custom)
        assertEquals("OFF", fresh(dir).load().mode)
    }

    @Test fun corruptFileDegradesToDefaultsNotCrash() {
        val dir = tempDir()
        java.io.File(dir, "winime-prefs.json").writeText("{not json")
        assertEquals(WinPrefs(), fresh(dir).load())
    }

    @Test fun saveAfterCorruptFileHealsIt() {
        val dir = tempDir()
        java.io.File(dir, "winime-prefs.json").writeText("{not json")
        val store = fresh(dir)
        // The next save must persist a real, valid state — not re-corrupt or
        // silently drop the write because the prior read failed.
        val healed = store.load().copy(mode = "OFF", banglaDigits = false)
        store.save(healed)
        assertEquals(healed, fresh(dir).load())
    }
}
