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
 */
object StartupRegistry {
    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "BangluTyper"

    fun set(enabled: Boolean, exePath: String) {
        if (!isWindowsOs()) return
        val command = if (enabled) {
            listOf(
                "reg", "add", RUN_KEY,
                "/v", VALUE_NAME, "/t", "REG_SZ", "/d", exePath, "/f",
            )
        } else {
            listOf("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f")
        }
        val exitCode = runCatching { ProcessBuilder(command).start().waitFor() }.getOrDefault(-1)
        if (exitCode != 0) {
            System.err.println("Banglu: startup registry update failed (exit $exitCode)")
        }
    }

    private fun isWindowsOs(): Boolean =
        System.getProperty("os.name", "").contains("windows", ignoreCase = true)
}
