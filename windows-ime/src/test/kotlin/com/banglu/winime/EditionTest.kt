package com.banglu.winime

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The edition wall — and it runs in BOTH editions.
 *
 * `./gradlew :windows-ime:test` exercises the website build;
 * `./gradlew :windows-ime:test -PbangluStore=true` exercises the Microsoft
 * Store build. Every assertion below is therefore written as an invariant that
 * must hold whichever `Edition` object is on the source path, not as "the
 * updater is present" or "the updater is absent" — which is exactly what makes
 * one test file able to guard two builds.
 *
 * The load-bearing one is [updaterPresenceMatchesTheEdition]: it asks the class
 * loader, so it fails if a Store build ever ships the updater classes even when
 * nothing calls them. The MSIX spike proved that is not a style preference —
 * merely CONSTRUCTING the JDK web client kills the app inside an MSIX
 * container.
 */
class EditionTest {
    private fun classPresent(name: String): Boolean =
        runCatching { Class.forName(name, false, EditionTest::class.java.classLoader) }.isSuccess

    @Test fun theEditionIdentifiesItselfWithAStableAsciiId() {
        // The id ends up in bug reports; it must be one of two known values and
        // must not be a display string that could be translated later.
        assertTrue(Edition.id in setOf("website", "store"), "unexpected edition id '${Edition.id}'")
        assertTrue(Edition.id.all { it in 'a'..'z' }, "the edition id must stay ASCII lowercase")
        assertTrue(Edition.label.isNotBlank())
    }

    @Test fun updaterPresenceMatchesTheEdition() {
        // The updater's classes must be ABSENT from a Store build, not merely
        // unreachable: an MSIX container refuses the loopback socket pair the
        // JDK web client opens in its constructor, so a Store package carrying
        // that code is one careless call away from not starting at all.
        val updaterClasses = listOf(
            "com.banglu.winime.update.UpdateService",
            "com.banglu.winime.update.UpdatePlan",
            "com.banglu.winime.update.UpdateManifest",
        )
        for (name in updaterClasses) {
            assertEquals(
                Edition.hasUpdater,
                classPresent(name),
                "$name present=${classPresent(name)} but hasUpdater=${Edition.hasUpdater}",
            )
        }
    }

    @Test fun runKeyWriterPresenceMatchesTheEdition() {
        // Same law for start-on-login: a packaged app must not carry a writer
        // for a Run key it can never point at a valid path.
        assertEquals(
            Edition.startOnLogin != null,
            classPresent("com.banglu.winime.StartupRegistry"),
        )
    }

    @Test fun theGatewayAgreesWithHasUpdater() {
        // `hasUpdater` drives whether Main.kt draws ANY update surface, so a
        // build where the flag and the factory disagree would either show an
        // inert row or hide a working updater.
        val dir = createTempDirectory(prefix = "edition").toFile()
        val gateway = Edition.updateGateway(dir) { }
        if (Edition.hasUpdater) assertNotNull(gateway) else assertNull(gateway)
    }

    @Test fun exactlyOneStartOnLoginSurfaceIsOffered() {
        // The failure this forbids is the one the user has hit twice: a toggle
        // that silently does nothing. Either the edition can really apply the
        // setting (a control), or it says where the setting lives (a note).
        // Never both — that would be two answers to one question — and never
        // neither, which would leave start-on-login undiscoverable.
        val hasControl = Edition.startOnLogin != null
        val hasNote = Edition.startupNote != null
        assertTrue(hasControl != hasNote, "control=$hasControl note=$hasNote")
        if (hasNote) assertTrue(Edition.startupNote!!.isNotBlank())
    }

    @Test fun autoUpdatePreferenceIsMeaninglessWithoutAnUpdater() {
        // Not a behaviour assertion — a documentation one. WinPrefs keeps the
        // field in both editions so a user who moves between them does not lose
        // their setting; the Store build simply never reads it, which is what
        // `hasUpdater` gating in Main.kt guarantees.
        assertTrue(WinPrefs().autoUpdate, "the default must stay opt-in for the website edition")
    }

    // ── the version line ────────────────────────────────────────────────────

    @Test fun theVersionLineNamesBothTheVersionAndTheEdition() {
        val line = EditionInfo.line
        val version = assertNotNull(AppVersion.current, "the build must know its own version")
        assertTrue(line.contains(version), "version line '$line' omits the version")
        assertTrue(line.contains(Edition.id), "version line '$line' omits the edition id")
        assertTrue(line.contains(Edition.label), "version line '$line' omits the edition label")
    }

    @Test fun anUnknownVersionSaysSoRatherThanInventingOne() {
        val line = EditionInfo.format(null, "ওয়েবসাইট সংস্করণ", "website")
        assertTrue(line.contains("অজানা"), line)
        assertFalse(line.contains("null"), line)
        assertTrue(line.contains("website"), line)
    }

    @Test fun theVersionLineIsStableAndReadable() {
        assertEquals(
            "সংস্করণ 1.0.1 · Microsoft Store সংস্করণ (store)",
            EditionInfo.format("1.0.1", "Microsoft Store সংস্করণ", "store"),
        )
    }
}
