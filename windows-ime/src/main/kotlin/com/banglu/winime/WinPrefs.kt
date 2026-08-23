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
    /**
     * Governs the AUTOMATIC startup update check and nothing else. Turning it
     * off never disables the manual "আপডেট দেখুন" action — a user who asks
     * always gets an answer. Defaults ON: a keyboard that silently stays
     * broken because its owner never went looking for a fix is the failure
     * mode this whole feature exists to end. The default also means an older
     * prefs file (written before this field existed) opts in, which is the
     * intended reading of a missing value.
     */
    val autoUpdate: Boolean = true,
    /**
     * The last version the tray balloon announced ([UpdateNotice]) — "" means
     * never. Persisted so the once-per-version rule survives the restart that
     * happens at every Windows login.
     */
    val updateNoticeVersion: String = "",
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

// `StartupRegistry` — the HKCU Run-key toggle behind "লগইনে চালু হবে" — used to
// live here. It is now `src/msi/kotlin/…/StartupRegistry.kt`: a packaged (MSIX)
// app cannot deliver start-on-login through a Run key, so the Store edition
// declares a `StartupTask` in its manifest and ships no Run-key writer at all.
// `WinPrefs.startOnLogin` is still persisted in both editions — it is simply
// never read in the Store one, where Windows owns the setting.
