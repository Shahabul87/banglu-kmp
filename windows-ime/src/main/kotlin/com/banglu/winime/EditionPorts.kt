package com.banglu.winime

import java.io.File

/**
 * The seam that makes বাংলু টাইপার's two distribution editions a BUILD-TIME
 * choice rather than a runtime flag.
 *
 * There are exactly two editions and they differ only in how the app is
 * delivered and kept current:
 *
 * | | website (default) | Microsoft Store (`-PbangluStore=true`) |
 * |---|---|---|
 * | packaging | jpackage MSI | MSIX, ingested and re-signed by Microsoft |
 * | updates | the in-app updater in `update/` | the Store |
 * | start on login | `HKCU\…\Run`, toggled in the tray | manifest `StartupTask`, toggled in Windows Settings |
 *
 * The Store edition does not merely *disable* the updater: `src/msi/kotlin` is
 * not compiled into it at all, so the whole `update/` package — and the JDK web
 * client it builds — is physically absent from the package. That is not
 * fussiness. The JDK's web client constructor opens an internal loopback
 * socket pair, and an MSIX container refuses it — the app died at start-up with
 * `Unable to establish loopback connection` in the packaging spike, and no
 * manifest capability fixed it. It is also the right product answer: a Store
 * app whose only network code can never legitimately run should not carry that
 * code, and `verifyStoreEdition` fails the build if it creeps back in.
 *
 * Everything else — the hook, the engine, the composer, the controller, the
 * tray, the control window, the caret preview, learning, app-compat — is the
 * same source in both editions.
 *
 * Each edition supplies an `Edition` object with exactly these members
 * (`src/msi/kotlin/…/Edition.kt`, `src/store/kotlin/…/Edition.kt`); the shape
 * is asserted by `EditionTest`, which runs in BOTH editions.
 */

/**
 * Everything the control window renders about updates, in one value.
 *
 * Declared here rather than beside the updater because `Main.kt` must compile
 * in an edition where the updater does not exist. Empty line = say nothing,
 * which is what an automatic check that found nothing (or failed) leaves
 * behind, and what the Store edition reports forever.
 */
data class UpdateStatus(
    val line: String = "",
    /** True only when there is a verified-shape update the user may install. */
    val actionable: Boolean = false,
    /** True while a check/download/install is in flight — disables the button. */
    val busy: Boolean = false,
)

/**
 * The in-app updater, as `Main.kt` sees it. Every method BLOCKS — call from a
 * background dispatcher only, never from the AWT event thread and never from
 * anything on the typing path.
 *
 * `null` from [EditionPorts.updateGateway] means the edition HAS no updater;
 * callers must draw no update UI at all rather than an inert one.
 */
interface UpdateGateway {
    fun check(manual: Boolean)
    fun install(onInstallerStarted: () -> Unit)
}

/**
 * "Start when I log in", as the packaging medium implements it.
 *
 * Blocking, and returns true ONLY on a confirmed success: the caller flips the
 * checkbox and persists the preference on true and on nothing else, because a
 * toggle that reports success it did not achieve is the exact failure this
 * interface exists to make impossible.
 */
interface StartOnLoginControl {
    fun set(enabled: Boolean): Boolean
}

/**
 * The members every `Edition` object must provide. Both editions declare
 * `object Edition : EditionPorts`, so the compiler — not a convention — is what
 * keeps the two in step, and `Main.kt` type-checks against this interface
 * rather than against whichever object happens to be on the source path.
 */
interface EditionPorts {
    /** ASCII, stable, for bug reports and logs: `website` or `store`. */
    val id: String

    /** What the user sees on the control window's version line. */
    val label: String

    /** False in the Store edition — draw no update row, item or preference. */
    val hasUpdater: Boolean

    /**
     * One disabled tray line explaining where start-on-login lives when this
     * edition has no toggle of its own; null when [startOnLogin] is present.
     * Never both, never neither.
     */
    val startupNote: String?

    /** Null in the Store edition: Windows owns the packaged app's StartupTask. */
    val startOnLogin: StartOnLoginControl?

    /** Null in the Store edition. [downloadDir] is unused when null is returned. */
    fun updateGateway(downloadDir: File, report: (UpdateStatus) -> Unit): UpdateGateway?
}

/**
 * The one line that tells a bug reporter — and us — which build they are on.
 *
 * Rendered in the control window's footer. Pure and parameterized so the
 * formatting is testable without a packaged app; [line] is the live value.
 */
object EditionInfo {
    val line: String get() = format(AppVersion.current, Edition.label, Edition.id)

    internal fun format(version: String?, label: String, id: String): String =
        "সংস্করণ ${version ?: "অজানা"} · $label ($id)"
}
