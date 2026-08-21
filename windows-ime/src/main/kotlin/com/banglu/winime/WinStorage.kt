package com.banglu.winime

import com.banglu.engine.platform.PlatformStorage
import com.banglu.engine.types.LearnedWord
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Windows IME learned-words host: port of desktop-app's FileStorage, made
 * dir-injectable for testability. Production default is %USERPROFILE%\.banglu
 * — the same directory the desktop editor uses (one shared learning brain).
 */
class WinStorage(
    private val baseDir: File = File(System.getProperty("user.home"), ".banglu")
) : PlatformStorage {
    @Serializable
    private data class Row(val p: String, val b: String, val f: Int, val t: Long)

    init { baseDir.mkdirs() }

    private val file = File(baseDir, "learned.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private fun readAll(): MutableList<Row> = synchronized(lock) {
        if (!file.exists()) mutableListOf()
        else runCatching { json.decodeFromString<MutableList<Row>>(file.readText()) }
            .getOrElse { mutableListOf() }
    }

    // S108 (invariant #10): tmp + atomic replace, same pattern as desktop's
    // FileStorage and the macOS IME's LearnedStore. A plain writeText
    // interrupted mid-write corrupted learned.json; readAll then swallowed
    // the parse error into an empty list and the NEXT save persisted that
    // empty list — silent loss of the whole shared learning brain.
    private fun writeRows(rows: List<Row>) {
        val tmp = File(baseDir, "learned.json.tmp")
        tmp.writeText(json.encodeToString(rows))
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

    override suspend fun getLearnedWords(): List<LearnedWord> =
        readAll().mapIndexed { i, r -> LearnedWord("d$i", r.p, r.b, r.f, r.t) }

    override suspend fun saveLearnedWord(phonetic: String, bengali: String, frequency: Int) {
        synchronized(lock) {
            val rows = readAll()
            val existing = rows.indexOfFirst { it.p == phonetic && it.b == bengali }
            if (existing >= 0) {
                val old = rows[existing]
                rows[existing] = old.copy(f = maxOf(old.f + 1, frequency), t = System.currentTimeMillis())
            } else {
                rows.add(Row(phonetic, bengali, frequency, System.currentTimeMillis()))
            }
            writeRows(rows)
        }
    }

    override suspend fun removeLearnedWord(phonetic: String) {
        // S78 explicit user correction (tap on the engine primary): drop this
        // key's sub-custom preference rows; custom formulas (f >= 120) stay.
        synchronized(lock) {
            val rows = readAll()
            val kept = rows.filterNot { it.p == phonetic && it.f < 120 }
            if (kept.size != rows.size) writeRows(kept)
        }
    }

    override suspend fun clearLearnedWords() {
        synchronized(lock) { file.delete() }
    }

    override suspend fun getDictionaryVersion(): String? = null
    override suspend fun cacheDictionary(
        words: List<String>, frequencies: Map<String, Int>?,
        disambigMap: Map<String, String>?, version: String
    ) {}
    override suspend fun getCachedDictionary(currentVersion: String) = null
}
