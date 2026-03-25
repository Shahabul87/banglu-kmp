package com.banglu.engine

import com.banglu.engine.platform.DictionaryLoader
import com.banglu.engine.types.BigramModelData
import com.banglu.engine.types.SmartDictionaryEntry
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * JVM test implementation of DictionaryLoader that reads from the local
 * dictionary.sqlite file using JDBC.
 *
 * This enables JVM tests to access the full 480K Bengali word list,
 * frequency data, and disambiguation map -- unlocking Layers 5.5, 5.7, and 6.
 *
 * @param dbFile Path to dictionary.sqlite (defaults to repo root)
 */
class TestDictionaryLoader(
    private val dbFile: File = findDictionarySqlite()
) : DictionaryLoader {

    companion object {
        /**
         * Locate dictionary.sqlite relative to the working directory.
         * Gradle runs tests with cwd = project root (banglu-kmp/) or module root (shared/).
         * We check both locations.
         */
        fun findDictionarySqlite(): File {
            val candidates = listOf(
                File("dictionary.sqlite"),                           // repo root (cwd = banglu-kmp)
                File("../dictionary.sqlite"),                        // cwd = shared/
                File("android-keyboard/src/main/assets/dictionary.sqlite"), // fallback
            )
            return candidates.firstOrNull { it.exists() }
                ?: error(
                    "dictionary.sqlite not found. Searched:\n" +
                        candidates.joinToString("\n") { "  ${it.absolutePath}" }
                )
        }
    }

    private fun openConnection(): Connection {
        require(dbFile.exists()) { "Dictionary database not found: ${dbFile.absolutePath}" }
        return DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    }

    private inline fun <T> withConnection(block: (Connection) -> T): T? {
        val conn = try {
            openConnection()
        } catch (e: Exception) {
            System.err.println("TestDictionaryLoader: Failed to open database: ${e.message}")
            return null
        }
        return try {
            block(conn)
        } finally {
            conn.close()
        }
    }

    override suspend fun loadFullDictionary(): List<String>? = withConnection { conn ->
        val words = mutableListOf<String>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT bengali FROM words").use { rs ->
                while (rs.next()) {
                    words.add(rs.getString(1))
                }
            }
        }
        println("TestDictionaryLoader: Loaded ${words.size} words from dictionary")
        if (words.isNotEmpty()) words else null
    }

    override suspend fun loadFrequencyMap(): Map<String, Int>? = withConnection { conn ->
        val freqs = mutableMapOf<String, Int>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT bengali, frequency FROM words WHERE frequency > 0").use { rs ->
                while (rs.next()) {
                    freqs[rs.getString(1)] = rs.getInt(2)
                }
            }
        }
        println("TestDictionaryLoader: Loaded ${freqs.size} frequency entries")
        if (freqs.isNotEmpty()) freqs else null
    }

    override suspend fun loadDisambiguationMap(): Map<String, String>? = withConnection { conn ->
        val map = mutableMapOf<String, String>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT wrong_form, correct_form FROM disambiguation").use { rs ->
                while (rs.next()) {
                    map[rs.getString(1)] = rs.getString(2)
                }
            }
        }
        println("TestDictionaryLoader: Loaded ${map.size} disambiguation entries")
        if (map.isNotEmpty()) map else null
    }

    override suspend fun loadExtendedDictionary(): List<SmartDictionaryEntry>? {
        // Extended phonetic entries are not in the SQLite database.
        return null
    }

    override suspend fun loadBigramModel(): BigramModelData? {
        // Bigram model is not in the SQLite database.
        return null
    }
}
