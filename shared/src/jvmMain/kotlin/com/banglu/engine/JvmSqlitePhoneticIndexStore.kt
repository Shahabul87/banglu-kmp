package com.banglu.engine

import com.banglu.engine.platform.ExtendedDictionaryHit
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.platform.PhoneticIndexStore
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * S48: JDBC-backed phonetic index store for desktop — per-query lookups
 * against the FULL compiled dictionary (no in-memory 1.35M-row load).
 * Mirrors android-keyboard's SqlitePhoneticIndexStore semantics, including
 * (S108) the two hardening rules the Android store always had:
 * - the db must contain phonetic_index AND carry [DictionaryVersion.REQUIRED],
 *   else the store reports unavailable instead of serving stale vocabulary;
 * - a query failure degrades to an empty result (rule-fallback conversion),
 *   never an exception on the conversion path — SmartEngine has no catch
 *   blocks by design, so the store contract is "never throw".
 */
class JvmSqlitePhoneticIndexStore(private val dbFile: File) : PhoneticIndexStore {

    private val conn: Connection? = openValidated()

    /** True when the db opened, contains phonetic_index, and has the expected version. */
    val isAvailable: Boolean get() = conn != null

    private fun openValidated(): Connection? {
        if (!dbFile.exists()) return null
        var opened: Connection? = null
        return try {
            opened = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
            val hasTable = opened.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='phonetic_index' LIMIT 1"
            ).use { st -> st.executeQuery().use { rs -> rs.next() } }
            if (!hasTable) {
                System.err.println(
                    "JvmSqlitePhoneticIndexStore: no phonetic_index table in ${dbFile.absolutePath} — store unavailable"
                )
                opened.close()
                return null
            }
            val version = opened.prepareStatement(
                "SELECT value FROM metadata WHERE key='version' LIMIT 1"
            ).use { st -> st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null } }
            if (version != DictionaryVersion.REQUIRED) {
                System.err.println(
                    "JvmSqlitePhoneticIndexStore: version mismatch — expected " +
                        "${DictionaryVersion.REQUIRED}, got $version (${dbFile.absolutePath}) — store unavailable"
                )
                opened.close()
                return null
            }
            opened
        } catch (e: Exception) {
            System.err.println("JvmSqlitePhoneticIndexStore: failed to open ${dbFile.absolutePath}: $e")
            runCatching { opened?.close() }
            null
        }
    }

    private inline fun <T> safeQuery(fallback: T, block: (Connection) -> T): T {
        val c = conn ?: return fallback
        return try {
            block(c)
        } catch (e: Exception) {
            // Same law as Android's store: degrade to rule fallback, never
            // crash a conversion mid-keystroke.
            System.err.println("JvmSqlitePhoneticIndexStore: query failed: $e")
            fallback
        }
    }

    override fun lookupExact(key: String): List<PhoneticIndexHit> = safeQuery(emptyList()) { c ->
        c.prepareStatement(
            """SELECT w.bengali, p.frequency, p.tier, p.priority
               FROM phonetic_index p JOIN words w ON w.id = p.word_id
               WHERE p.key = ?
               ORDER BY p.tier ASC, p.priority ASC, p.frequency DESC LIMIT 24"""
        ).use { st ->
            st.setString(1, key)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        PhoneticIndexHit(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4))
                    )
                }
            }
        }
    }

    override fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit> {
        if (limit <= 0) return emptyList()
        return safeQuery(emptyList()) { c ->
            c.prepareStatement(
                """SELECT w.bengali, p.frequency, p.tier, p.priority
                   FROM phonetic_index p JOIN words w ON w.id = p.word_id
                   WHERE p.tier = 0 AND p.key GLOB ?
                   ORDER BY p.frequency DESC LIMIT ?"""
            ).use { st ->
                st.setString(1, prefix.replace("[", "[[]") + "*")
                st.setInt(2, limit)
                st.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(
                            PhoneticIndexHit(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4))
                        )
                    }
                }
            }
        }
    }

    override fun lookupEnglish(key: String): String? = safeQuery(null) { c ->
        c.prepareStatement("SELECT bengali FROM english_lexicon WHERE key = ? LIMIT 1")
            .use { st ->
                st.setString(1, key)
                st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
    }

    override fun containsWord(bengali: String): Boolean = safeQuery(false) { c ->
        c.prepareStatement("SELECT 1 FROM words WHERE bengali = ? LIMIT 1").use { st ->
            st.setString(1, bengali)
            st.executeQuery().use { rs -> rs.next() }
        }
    }

    // ── S102: extended dictionary served from sqlite (trie retirement) ───

    private val extendedAvailable: Boolean by lazy {
        safeQuery(false) { c ->
            c.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='extended_phonetics' LIMIT 1"
            ).use { st -> st.executeQuery().use { rs -> rs.next() } }
        }
    }

    override fun hasExtendedData(): Boolean = extendedAvailable

    override fun lookupExtendedExact(key: String): List<ExtendedDictionaryHit> =
        safeQuery(emptyList()) { c ->
            c.prepareStatement(
                """SELECT p.phonetic, e.bengali, e.frequency
                   FROM extended_phonetics p JOIN extended_dictionary e ON e.id = p.entry_id
                   WHERE p.phonetic = ?
                   ORDER BY e.frequency DESC LIMIT 24"""
            ).use { st ->
                st.setString(1, key)
                st.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(
                            ExtendedDictionaryHit(rs.getString(1), rs.getString(2), rs.getInt(3))
                        )
                    }
                }
            }
        }

    override fun lookupExtendedPrefix(prefix: String, limit: Int): List<ExtendedDictionaryHit> {
        if (limit <= 0) return emptyList()
        // Range form instead of LIKE so the phonetic index is always used.
        return safeQuery(emptyList()) { c ->
            c.prepareStatement(
                """SELECT p.phonetic, e.bengali, e.frequency
                   FROM extended_phonetics p JOIN extended_dictionary e ON e.id = p.entry_id
                   WHERE p.phonetic >= ? AND p.phonetic < ?
                   ORDER BY e.frequency DESC LIMIT ?"""
            ).use { st ->
                st.setString(1, prefix)
                st.setString(2, prefix + '￿')
                st.setInt(3, limit)
                st.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(
                            ExtendedDictionaryHit(rs.getString(1), rs.getString(2), rs.getInt(3))
                        )
                    }
                }
            }
        }
    }

    override fun extendedPhoneticForBengali(bengali: String): String? = safeQuery(null) { c ->
        c.prepareStatement(
            """SELECT p.phonetic FROM extended_dictionary e
               JOIN extended_phonetics p ON p.entry_id = e.id
               WHERE e.bengali = ? ORDER BY p.rowid LIMIT 1"""
        ).use { st ->
            st.setString(1, bengali)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    fun close() {
        runCatching { conn?.close() }
    }
}
