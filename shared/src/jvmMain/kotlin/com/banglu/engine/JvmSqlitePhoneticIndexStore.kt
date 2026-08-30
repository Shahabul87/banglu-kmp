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

    /**
     * S144 (Windows field report: backspace/space lag): the typo and lattice
     * layers probe hundreds of edit variants per keystroke, and every miss was
     * a sqlite point query — invisible on a warm Mac page cache, 50–500 ms on
     * an antivirus-hooked Windows disk. Two Bloom filters (phonetic_index keys,
     * extended_phonetics keys) answer the misses from memory; they are built
     * on their own read connection in a daemon thread so boot is not delayed,
     * and until they exist every query goes to sqlite exactly as before. No
     * false negatives by construction.
     */
    @Volatile private var keyBloom: com.banglu.engine.util.BloomFilter? = null
    @Volatile private var extBloom: com.banglu.engine.util.BloomFilter? = null
    /** Reverse direction (Bengali -> phonetic) — the suggestion scorer asks it
     *  once per candidate, ~100 times per keystroke. */
    @Volatile private var extBengaliBloom: com.banglu.engine.util.BloomFilter? = null
    private val extPhoneticMemo = com.banglu.engine.util.LruCache<String, String>(2048)

    init {
        if (conn != null) {
            Thread({ buildBlooms() }, "banglu-store-bloom").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
        }
    }

    private fun buildBlooms() {
        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { c ->
                val keys = com.banglu.engine.util.BloomFilter(KEY_BLOOM_BITS, BLOOM_HASHES)
                c.createStatement().use { st ->
                    st.executeQuery("SELECT DISTINCT key FROM phonetic_index").use { rs -> while (rs.next()) keys.add(rs.getString(1)) }
                }
                keyBloom = keys
                if (extendedAvailable) {
                    val ext = com.banglu.engine.util.BloomFilter(EXT_BLOOM_BITS, BLOOM_HASHES)
                    c.createStatement().use { st ->
                        st.executeQuery("SELECT DISTINCT phonetic FROM extended_phonetics").use { rs -> while (rs.next()) ext.add(rs.getString(1)) }
                    }
                    extBloom = ext
                    val extBengali = com.banglu.engine.util.BloomFilter(EXT_BLOOM_BITS, BLOOM_HASHES)
                    c.createStatement().use { st ->
                        st.executeQuery("SELECT DISTINCT bengali FROM extended_dictionary").use { rs -> while (rs.next()) extBengali.add(rs.getString(1)) }
                    }
                    extBengaliBloom = extBengali
                }
            }
        } catch (e: Exception) {
            System.err.println("JvmSqlitePhoneticIndexStore: bloom build failed (store keeps querying sqlite): $e")
        }
    }

    /** Test/diagnostic: true once the negative index answers misses from memory. */
    val negativeIndexReady: Boolean get() = keyBloom != null

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

    /** S144 diagnostic: sqlite queries actually issued (bloom misses excluded). */
    @Volatile var sqliteQueryCount: Long = 0L
        private set
    /** S144 diagnostic: queries per entry point (test/diagnostic only). */
    val sqliteQueriesByKind: MutableMap<String, Int> = java.util.concurrent.ConcurrentHashMap()
    private fun countKind(kind: String) { sqliteQueriesByKind.merge(kind, 1, Int::plus) }

    private inline fun <T> safeQuery(fallback: T, block: (Connection) -> T): T {
        val c = conn ?: return fallback
        sqliteQueryCount++
        return try {
            block(c)
        } catch (e: Exception) {
            // Same law as Android's store: degrade to rule fallback, never
            // crash a conversion mid-keystroke.
            System.err.println("JvmSqlitePhoneticIndexStore: query failed: $e")
            fallback
        }
    }

    override fun lookupExact(key: String): List<PhoneticIndexHit> {
        keyBloom?.let { if (!it.mightContain(key)) return emptyList() }
        return lookupExactSqlite(key)
    }

    private fun lookupExactSqlite(key: String): List<PhoneticIndexHit> = safeQuery(emptyList()) { c ->
        countKind("exact")
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
            countKind("prefix")
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

    @Volatile private var englishKeysLoaded = false
    private val englishKeySet: Set<String> by lazy {
        safeQuery(emptySet<String>()) { c ->
            HashSet<String>(65536).also { keys ->
                c.createStatement().use { st ->
                    st.executeQuery("SELECT key FROM english_lexicon").use { rs -> while (rs.next()) keys.add(rs.getString(1)) }
                }
            }
        }
    }
    override fun englishKeys(): Set<String> = englishKeySet.also { englishKeysLoaded = it.isNotEmpty() }

    override fun lookupEnglish(key: String): String? {
        // The key set is already in memory once the spelling rescue has run;
        // a miss then costs nothing (the lazy is NOT forced here — boot stays free).
        if (englishKeysLoaded && key !in englishKeySet) return null
        return lookupEnglishSqlite(key)
    }

    private fun lookupEnglishSqlite(key: String): String? = safeQuery(null) { c ->
        countKind("english")
        c.prepareStatement("SELECT bengali FROM english_lexicon WHERE key = ? LIMIT 1")
            .use { st ->
                st.setString(1, key)
                st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
    }

    override fun containsWord(bengali: String): Boolean = safeQuery(false) { c ->
        countKind("contains")
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

    override fun lookupExtendedExact(key: String): List<ExtendedDictionaryHit> {
        extBloom?.let { if (!it.mightContain(key)) return emptyList() }
        return lookupExtendedExactSqlite(key)
    }

    private fun lookupExtendedExactSqlite(key: String): List<ExtendedDictionaryHit> =
        safeQuery(emptyList()) { c ->
            countKind("extExact")
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
            countKind("extPrefix")
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

    override fun extendedPhoneticForBengali(bengali: String): String? {
        extBengaliBloom?.let { if (!it.mightContain(bengali)) return null }
        extPhoneticMemo[bengali]?.let { return it.ifEmpty { null } }
        val result = extendedPhoneticForBengaliSqlite(bengali)
        extPhoneticMemo[bengali] = result ?: ""
        return result
    }

    private fun extendedPhoneticForBengaliSqlite(bengali: String): String? = safeQuery(null) { c ->
        countKind("extPhoneticForBengali")
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

/** S144 bloom sizing: 1.65M keys in 32M bits × 4 hashes ≈ 0.4% false positives; 379K extended keys in 8M bits. */
private const val KEY_BLOOM_BITS = 1 shl 25
private const val EXT_BLOOM_BITS = 1 shl 23
private const val BLOOM_HASHES = 4
