package com.banglu.winime

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the MICROSOFT STORE edition specifically promises.
 *
 * Runs only under `./gradlew :windows-ime:test -PbangluStore=true`, which is
 * how the release and smoke workflows build this variant. Each assertion here
 * corresponds to something that killed or would have killed the packaged app:
 *
 *  - the updater's classes must be gone, because merely CONSTRUCTING the JDK
 *    web client throws `Unable to establish loopback connection` inside an
 *    MSIX container and takes the whole app down at start-up;
 *  - the Run-key writer must be gone, because a packaged app's executable path
 *    changes with every version and lives somewhere the user cannot reach;
 *  - and something must still tell the user where start-on-login went, or the
 *    feature simply vanishes with no explanation.
 */
class StoreEditionTest {
    private fun classPresent(name: String): Boolean =
        runCatching { Class.forName(name, false, StoreEditionTest::class.java.classLoader) }.isSuccess

    @Test fun itIsTheStoreEdition() {
        assertEquals("store", Edition.id)
    }

    @Test fun itHasNoUpdaterAtAll() {
        assertFalse(Edition.hasUpdater)
        assertNull(Edition.updateGateway(createTempDirectory(prefix = "store").toFile()) { })
        for (name in listOf(
            "com.banglu.winime.update.UpdateService",
            "com.banglu.winime.update.Http",
            "com.banglu.winime.update.Net",
            "com.banglu.winime.update.Checksum",
            "com.banglu.winime.update.Installer",
        )) {
            assertFalse(classPresent(name), "$name must not exist in a Store build")
        }
    }

    @Test fun itShipsNoRunKeyWriter() {
        assertNull(Edition.startOnLogin)
        assertFalse(classPresent("com.banglu.winime.StartupRegistry"))
        assertFalse(classPresent("com.banglu.winime.RunKeyStartOnLogin"))
    }

    @Test fun itSaysWhereStartOnLoginActuallyLives() {
        // The manifest declares a windows.startupTask; Windows owns the switch.
        // An absent toggle with a signpost, never a toggle that lies.
        val note = assertNotNull(Edition.startupNote)
        assertTrue(note.contains("Startup"), note)
        assertTrue(note.contains("Settings"), note)
    }

    @Test fun theVersionLineSaysStore() {
        assertTrue(EditionInfo.line.contains("(store)"), EditionInfo.line)
    }
}
