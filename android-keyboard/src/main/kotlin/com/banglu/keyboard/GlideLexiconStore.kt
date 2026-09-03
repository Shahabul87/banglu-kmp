package com.banglu.keyboard

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.banglu.engine.DictionaryVersion
import com.banglu.engine.glide.GlideEnglishWords
import com.banglu.engine.glide.GlideSeedRomans
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
            // S171: cap-keyed cache — a lite store must never load a full-cap
            // file (seen on the 2 GB emulator). The pre-S171 single-name cache
            // is removed once so it stops occupying 4 MB of app storage.
            File(filesDir, LEGACY_BN_CACHE).takeIf { it.exists() }?.delete()
            val name = banglaCacheName(liteMode)
            val built = fromCache(name) ?: buildBangla(cap)?.also { toCache(name, it) }
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
                // S163b: ALL priorities — the chat register (korsi, issa,
                // bolbo, jabo…) lives in priority-1 habit-alias rows; users
                // glide the spellings they actually type. Engine shorthand
                // seeds (kmon, valo) never touch the index at all and join
                // first at everyday-band frequency.
                // S169 (first-install native-heap spike, heapprofd 2026-09-02):
                // the old `… ORDER BY f DESC LIMIT n` made SQLite sort every
                // key in memory (Android builds SQLite with in-memory temp
                // store): native heap 17 → 182 MB right after the dictionary
                // load — an LMK risk on 2 GB phones. GROUP BY alone walks
                // idx_phonetic_index_key in order (no temp b-tree); the
                // top-K happens here, bounded to `cap` entries.
                val seeds = LinkedHashMap<String, Int>()
                for ((w, f) in GlideSeedRomans.entries()) seeds[w] = f
                val merged = db.rawQuery(
                    "SELECT key, MAX(frequency) f FROM phonetic_index GROUP BY key",
                    null
                ).use { c ->
                    topKByFrequency(
                        rows = generateSequence { if (c.moveToNext()) (c.getString(0) ?: "") to c.getInt(1) else null },
                        seeds = seeds,
                        cap = cap
                    )
                }
                GlideLexicon.build(
                    GlideSeedRomans.expandVariants(merged.map { it.key to it.value }),
                    grid
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Glide BN lexicon build failed", e)
            null
        }
    }

    private fun fromCache(name: String): GlideLexicon? = try {
        val f = File(filesDir, name)
        if (f.exists()) GlideLexicon.deserialize(f.readBytes(), cacheStamp()) else null
    } catch (_: Throwable) {
        null
    }

    /** Dictionary version PLUS the lexicon-builder revision — a builder
     *  change (e.g. S163b alias inclusion) must invalidate old caches even
     *  when the dictionary itself did not move. */
    private fun cacheStamp() = "${DictionaryVersion.REQUIRED}#$LEXICON_REV"

    private fun toCache(name: String, lex: GlideLexicon) {
        try {
            val tmp = File(filesDir, "$name.tmp")
            tmp.writeBytes(GlideLexicon.serialize(lex, cacheStamp()))
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
        /** Bump when the SELECTION logic changes
         *  (2 = S163b alias-inclusive; 3 = +bh→v variants; 4 = S169 streaming
         *  top-K — same set, ties may order differently). */
        private const val LEXICON_REV = 4
        private const val LEGACY_BN_CACHE = "glide_bn.bin"
        /** S171: one cache file per cap. */
        fun banglaCacheName(liteMode: Boolean) = "glide_bn_${if (liteMode) LITE_BN_CAP else BN_CAP}.bin"
        private const val EN_CACHE = "glide_en.bin"
        const val BN_CAP = 50_000
        const val LITE_BN_CAP = 20_000
        const val EN_CAP = 30_000

        private fun eligible(w: String) = w.length >= 2 && w.all { it in 'a'..'z' }

        /**
         * S169: bounded top-K over a STREAM of (key, frequency) rows in any
         * order. Seeds are always kept (their frequency rises to a row's if
         * higher); the remaining `cap - seeds` slots go to the highest-
         * frequency eligible keys. Memory is O(cap), never O(rows).
         */
        fun topKByFrequency(
            rows: Sequence<Pair<String, Int>>,
            seeds: Map<String, Int>,
            cap: Int,
        ): LinkedHashMap<String, Int> {
            val kept = LinkedHashMap<String, Int>(seeds.size * 2)
            for ((w, f) in seeds) if (eligible(w)) kept[w] = f
            val slots = (cap - kept.size).coerceAtLeast(0)
            // Min-heap on frequency; ties broken by key so the result is stable.
            val heap = java.util.PriorityQueue<Pair<String, Int>>(slots + 1) { a, b ->
                if (a.second != b.second) a.second.compareTo(b.second) else b.first.compareTo(a.first)
            }
            for ((w, f) in rows) {
                if (!eligible(w)) continue
                val seedF = kept[w]
                if (seedF != null) { if (f > seedF) kept[w] = f; continue }
                if (slots == 0) continue
                if (heap.size < slots) heap.add(w to f)
                else if (f > heap.peek().second) { heap.poll(); heap.add(w to f) }
            }
            val top = ArrayList(heap); top.sortWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            for ((w, f) in top) kept[w] = f
            return kept
        }

        /** Eligibility filter, unit-tested: a-z only, length >= 2, capped. */
        fun selectTop(words: List<Pair<String, Int>>, cap: Int): List<Pair<String, Int>> =
            words.asSequence()
                .filter { (w, _) -> w.length >= 2 && w.all { it in 'a'..'z' } }
                .take(cap)
                .toList()
    }
}
