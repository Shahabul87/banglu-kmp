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
            file.writeText(json.encodeToString(rows))
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
