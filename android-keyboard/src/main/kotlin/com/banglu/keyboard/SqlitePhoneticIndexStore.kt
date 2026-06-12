package com.banglu.keyboard

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.platform.PhoneticIndexStore
import java.io.File

/**
 * Sqlite-backed phonetic index with a persistent read-only connection.
 * Open once per IME session; call close() from the service's onDestroy.
 * All methods fail soft (empty results) so a corrupt db never crashes the IME.
 *
 * Tables used (db version 3.4.0):
 * - phonetic_index(key, word_id, frequency, tier, priority) with idx_phonetic_index_key(key, priority, tier)
 * - words(id, bengali, frequency) — Bengali text lives here, joined by word_id
 * - english_lexicon(key PRIMARY KEY, bengali)
 *
 * @param dbFile         The SQLite database file to open (must contain phonetic_index table).
 * @param requiredVersion The metadata version string that must match; mismatch closes the
 *                        connection immediately and makes [isAvailable] false (fail-soft).
 */
class SqlitePhoneticIndexStore(
    dbFile: File,
    private val requiredVersion: String = AndroidDictionaryLoader.REQUIRED_DB_VERSION
) : PhoneticIndexStore {

    companion object {
        private const val TAG = "BangluPhoneticIndex"

        /** Upper-bound sentinel for prefix range queries (highest Unicode scalar in BMP). */
        private const val KEY_UPPER_BOUND = '\uFFFF'
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

    override fun lookupExact(key: String): List<PhoneticIndexHit> = query(
        """SELECT w.bengali, p.frequency, p.tier, p.priority FROM phonetic_index p
           JOIN words w ON w.id = p.word_id
           WHERE p.key = ? ORDER BY p.priority ASC, p.frequency DESC LIMIT 16""",
        arrayOf(key)
    )

    override fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit> {
        if (limit <= 0 || prefix.isEmpty()) return emptyList()
        return query(
            """SELECT w.bengali, p.frequency, p.tier, p.priority FROM phonetic_index p
               JOIN words w ON w.id = p.word_id
               WHERE p.key >= ? AND p.key < ? AND p.tier = ?
               ORDER BY p.frequency DESC LIMIT ?""",
            arrayOf(prefix, prefix + KEY_UPPER_BOUND, PhoneticIndexHit.TIER_A.toString(), limit.toString())
        )
    }

    override fun lookupEnglish(key: String): String? = try {
        db?.rawQuery("SELECT bengali FROM english_lexicon WHERE key = ? LIMIT 1", arrayOf(key))
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
        db?.rawQuery("SELECT 1 FROM words WHERE bengali = ? LIMIT 1", arrayOf(bengali))
            ?.use { c -> c.moveToFirst() } ?: false
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "containsWord lookup failed", e)
        false
    }

    private fun query(sql: String, args: Array<String>): List<PhoneticIndexHit> = try {
        db?.rawQuery(sql, args)?.use { c ->
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

    /** Close the persistent connection. Queries after close fail soft to empty results. */
    fun close() {
        try {
            db?.close()
        } catch (_: Exception) {
            // Already closed or in teardown; nothing to do.
        }
    }
}
