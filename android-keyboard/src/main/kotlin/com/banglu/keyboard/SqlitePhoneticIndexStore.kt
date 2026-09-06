package com.banglu.keyboard

import android.database.Cursor
import android.database.CursorWindow
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import com.banglu.engine.platform.ExtendedDictionaryHit
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.platform.PhoneticIndexStore
import java.io.File

/**
 * Sqlite-backed phonetic index with a persistent read-only connection.
 * Open once per IME session; call close() from the service's onDestroy.
 * All methods fail soft (empty results) so a corrupt db never crashes the IME.
 *
 * Tables used (db version 3.4.1):
 * - phonetic_index(key, word_id, frequency, tier, priority) with idx_phonetic_index_key(key, tier, priority)
 * - words(id, bengali, frequency) — Bengali text lives here, joined by word_id
 * - english_lexicon(key PRIMARY KEY, bengali)
 *
 * @param dbFile         The SQLite database file to open (must contain phonetic_index table).
 * @param requiredVersion The metadata version string that must match; mismatch closes the
 *                        connection immediately and makes [isAvailable] false (fail-soft).
 */
class SqlitePhoneticIndexStore(
    private val dbFile: File,
    private val requiredVersion: String = AndroidDictionaryLoader.REQUIRED_DB_VERSION
) : PhoneticIndexStore {

    companion object {
        private const val TAG = "BangluPhoneticIndex"

        /** Upper-bound sentinel for prefix range queries (highest Unicode scalar in BMP). */
        private const val KEY_UPPER_BOUND = '\uFFFF'

        /** S195: window for point queries (≤ 16 short rows). Android's default
         *  CursorWindow is 2 MB per cursor; the engine issues hundreds of point
         *  queries per keystroke, and the freed 2 MB chunks stayed cached in the
         *  native allocator — the S22 timeline showed the native heap growing
         *  from 34 MB to 324 MB across three 26-letter bursts (134 → 306 MB of
         *  it "free" but retained) and the release smoke sampling 323 MB. */
        private const val POINT_WINDOW_BYTES = 64L * 1024L

        /** S195: negative index sizes — the S144 JVM values (1.66M distinct
         *  index keys → 32M bits = 4 MB; 396K extended phonetics / 131K
         *  extended words → 8M bits = 1 MB each; 4 hashes ≈ 1–2 % false
         *  positives, and a false positive only costs the sqlite query that
         *  used to run every time). */
        private const val KEY_BLOOM_BITS = 1 shl 25
        private const val EXT_BLOOM_BITS = 1 shl 23
        private const val BLOOM_HASHES = 4
    }

    // S195 (heapprofd on the S22, after the regex/CloseGuard fixes): ~8,000
    // point queries for 52 keystrokes — every miss of the typo/lattice probes
    // was a sqlite round trip with its own cursor window and ephemeral sort
    // table. The S144 negative index (JVM store) answers the misses from
    // memory; until it is built every query goes to sqlite exactly as before.
    // No false negatives by construction. Built on a daemon thread at minimum
    // priority on its OWN read connection so the engine lane never waits.
    @Volatile private var keyBloom: com.banglu.engine.util.BloomFilter? = null
    @Volatile private var extBloom: com.banglu.engine.util.BloomFilter? = null
    @Volatile private var extBengaliBloom: com.banglu.engine.util.BloomFilter? = null
    private val extPhoneticMemo = com.banglu.engine.util.LruCache<String, String>(2048)
    @Volatile private var englishKeysLoaded = false

    /** Diagnostic: true once index misses are answered from memory. */
    val negativeIndexReady: Boolean get() = keyBloom != null

    private fun buildBlooms() {
        try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { c ->
                val keys = com.banglu.engine.util.BloomFilter(KEY_BLOOM_BITS, BLOOM_HASHES)
                // Plain scan, no DISTINCT: a DISTINCT/GROUP BY over 1.8M rows
                // sorts them in sqlite's temp store (the S169b spike); Bloom
                // adds are idempotent so duplicates cost nothing.
                c.rawQuery("SELECT key FROM phonetic_index", null).use { cur ->
                    while (cur.moveToNext()) keys.add(cur.getString(0))
                }
                keyBloom = keys
                if (extendedAvailable) {
                    val ext = com.banglu.engine.util.BloomFilter(EXT_BLOOM_BITS, BLOOM_HASHES)
                    c.rawQuery("SELECT phonetic FROM extended_phonetics", null).use { cur ->
                        while (cur.moveToNext()) ext.add(cur.getString(0))
                    }
                    extBloom = ext
                    val extBengali = com.banglu.engine.util.BloomFilter(EXT_BLOOM_BITS, BLOOM_HASHES)
                    c.rawQuery("SELECT bengali FROM extended_dictionary", null).use { cur ->
                        while (cur.moveToNext()) extBengali.add(cur.getString(0))
                    }
                    extBengaliBloom = extBengali
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "negative index build failed — store keeps querying sqlite", e)
        }
    }

    private val pointCursorFactory: SQLiteDatabase.CursorFactory? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SQLiteDatabase.CursorFactory { _, driver, editTable, query ->
                SQLiteCursor(driver, editTable, query).also {
                    it.setWindow(CursorWindow("banglu-point", POINT_WINDOW_BYTES))
                }
            }
        } else null

    /** A point query with a small native window (see [POINT_WINDOW_BYTES]). */
    private fun pointQuery(sql: String, args: Array<String>?): Cursor? {
        val d = db ?: return null
        val factory = pointCursorFactory ?: return d.rawQuery(sql, args)
        return d.rawQueryWithFactory(factory, sql, args, "")
    }

    private val db: SQLiteDatabase? = run {
        if (!dbFile.exists()) return@run null
        var opened: SQLiteDatabase? = null
        try {
            opened = SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
            // Verify the phonetic_index table exists.
            val hasTable = opened.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='phonetic_index'",
                null
            ).use { c -> c.moveToFirst() }
            if (!hasTable) {
                opened.close()
                return@run null
            }
            // Verify the db version matches what we expect.
            val dbVersion = try {
                opened.rawQuery(
                    "SELECT value FROM metadata WHERE key='version' LIMIT 1", null
                ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            } catch (_: Exception) { null }
            if (dbVersion != requiredVersion) {
                if (BuildConfig.DEBUG) Log.w(
                    TAG,
                    "Version mismatch: expected $requiredVersion, got $dbVersion — phonetic index unavailable"
                )
                opened.close()
                return@run null
            }
            opened
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to open phonetic index", e)
            try { opened?.close() } catch (_: Exception) { /* ignore */ }
            null
        }
    }

    /** True when the db opened, contains phonetic_index, and has the expected version. */
    val isAvailable: Boolean get() = db != null

    /**
     * S4/C1 tier-first key ranking: (tier ASC, priority ASC, frequency DESC).
     * A Tier-A (real-usage) word beats a Tier-B junk word even when the junk
     * word canonically owns the key (bishas → বিশ্বাস before বিষাস); within a
     * tier, canonical (priority 0) beats habit alias; frequency breaks ties.
     */
    override fun lookupExact(key: String): List<PhoneticIndexHit> {
        keyBloom?.let { if (!it.mightContain(key)) return emptyList() }
        return lookupExactSqlite(key)
    }

    private fun lookupExactSqlite(key: String): List<PhoneticIndexHit> = query(
        """SELECT w.bengali, p.frequency, p.tier, p.priority FROM phonetic_index p
           JOIN words w ON w.id = p.word_id
           WHERE p.key = ? ORDER BY p.tier ASC, p.priority ASC, p.frequency DESC LIMIT 16""",
        arrayOf(key)
    )

    override fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit> {
        if (limit <= 0 || prefix.isEmpty()) return emptyList()
        return query(
            """SELECT w.bengali, p.frequency, p.tier, p.priority FROM phonetic_index p
               JOIN words w ON w.id = p.word_id
               WHERE p.key >= ? AND p.key < ? AND p.tier = ?
               ORDER BY p.tier ASC, p.priority ASC, p.frequency DESC LIMIT ?""",
            arrayOf(prefix, prefix + KEY_UPPER_BOUND, PhoneticIndexHit.TIER_A.toString(), limit.toString())
        )
    }

    /** S143: loaded once per store (~39K short keys, ~1 MB); fail-soft empty. */
    private val englishKeySet: Set<String> by lazy {
        try {
            HashSet<String>(65536).also { keys ->
                db?.rawQuery("SELECT key FROM english_lexicon", null)?.use { c ->
                    while (c.moveToNext()) keys.add(c.getString(0))
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "english keys failed", e)
            emptySet()
        }
    }
    override fun englishKeys(): Set<String> = englishKeySet.also { englishKeysLoaded = it.isNotEmpty() }

    override fun lookupEnglish(key: String): String? = try {
        // S195: once the spelling rescue has loaded the key set, a miss never
        // touches sqlite (same short circuit as the JVM store).
        if (englishKeysLoaded && key !in englishKeySet) null
        else pointQuery("SELECT bengali FROM english_lexicon WHERE key = ? LIMIT 1", arrayOf(key))
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "english lookup failed", e)
        null
    }

    /**
     * Lite-mode commit gate support: word membership straight from the words
     * table (idx_words_bengali → O(log n) point lookup). Fail-soft false so a
     * corrupt db floors OOV output rather than crashing the IME; the engine
     * memoizes results so repeated gate evaluations of one word hit sqlite once.
     */
    override fun containsWord(bengali: String): Boolean = try {
        pointQuery("SELECT 1 FROM words WHERE bengali = ? LIMIT 1", arrayOf(bengali))
            ?.use { c -> c.moveToFirst() } ?: false
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "containsWord lookup failed", e)
        false
    }

    // ── S102: extended dictionary served from sqlite instead of the in-RAM
    //    trie (~70-90MB of full-mode heap). Same fail-soft posture as every
    //    other query; the engine memoizes on the async lane. ───────────────

    private val extendedAvailable: Boolean by lazy {
        try {
            db?.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='extended_phonetics' LIMIT 1",
                null
            )?.use { c -> c.moveToFirst() } ?: false
        } catch (_: Exception) {
            false
        }
    }

    override fun hasExtendedData(): Boolean = extendedAvailable

    override fun lookupExtendedExact(key: String): List<ExtendedDictionaryHit> {
        extBloom?.let { if (!it.mightContain(key)) return emptyList() }
        return lookupExtendedExactSqlite(key)
    }

    private fun lookupExtendedExactSqlite(key: String): List<ExtendedDictionaryHit> = queryExtended(
        """SELECT p.phonetic, e.bengali, e.frequency FROM extended_phonetics p
           JOIN extended_dictionary e ON e.id = p.entry_id
           WHERE p.phonetic = ? ORDER BY e.frequency DESC LIMIT 16""",
        arrayOf(key)
    )

    override fun lookupExtendedPrefix(prefix: String, limit: Int): List<ExtendedDictionaryHit> {
        if (limit <= 0 || prefix.isEmpty()) return emptyList()
        return queryExtended(
            """SELECT p.phonetic, e.bengali, e.frequency FROM extended_phonetics p
               JOIN extended_dictionary e ON e.id = p.entry_id
               WHERE p.phonetic >= ? AND p.phonetic < ?
               ORDER BY e.frequency DESC LIMIT ?""",
            arrayOf(prefix, prefix + KEY_UPPER_BOUND, limit.toString())
        )
    }

    override fun extendedPhoneticForBengali(bengali: String): String? {
        extBengaliBloom?.let { if (!it.mightContain(bengali)) return null }
        extPhoneticMemo[bengali]?.let { return it.ifEmpty { null } }
        val result = extendedPhoneticForBengaliSqlite(bengali)
        extPhoneticMemo[bengali] = result ?: ""
        return result
    }

    private fun extendedPhoneticForBengaliSqlite(bengali: String): String? = try {
        pointQuery(
            """SELECT p.phonetic FROM extended_dictionary e
               JOIN extended_phonetics p ON p.entry_id = e.id
               WHERE e.bengali = ? ORDER BY p.rowid LIMIT 1""",
            arrayOf(bengali)
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "extended reverse lookup failed", e)
        null
    }

    private fun queryExtended(sql: String, args: Array<String>): List<ExtendedDictionaryHit> = try {
        pointQuery(sql, args)?.use { c ->
            val hits = ArrayList<ExtendedDictionaryHit>()
            while (c.moveToNext()) {
                hits.add(ExtendedDictionaryHit(c.getString(0), c.getString(1), c.getInt(2)))
            }
            hits
        } ?: emptyList()
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "Extended query failed", e)
        emptyList()
    }

    private fun query(sql: String, args: Array<String>): List<PhoneticIndexHit> = try {
        pointQuery(sql, args)?.use { c ->
            val hits = ArrayList<PhoneticIndexHit>()
            while (c.moveToNext()) {
                hits.add(PhoneticIndexHit(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3)))
            }
            hits
        } ?: emptyList()
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "Index query failed", e)
        emptyList()
    }

    init {
        // Runs after every property above (db included) is initialised.
        if (db != null) {
            Thread({ buildBlooms() }, "banglu-store-bloom").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }.start()
        }
    }

    /** Close the persistent connection. Queries after close fail soft to empty results. */
    fun close() {
        try {
            db?.close()
        } catch (_: Exception) {
            // Already closed or in teardown; nothing to do.
        }
    }
}
