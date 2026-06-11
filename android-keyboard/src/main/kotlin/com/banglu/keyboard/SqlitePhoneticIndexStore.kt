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
 * Tables used (db version 3.3.0):
 * - phonetic_index(key, word_id, frequency, tier) with idx_phonetic_index_key(key, tier)
 * - words(id, bengali, frequency) — Bengali text lives here, joined by word_id
 * - english_lexicon(key PRIMARY KEY, bengali)
 */
class SqlitePhoneticIndexStore(dbFile: File) : PhoneticIndexStore {

    companion object {
        private const val TAG = "BangluPhoneticIndex"
    }

    private val db: SQLiteDatabase? = try {
        if (!dbFile.exists()) null
        else SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            .takeIf { database ->
                database.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='phonetic_index'",
                    null
                ).use { c -> c.moveToFirst() }.also { ok -> if (!ok) database.close() }
            }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "Failed to open phonetic index", e)
        null
    }

    /** True when the db opened and contains the phonetic_index table. */
    val isAvailable: Boolean get() = db != null

    override fun lookupExact(key: String): List<PhoneticIndexHit> = query(
        """SELECT w.bengali, p.frequency, p.tier FROM phonetic_index p
           JOIN words w ON w.id = p.word_id
           WHERE p.key = ? ORDER BY p.frequency DESC LIMIT 16""",
        arrayOf(key)
    )

    override fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit> {
        if (limit <= 0 || prefix.isEmpty()) return emptyList()
        return query(
            """SELECT w.bengali, p.frequency, p.tier FROM phonetic_index p
               JOIN words w ON w.id = p.word_id
               WHERE p.key >= ? AND p.key < ? AND p.tier = ${PhoneticIndexHit.TIER_A}
               ORDER BY p.frequency DESC LIMIT ?""",
            arrayOf(prefix, prefix + '￿', limit.toString())
        )
    }

    override fun lookupEnglish(key: String): String? = try {
        db?.rawQuery("SELECT bengali FROM english_lexicon WHERE key = ? LIMIT 1", arrayOf(key))
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "english lookup failed", e)
        null
    }

    private fun query(sql: String, args: Array<String>): List<PhoneticIndexHit> = try {
        db?.rawQuery(sql, args)?.use { c ->
            val hits = ArrayList<PhoneticIndexHit>(c.count)
            while (c.moveToNext()) hits.add(PhoneticIndexHit(c.getString(0), c.getInt(1), c.getInt(2)))
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
