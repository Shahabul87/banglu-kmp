package com.banglu.engine

import com.banglu.engine.platform.DictionaryLoader
import com.banglu.engine.types.BigramModelData
import com.banglu.engine.types.SmartDictionaryEntry
import com.banglu.engine.types.WordCategory
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
        return withConnection { conn ->
            if (!conn.hasTable("extended_dictionary") || !conn.hasTable("extended_phonetics")) {
                return@withConnection null
            }

            val entries = mutableMapOf<Int, MutableExtendedEntry>()
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                    "SELECT id, bengali, frequency, category FROM extended_dictionary"
                ).use { rs ->
                    while (rs.next()) {
                        val id = rs.getInt("id")
                        entries[id] = MutableExtendedEntry(
                            bengali = rs.getString("bengali"),
                            frequency = rs.getInt("frequency"),
                            category = parseWordCategory(rs.getString("category")),
                        )
                    }
                }
            }

            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT phonetic, entry_id FROM extended_phonetics").use { rs ->
                    while (rs.next()) {
                        entries[rs.getInt("entry_id")]
                            ?.phonetics
                            ?.add(rs.getString("phonetic"))
                    }
                }
            }

            val result = entries.values.mapNotNull { entry ->
                if (entry.phonetics.isEmpty()) return@mapNotNull null
                SmartDictionaryEntry(
                    bengali = entry.bengali,
                    phonetics = entry.phonetics.distinct(),
                    frequency = entry.frequency,
                    category = entry.category,
                )
            }

            println("TestDictionaryLoader: Loaded ${result.size} extended dictionary entries")
            result.ifEmpty { null }
        }
    }

    override suspend fun loadBigramModel(): BigramModelData? {
        return withConnection { conn ->
            if (!conn.hasTable("bigram_unigrams") || !conn.hasTable("bigram_pairs")) {
                return@withConnection null
            }

            val unigrams = mutableMapOf<String, Int>()
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT word, count FROM bigram_unigrams").use { rs ->
                    while (rs.next()) {
                        unigrams[rs.getString("word")] = rs.getInt("count")
                    }
                }
            }

            val bigrams = mutableMapOf<String, Int>()
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT previous_word, next_word, count FROM bigram_pairs").use { rs ->
                    while (rs.next()) {
                        bigrams["${rs.getString("previous_word")}\t${rs.getString("next_word")}"] =
                            rs.getInt("count")
                    }
                }
            }

            println(
                "TestDictionaryLoader: Loaded ${unigrams.size} unigram entries and " +
                    "${bigrams.size} bigram entries"
            )

            if (unigrams.isEmpty() && bigrams.isEmpty()) {
                null
            } else {
                BigramModelData(
                    unigrams = unigrams,
                    bigrams = bigrams,
                    totalUnigrams = conn.loadMetadataInt("total_unigrams") ?: unigrams.values.sum(),
                    totalBigrams = conn.loadMetadataInt("total_bigrams") ?: bigrams.values.sum(),
                )
            }
        }
    }

    private fun Connection.hasTable(tableName: String): Boolean {
        prepareStatement(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1"
        ).use { stmt ->
            stmt.setString(1, tableName)
            stmt.executeQuery().use { rs ->
                return rs.next()
            }
        }
    }

    private fun Connection.loadMetadataInt(key: String): Int? {
        prepareStatement("SELECT value FROM metadata WHERE key = ? LIMIT 1").use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getString(1).toIntOrNull() else null
            }
        }
    }

    private fun parseWordCategory(raw: String?): WordCategory =
        raw
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { WordCategory.valueOf(it) }.getOrNull() }
            ?: WordCategory.UNKNOWN

    private data class MutableExtendedEntry(
        val bengali: String,
        val frequency: Int,
        val category: WordCategory,
        val phonetics: MutableList<String> = mutableListOf(),
    )
}
