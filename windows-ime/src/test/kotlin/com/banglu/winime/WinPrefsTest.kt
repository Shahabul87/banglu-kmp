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
    }

    @Test fun roundTripsAllFields() {
        val dir = tempDir()
        val custom = WinPrefs(banglaDigits = false, startOnLogin = false, mode = "ENGLISH")
        fresh(dir).save(custom)
        assertEquals(custom, fresh(dir).load())
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
