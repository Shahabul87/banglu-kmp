package com.banglu.desktop.editor

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Crash-proof persistence (spec §5): [Draft] is the always-on autosave —
 * the full working state including the forming word; [EditorPrefs] is
 * durable UI state. Writes are atomic (tmp + rename) so a crash mid-write
 * never corrupts the previous draft.
 */
@Serializable
data class Draft(
    val text: String,
    val cursor: Int,
    val formingRaw: String,
    val filePath: String?,
    val savedText: String?,
)

@Serializable
data class EditorPrefs(
    val recent: List<String> = emptyList(),
    val banglaDigits: Boolean = true,
    val winW: Int = 860,
    val winH: Int = 640,
    val learningEnabled: Boolean = true,
)

class DraftStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val draftFile = File(dir, "draft.json")
    private val prefsFile = File(dir, "editor.json")

    init { dir.mkdirs() }

    private fun writeAtomic(target: File, content: String) {
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(content)
        try {
            java.nio.file.Files.move(
                tmp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            // Filesystem without atomic move: still replace — non-atomic beats stale.
            java.nio.file.Files.move(
                tmp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun saveDraft(d: Draft) = writeAtomic(draftFile, json.encodeToString(d))

    fun loadDraft(): Draft? = draftFile.takeIf(File::exists)?.let {
        runCatching { json.decodeFromString<Draft>(it.readText()) }.getOrNull()
    }

    fun clearDraft() { draftFile.delete() }

    fun savePrefs(p: EditorPrefs) = writeAtomic(prefsFile, json.encodeToString(p))

    fun loadPrefs(): EditorPrefs = prefsFile.takeIf(File::exists)?.let {
        runCatching { json.decodeFromString<EditorPrefs>(it.readText()) }.getOrNull()
    } ?: EditorPrefs()
}
