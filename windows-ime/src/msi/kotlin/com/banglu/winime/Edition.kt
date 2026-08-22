package com.banglu.winime

import com.banglu.winime.update.UpdateService
import java.io.File

/**
 * The WEBSITE edition — the default build, packaged as an MSI and downloaded
 * from craftsai.org / a GitHub release.
 *
 * It carries the in-app updater (nothing else would ever tell these users a fix
 * exists) and toggles start-on-login itself through the `HKCU\…\Run` key.
 *
 * Its Store twin is `src/store/kotlin/…/Edition.kt`. Everything either object
 * may say is fixed by [EditionPorts]; adding a member to one without the other
 * fails to compile, and `EditionTest` runs against both.
 */
object Edition : EditionPorts {
    override val id = "website"
    override val label = "ওয়েবসাইট সংস্করণ"
    override val hasUpdater = true

    /** The tray has a real toggle in this edition, so nothing to explain. */
    override val startupNote: String? = null

    override val startOnLogin: StartOnLoginControl? = RunKeyStartOnLogin

    override fun updateGateway(downloadDir: File, report: (UpdateStatus) -> Unit): UpdateGateway? =
        UpdateService(downloadDir = downloadDir, report = report)
}
