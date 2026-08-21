package com.banglu.winime

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Per-exe passthrough table: the escape hatch that lets a misbehaving host
 * (or a password manager) opt entirely out of interception. When
 * `isPassthrough` is true for the focused app's exe, the caller must let
 * every keystroke through untouched — no conversion, no injection.
 *
 * Built-ins (password managers) are ON by default; a user can `remove()`
 * a built-in, which persists as an override rather than mutating the
 * built-in set itself (S5, per task brief: "remove of a built-in must
 * persist as an override").
 */
class AppCompat(baseDir: File) {
    @Serializable
    private data class Override(val exe: String, val passthrough: Boolean)

    companion object {
        // Password managers must never see intercepted keystrokes — this is
        // a privacy/safety guarantee of the product, not a convenience default.
        private val BUILT_INS = setOf(
            "keepass.exe", "keepassxc.exe", "1password.exe", "bitwarden.exe",
        )
    }

    init { baseDir.mkdirs() }

    private val file = File(baseDir, "winime-appcompat.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    // Reads never touch disk or the lock — the keyboard-hook thread calls
    // isPassthrough on every keystroke, so the effective set must be an
    // immutable snapshot swapped wholesale on mutation, not read from file.
    @Volatile
    private var snapshot: Set<String> = loadEffectiveSet()

    private fun readOverrides(): List<Override> =
        if (!file.exists()) emptyList()
        else runCatching { json.decodeFromString<List<Override>>(file.readText()) }
            .getOrElse { emptyList() }

    private fun loadEffectiveSet(): Set<String> {
        val effective = BUILT_INS.toMutableSet()
        for (o in readOverrides()) {
            val key = o.exe.lowercase()
            if (o.passthrough) effective.add(key) else effective.remove(key)
        }
        return effective
    }

    private fun writeOverrides(overrides: List<Override>) {
        val tmp = File(file.parentFile, "winime-appcompat.json.tmp")
        tmp.writeText(json.encodeToString(overrides))
        try {
            java.nio.file.Files.move(
                tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            // Filesystem without atomic move: still replace — non-atomic beats stale.
            java.nio.file.Files.move(
                tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun isPassthrough(exeName: String): Boolean = snapshot.contains(exeName.lowercase())

    val entries: List<String> get() = snapshot.toList()

    fun add(exeName: String) {
        val key = exeName.lowercase()
        synchronized(lock) {
            val overrides = readOverrides().filterNot { it.exe.lowercase() == key }.toMutableList()
            // Only persist an override when it deviates from the built-in
            // default; adding a built-in that's already on needs no row.
            if (key !in BUILT_INS) overrides.add(Override(key, true))
            writeOverrides(overrides)
            snapshot = loadEffectiveSet()
        }
    }

    fun remove(exeName: String) {
        val key = exeName.lowercase()
        synchronized(lock) {
            val overrides = readOverrides().filterNot { it.exe.lowercase() == key }.toMutableList()
            // Removing a built-in must persist as an explicit override so it
            // stays removed across a fresh instance; removing a non-built-in
            // that was never added needs no row.
            if (key in BUILT_INS) overrides.add(Override(key, false))
            writeOverrides(overrides)
            snapshot = loadEffectiveSet()
        }
    }
}
