package com.banglu.winime

/**
 * The "start on login" registry toggle. Windows-only by construction: every
 * public entry point checks [isWindowsOs] BEFORE touching [ProcessBuilder],
 * so running this on any other OS (this whole test suite runs on a Mac)
 * cannot spawn `reg` and cannot throw or hang.
 *
 * WEBSITE EDITION ONLY. A Run key names an absolute path to an executable; an
 * MSIX-packaged app lives under `C:\Program Files\WindowsApps\…`, a path the
 * user cannot reach and whose directory name changes with every version, and
 * Windows expects packaged apps to declare a `StartupTask` in the manifest
 * instead. So this file is compiled into the MSI build only — see
 * `EditionPorts.kt`.
 *
 * Never logs or echoes `exePath` — see CLAUDE.md's secrets policy; a local
 * file path is not a credential, but the brief is explicit that this must
 * not leak it, so failures are reported by exit code alone.
 *
 * `set` is synchronous and genuinely blocks for `reg.exe`'s real duration —
 * callers MUST run it off the UI thread (review finding, Task 8 round 2):
 * `reg` can stall on registry virtualization, AV interception, or Group
 * Policy, and Compose-for-Desktop's tray/preview window share one AWT event
 * thread with the whole menu.
 */
object StartupRegistry {
    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "BangluTyper"
    private const val WAIT_TIMEOUT_SECONDS = 5L

    /**
     * Returns true only on a confirmed, zero-exit-code `reg` run within the
     * timeout. A `reg.exe` that never returns is killed and reported as a
     * failure rather than left to block the calling coroutine forever — the
     * caller must not persist state or flip the checkbox as if this
     * succeeded when it returns false.
     */
    fun set(enabled: Boolean, exePath: String): Boolean {
        if (!isWindowsOs(System.getProperty("os.name"))) return false
        val command = if (enabled) {
            listOf(
                "reg", "add", RUN_KEY,
                "/v", VALUE_NAME, "/t", "REG_SZ", "/d", exePath, "/f",
            )
        } else {
            listOf("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f")
        }
        return runCatching {
            val process = ProcessBuilder(command).start()
            if (!process.waitFor(WAIT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                // Stuck reg.exe: kill it rather than leak a process and hang
                // the caller — never logs the command (it carries exePath).
                process.destroyForcibly()
                System.err.println("Banglu: startup registry update timed out")
                return@runCatching false
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                System.err.println("Banglu: startup registry update failed (exit $exitCode)")
            }
            exitCode == 0
        }.getOrDefault(false)
    }

    /**
     * Pure and parameterized so it is unit-testable on its own, with no
     * `ProcessBuilder` and no dependency on the real `System.getProperty` —
     * it is the single guard standing between this repo's Mac test suite
     * and a real `reg` invocation.
     */
    internal fun isWindowsOs(osName: String?): Boolean =
        osName?.contains("windows", ignoreCase = true) == true
}

/**
 * [StartOnLoginControl] as the MSI edition implements it: an `HKCU\…\Run`
 * value naming the installed launcher.
 *
 * Resolving that path is part of the operation rather than the caller's job,
 * because the failure it can produce — no single meaningful executable, which
 * is what a `./gradlew :windows-ime:run` dev session looks like — has to reach
 * the user as "this did not work" and not as a silently ignored click. A Run
 * key pointing at a bare `java` with no classpath would fail at every login,
 * forever, with nothing on screen ever saying so.
 */
object RunKeyStartOnLogin : StartOnLoginControl {
    override fun set(enabled: Boolean): Boolean {
        // The delete branch never needs a path.
        if (!enabled) return StartupRegistry.set(enabled = false, exePath = "")
        val exePath = resolveExePath() ?: return false
        return StartupRegistry.set(enabled = true, exePath = exePath)
    }

    /**
     * Best-effort executable path — `null` in a dev run. Never logged
     * (CLAUDE.md secrets policy).
     */
    private fun resolveExePath(): String? =
        runCatching { ProcessHandle.current().info().command().orElse(null) }.getOrNull()
}
