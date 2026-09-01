package com.banglu.keyboard

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.banglu.engine.DictionaryVersion
import com.banglu.engine.glide.GlideEnglishWords
import com.banglu.engine.glide.GlideGrid
import com.banglu.engine.glide.GlideLexicon
import java.io.File

/**
 * S163: builds and caches the glide template lexicons on-device.
 *
 * - BN: top canonical romans from the SAME dictionary file the engine store
 *   uses, via an INDEPENDENT read-only connection opened only for the one
 *   build (the engine's own store and lanes are never touched).
 * - EN: the shared English wordlist.
 * - Cache: version-stamped binary in filesDir (tmp + atomic rename); a
 *   dictionary upgrade invalidates it via the version gate.
 * - All entry points are plain blocking functions the service calls from
 *   engineLane — NEVER the main thread. Failures log and return null:
 *   glide silently unavailable, the keyboard never crashes for it.
 */
class GlideLexiconStore(
    private val filesDir: File,
    private val liteMode: Boolean,
) {
    @Volatile private var bangla: GlideLexicon? = null
    @Volatile private var english: GlideLexicon? = null
    private val grid = GlideGrid()

    fun banglaLexicon(): GlideLexicon? {
        bangla?.let { return it }
        synchronized(this) {
            bangla?.let { return it }
            val cap = if (liteMode) LITE_BN_CAP else BN_CAP
            val built = fromCache(BN_CACHE) ?: buildBangla(cap)?.also { toCache(BN_CACHE, it) }
            bangla = built
            return built
        }
    }

    fun englishLexicon(): GlideLexicon? {
        english?.let { return it }
        synchronized(this) {
            english?.let { return it }
            val built = fromCache(EN_CACHE)
                ?: runCatching { GlideLexicon.build(selectTop(GlideEnglishWords.top(EN_CAP), EN_CAP), grid) }
                    .getOrNull()?.also { toCache(EN_CACHE, it) }
            english = built
            return built
        }
    }

    /** S72 memory pressure: drop the in-memory copies; cache files stay. */
    fun dropForMemoryPressure() {
        bangla = null
        english = null
    }

    private fun buildBangla(cap: Int): GlideLexicon? {
        val dbFile = File(filesDir, "dictionary.sqlite")
        if (!dbFile.exists()) return null
        return try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val words = ArrayList<Pair<String, Int>>(cap)
                db.rawQuery(
                    """SELECT key, MAX(frequency) f FROM phonetic_index
                       WHERE priority = 0 GROUP BY key
                       ORDER BY f DESC LIMIT ${cap * 2}""",
                    null
                ).use { c ->
                    while (c.moveToNext() && words.size < cap) {
                        val w = c.getString(0) ?: continue
                        if (w.length < 2 || !w.all { it in 'a'..'z' }) continue
                        words.add(w to c.getInt(1))
                    }
                }
                GlideLexicon.build(words, grid)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Glide BN lexicon build failed", e)
            null
        }
    }

    private fun fromCache(name: String): GlideLexicon? = try {
        val f = File(filesDir, name)
        if (f.exists()) GlideLexicon.deserialize(f.readBytes(), DictionaryVersion.REQUIRED) else null
    } catch (_: Throwable) {
        null
    }

    private fun toCache(name: String, lex: GlideLexicon) {
        try {
            val tmp = File(filesDir, "$name.tmp")
            tmp.writeBytes(GlideLexicon.serialize(lex, DictionaryVersion.REQUIRED))
            val dst = File(filesDir, name)
            if (!tmp.renameTo(dst)) {
                dst.delete()
                tmp.renameTo(dst)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Glide lexicon cache write failed", e)
        }
    }

    companion object {
        private const val TAG = "GlideLexiconStore"
        private const val BN_CACHE = "glide_bn.bin"
        private const val EN_CACHE = "glide_en.bin"
        const val BN_CAP = 50_000
        const val LITE_BN_CAP = 20_000
        const val EN_CAP = 30_000

        /** Eligibility filter, unit-tested: a-z only, length >= 2, capped. */
        fun selectTop(words: List<Pair<String, Int>>, cap: Int): List<Pair<String, Int>> =
            words.asSequence()
                .filter { (w, _) -> w.length >= 2 && w.all { it in 'a'..'z' } }
                .take(cap)
                .toList()
    }
}
