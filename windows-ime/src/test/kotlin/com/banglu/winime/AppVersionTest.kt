package com.banglu.winime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Version parsing and self-identification, tested in BOTH editions.
 *
 * These moved out of `UpdaterTest` when the updater became website-only:
 * `AppVersion` and `Version` are needed by every build (the control window's
 * version line reads them), so the wall that guards them has to run in every
 * build too. The updater's own use of `Version.isNewer` — "is the published
 * manifest newer than me?" — is still pinned in `UpdaterTest`, which only
 * exists in the website edition.
 */
class AppVersionTest {
    @Test fun newerVersionsAreNewer() {
        assertTrue(Version.isNewer("1.0.1", "1.0.0"))
        assertTrue(Version.isNewer("1.1.0", "1.0.9"))
        assertTrue(Version.isNewer("2.0.0", "1.9.9"))
        assertTrue(Version.isNewer("1.0.10", "1.0.9"))
    }

    @Test fun olderAndEqualVersionsAreNotNewer() {
        assertFalse(Version.isNewer("1.0.0", "1.0.1"))
        assertFalse(Version.isNewer("1.0.0", "1.0.0"))
        assertFalse(Version.isNewer("0.9.9", "1.0.0"))
    }

    @Test fun differentComponentCountsComparePositionally() {
        // "1.0" and "1.0.0" are the same release, not an upgrade in either
        // direction — jpackage writes a 3-part version, a hand-edited manifest
        // might not, and neither should invent a difference.
        assertFalse(Version.isNewer("1.0", "1.0.0"))
        assertFalse(Version.isNewer("1.0.0", "1.0"))
        assertTrue(Version.isNewer("1.0.1", "1.0"))
    }

    @Test fun malformedVersionsAreNeverNewer() {
        // THE anti-footgun: whatever a broken or hostile manifest says, a
        // version we cannot parse must leave the user where they are.
        for (junk in listOf("", "   ", "v1.0.1", "1.0.1-beta", "garbage", "1..2", "1.0.0.0.1", "-1")) {
            assertFalse(Version.isNewer(junk, "1.0.0"), "'$junk' must not read as newer")
            assertNull(Version.parse(junk), "'$junk' must not parse")
        }
    }

    @Test fun unknownCurrentVersionIsNeverUpgraded() {
        // An app that does not know what it is cannot conclude something else
        // is newer.
        assertFalse(Version.isNewer("9.9.9", "unknown"))
    }

    @Test fun appVersionPrefersJpackageAndRejectsJunk() {
        assertEquals("1.2.3", AppVersion.resolve("1.2.3", "9.9.9"))
        assertEquals("9.9.9", AppVersion.resolve(null, "9.9.9"))
        assertEquals("9.9.9", AppVersion.resolve("not-a-version", "9.9.9"))
        assertEquals("1.2.3", AppVersion.resolve(" 1.2.3 ", null))
        assertNull(AppVersion.resolve(null, null))
        assertNull(AppVersion.resolve("junk", "junk"))
    }

    @Test fun theBuiltAppKnowsItsOwnVersion() {
        // generateVersionResource stamps bangluTyperVersion into the jar. In
        // the website edition a break here silently stops the updater from
        // offering anything; in the Store edition it silently strips the
        // version out of every bug report. Both are quiet regressions.
        val version = AppVersion.current
        assertNotNull(version, "banglu-typer-version.txt is missing from the classpath")
        assertNotNull(Version.parse(version))
    }
}
