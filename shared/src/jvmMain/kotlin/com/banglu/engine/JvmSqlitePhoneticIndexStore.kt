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
 * Mirrors android-keyboard's SqlitePhoneticIndexStore semantics.
 */
class JvmSqlitePhoneticIndexStore(private val dbFile: File) : PhoneticIndexStore {

    private val conn: Connection =
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")

    val isAvailable: Boolean = dbFile.exists()

    override fun lookupExact(key: String): List<PhoneticIndexHit> =
        conn.prepareStatement(
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

    override fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit> {
        if (limit <= 0) return emptyList()
        return conn.prepareStatement(
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

    override fun lookupEnglish(key: String): String? =
        conn.prepareStatement("SELECT bengali FROM english_lexicon WHERE key = ? LIMIT 1")
            .use { st ->
                st.setString(1, key)
                st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }

    override fun containsWord(bengali: String): Boolean =
        conn.prepareStatement("SELECT 1 FROM words WHERE bengali = ? LIMIT 1").use { st ->
            st.setString(1, bengali)
            st.executeQuery().use { rs -> rs.next() }
        }

    // ── S102: extended dictionary served from sqlite (trie retirement) ───

    private val extendedAvailable: Boolean by lazy {
        conn.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name='extended_phonetics' LIMIT 1"
        ).use { st -> st.executeQuery().use { rs -> rs.next() } }
    }

    override fun hasExtendedData(): Boolean = extendedAvailable

    override fun lookupExtendedExact(key: String): List<ExtendedDictionaryHit> =
        conn.prepareStatement(
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

    override fun lookupExtendedPrefix(prefix: String, limit: Int): List<ExtendedDictionaryHit> {
        if (limit <= 0) return emptyList()
        // Range form instead of LIKE so the phonetic index is always used.
        return conn.prepareStatement(
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

    override fun extendedPhoneticForBengali(bengali: String): String? =
        conn.prepareStatement(
            """SELECT p.phonetic FROM extended_dictionary e
               JOIN extended_phonetics p ON p.entry_id = e.id
               WHERE e.bengali = ? ORDER BY p.rowid LIMIT 1"""
        ).use { st ->
            st.setString(1, bengali)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    fun close() = conn.close()
}
