package com.banglu.winime

import java.io.File

/**
 * The MICROSOFT STORE edition — built with `-PbangluStore=true` and shipped as
 * an MSIX that Microsoft re-signs at ingestion.
 *
 * Two features are deliberately ABSENT here rather than disabled, and each
 * absence is a correctness fix, not a simplification:
 *
 * **No updater.** The Store updates Store apps. Beyond being redundant, the
 * JDK web client's constructor opens an internal loopback socket pair that an
 * MSIX container refuses — the packaging spike watched the app die at start-up
 * with `Unable to establish loopback connection`, and neither `internetClient`
 * nor `privateNetworkClientServer` fixed it. `src/msi/kotlin` is simply not on
 * this build's source path, so the whole `update/` package does not exist in
 * the build at all (asserted by `EditionTest`, and by `verifyStoreEdition` at
 * the source level). The app therefore ships with no
 * networking code whatsoever, which makes the privacy claim in the README
 * stronger here than in the MSI build.
 *
 * **No start-on-login toggle.** A packaged app cannot use `HKCU\…\Run`: its
 * executable lives under `C:\Program Files\WindowsApps\…`, a path that changes
 * with every version and that the user cannot reach. MSIX has its own mechanism
 * — a `windows.startupTask` extension in `AppxManifest.xml`, which this package
 * declares with `Enabled="true"` — and the OS, not the app, owns the switch:
 * **Settings → Apps → Startup**, or Task Manager's Startup tab. Toggling it
 * from inside the app would need the WinRT `StartupTask` API, which is not
 * reachable from this codebase without dragging a second native-interop layer
 * outside `hook/` (a repo isolation law). So the tray shows one disabled line
 * saying where the setting actually is. An absent feature with a signpost beats
 * a switch that lies.
 */
object Edition : EditionPorts {
    override val id = "store"
    override val label = "Microsoft Store সংস্করণ"

    /** The Store updates Store apps; this build has no updater to run. */
    override val hasUpdater = false

    override val startupNote: String? =
        "লগইনে চালু: Windows Settings → Apps → Startup"

    override val startOnLogin: StartOnLoginControl? = null

    override fun updateGateway(downloadDir: File, report: (UpdateStatus) -> Unit): UpdateGateway? = null
}
