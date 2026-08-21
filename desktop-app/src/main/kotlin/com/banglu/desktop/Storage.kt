package com.banglu.desktop

import com.banglu.engine.JvmSqliteDictionaryLoader
import com.banglu.engine.platform.PlatformStorage
import com.banglu.engine.types.LearnedWord
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** S49: learned words persisted to ~/.banglu/learned.json. */
object FileStorage : PlatformStorage {
    @Serializable
    private data class Row(val p: String, val b: String, val f: Int, val t: Long)

    private val dir = File(System.getProperty("user.home"), ".banglu").apply { mkdirs() }
    private val file = File(dir, "learned.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private fun readAll(): MutableList<Row> = synchronized(lock) {
        if (!file.exists()) mutableListOf()
        else runCatching { json.decodeFromString<MutableList<Row>>(file.readText()) }
            .getOrElse { mutableListOf() }
    }

    // S108 (invariant #10): unique temp file + atomic replace, same pattern as
    // DraftStore and the macOS IME's LearnedStore. A plain writeText
    // interrupted mid-write corrupted learned.json; readAll then swallowed the
    // parse error into an empty list and the NEXT save persisted that empty
    // list — silent loss of the whole shared learning brain.
    //
    // The temp NAME must be unique per write, because ~/.banglu is shared by
    // several Banglu PROCESSES: the Windows typer (windows-ime's WinStorage)
    // writes this same learned.json, and typing Bangla INTO this editor with
    // that app is the expected configuration on Windows. `synchronized(lock)`
    // below is an in-process lock and buys nothing across processes — with a
    // fixed temp name two processes could truncate and write the same temp
    // file concurrently, and the move would then promote a truncated or
    // interleaved file over learned.json, which is exactly the corruption
    // this pattern exists to prevent.
    private fun writeRows(rows: List<Row>) {
        val tmp = java.nio.file.Files.createTempFile(dir.toPath(), "learned", ".tmp")
        try {
            tmp.toFile().writeText(json.encodeToString(rows))
            try {
                java.nio.file.Files.move(
                    tmp, file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                // Filesystem without atomic move: still replace — non-atomic beats stale.
                java.nio.file.Files.move(
                    tmp, file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            // A successful move consumed the path; this only cleans up after a
            // failed write or a failed move, so a unique name can never leave
            // litter behind in the user's ~/.banglu.
            runCatching { java.nio.file.Files.deleteIfExists(tmp) }
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

/** Installer resources -> ~/.banglu -> repo dev path. */
fun findDictionaryFile(): File {
    System.getProperty("compose.application.resources.dir")?.let {
        File(it, "dictionary.sqlite").takeIf(File::exists)?.let { f -> return f }
    }
    File(System.getProperty("user.home"), ".banglu/dictionary.sqlite")
        .takeIf(File::exists)?.let { return it }
    return JvmSqliteDictionaryLoader.findDictionarySqlite()
}
