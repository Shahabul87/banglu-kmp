package com.banglu.winime

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The persisted shape of every tray toggle plus the last-selected mode. This
 * replaces the inline placeholder that Task 7 left in `Main.kt` — nothing
 * read it there; `WinPrefsStore` is the real, disk-backed home for it.
 *
 * `mode` is a plain String (not [Mode]) so a future enum value never fails
 * to deserialize an older prefs file — `WinPrefsStore` degrades an unknown
 * value to [Mode.BANGLA] at the read site instead.
 */
@Serializable
data class WinPrefs(
    val banglaDigits: Boolean = true,
    val startOnLogin: Boolean = true,
    val mode: String = "BANGLA",
)

/**
 * Windows IME settings host: same shape as [WinStorage]/[AppCompat] —
 * `baseDir` injectable for tests (no default, so a test can never write into
 * the real `~/.banglu`), tmp + atomic-move writes, corrupt file degrades to
 * defaults rather than crashing or destroying the next save.
 */
class WinPrefsStore(private val baseDir: File) {
    init { baseDir.mkdirs() }

    private val file = File(baseDir, "winime-prefs.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    fun load(): WinPrefs = synchronized(lock) {
        if (!file.exists()) WinPrefs()
        else runCatching { json.decodeFromString<WinPrefs>(file.readText()) }
            .getOrElse { WinPrefs() }
    }

    // Same pattern as WinStorage/AppCompat (S108, invariant #10): tmp file +
    // atomic replace, non-atomic fallback for filesystems without it. A
    // truncated write here must never leave winime-prefs.json unreadable for
    // the NEXT load, which would otherwise silently reset every toggle.
    fun save(p: WinPrefs) {
        synchronized(lock) {
            val tmp = File(baseDir, "winime-prefs.json.tmp")
            tmp.writeText(json.encodeToString(p))
            try {
                java.nio.file.Files.move(
                    tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                // Filesystem without atomic move: still replace — non-atomic
                // beats stale.
                java.nio.file.Files.move(
                    tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
    }
}

/**
 * The "start on login" registry toggle. Windows-only by construction: every
 * public entry point checks [isWindowsOs] BEFORE touching [ProcessBuilder],
 * so running this on any other OS (this whole test suite runs on a Mac)
 * cannot spawn `reg` and cannot throw or hang.
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
