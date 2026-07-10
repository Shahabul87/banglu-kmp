package com.banglu.keyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.banglu.engine.platform.DictionaryLoader
import com.banglu.engine.types.BigramModelData
import com.banglu.engine.types.SmartDictionaryEntry
import com.banglu.engine.types.WordCategory
import java.io.File

/**
 * Loads the full 480K Bengali dictionary from a bundled SQLite database.
 *
 * The dictionary.sqlite file is shipped in assets/ and copied to internal storage
 * on first use. Subsequent calls open the already-copied database directly.
 *
 * Tables used:
 * - words(id, bengali, frequency) - 480K Bengali words for validation and recovery
 * - disambiguation(wrong_form, correct_form) - 3,456 wrong-to-right character swaps
 */
class AndroidDictionaryLoader(
    private val context: Context,
    private val loadFullWordList: Boolean = true,
    private val loadExtendedEntries: Boolean = true,
    private val loadFrequencyScores: Boolean = true,
    private val loadDisambiguationData: Boolean = true,
    private val loadBigramData: Boolean = true
) : DictionaryLoader {

    companion object {
        private const val TAG = "BangluDictLoader"
        internal const val DB_FILENAME = "dictionary.sqlite"
        internal const val REQUIRED_DB_VERSION = "3.8.1"
    }

    /**
     * Ensure the database file exists in internal storage and has the correct version.
     * Triggers the lazy copy-from-assets logic if needed.
     *
     * This is safe to call independently of any load-flags; it performs only the
     * provisioning step (copy + version check) and returns the file when usable.
     * The call does disk I/O (up to ~104 MB asset copy on first install), so it
     * MUST be invoked from a background dispatcher (e.g. Dispatchers.IO).
     *
     * @return The database [File] if it exists and is the correct version, null otherwise.
     */
    fun ensureDatabaseFile(): File? {
        val file = dbFile  // triggers lazy copy if needed
        return if (file.exists()) file else null
    }

    /** Lazily ensure the database file exists in internal storage (copy from assets once). */
    private val dbFile: File by lazy {
        val file = File(context.filesDir, DB_FILENAME)
        if (!file.exists() || databaseVersion(file) != REQUIRED_DB_VERSION) {
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "Copying $DB_FILENAME from assets to ${file.absolutePath}")
                val tmpFile = File(context.filesDir, "$DB_FILENAME.tmp")
                context.assets.open(DB_FILENAME).use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tmpFile.length() > 0L) {
                    if (file.exists() && !file.delete()) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Could not delete old database before refresh")
                    }
                    if (!tmpFile.renameTo(file)) {
                        tmpFile.copyTo(file, overwrite = true)
                        tmpFile.delete()
                    }
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Copy complete (${file.length() / 1024 / 1024}MB)")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to copy database from assets", e)
            }
        }
        file
    }

    /**
     * Open the database, run the block, and close the database.
     * Returns null if the database cannot be opened.
     */
    private inline fun <T> withDatabase(block: (SQLiteDatabase) -> T): T? {
        if (!dbFile.exists()) return null
        val db = try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to open database", e)
            return null
        }
        return try {
            block(db)
        } finally {
            db.close()
        }
    }

    private fun databaseVersion(file: File): String? {
        if (!file.exists()) return null
        val db = try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            return null
        }
        return try {
            db.rawQuery("SELECT value FROM metadata WHERE key='version' LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null
        } finally {
            db.close()
        }
    }

    private fun SQLiteDatabase.hasTable(tableName: String): Boolean {
        rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
            arrayOf(tableName)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    override suspend fun loadFullDictionary(): List<String>? = if (!loadFullWordList) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Skipping full word list in low-memory dictionary mode")
        null
    } else withDatabase { db ->
        val words = mutableListOf<String>()
        try {
            db.rawQuery("SELECT bengali FROM words", null).use { cursor ->
                while (cursor.moveToNext()) {
                    words.add(cursor.getString(0))
                }
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${words.size} words from dictionary")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load full dictionary", e)
            return@withDatabase null
        } catch (oom: OutOfMemoryError) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Skipping full dictionary after OOM", oom)
            return@withDatabase null
        }
        if (words.isNotEmpty()) words else null
    }

    override suspend fun loadFrequencyMap(): Map<String, Int>? = if (!loadFrequencyScores) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Skipping frequency map in lite dictionary mode")
        null
    } else withDatabase { db ->
        val freqs = mutableMapOf<String, Int>()
        try {
            // S4/C2: frequency > 1, not > 0 — 328K of 472K rows carry the
            // corpus-tail placeholder frequency 1 (real signal starts at 10),
            // and 1-vs-0 crosses no ranking threshold anywhere in the engine.
            // Materializing those rows tripled the map and OOMed the 256MB
            // device heap during full-mode load.
            db.rawQuery("SELECT bengali, frequency FROM words WHERE frequency > 1", null).use { cursor ->
                while (cursor.moveToNext()) {
                    freqs[cursor.getString(0)] = cursor.getInt(1)
                }
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${freqs.size} frequency entries")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load frequency map", e)
            return@withDatabase null
        } catch (oom: OutOfMemoryError) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Skipping frequency map after OOM", oom)
            return@withDatabase null
        }
        if (freqs.isNotEmpty()) freqs else null
    }

    override suspend fun loadDisambiguationMap(): Map<String, String>? = if (!loadDisambiguationData) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Skipping disambiguation map in lite dictionary mode")
        null
    } else withDatabase { db ->
        val map = mutableMapOf<String, String>()
        try {
            db.rawQuery("SELECT wrong_form, correct_form FROM disambiguation", null).use { cursor ->
                while (cursor.moveToNext()) {
                    map[cursor.getString(0)] = cursor.getString(1)
                }
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${map.size} disambiguation entries")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load disambiguation map", e)
            return@withDatabase null
        }
        if (map.isNotEmpty()) map else null
    }

    override suspend fun loadExtendedDictionary(): List<SmartDictionaryEntry>? {
        if (!loadExtendedEntries) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Skipping extended dictionary in lite dictionary mode")
            return null
        }
        return withDatabase { db ->
            if (!db.hasTable("extended_dictionary") || !db.hasTable("extended_phonetics")) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Extended dictionary tables not found")
                return@withDatabase null
            }

            val entriesById = linkedMapOf<Int, MutableExtendedEntry>()
            try {
                db.rawQuery(
                    """
                    SELECT id, bengali, frequency, category
                    FROM extended_dictionary
                    ORDER BY id
                    """.trimIndent(),
                    null
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(0)
                        entriesById[id] = MutableExtendedEntry(
                            bengali = cursor.getString(1),
                            frequency = cursor.getInt(2),
                            category = parseWordCategory(cursor.getString(3))
                        )
                    }
                }

                db.rawQuery(
                    """
                    SELECT entry_id, phonetic
                    FROM extended_phonetics
                    ORDER BY entry_id
                    """.trimIndent(),
                    null
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        entriesById[cursor.getInt(0)]?.phonetics?.add(cursor.getString(1))
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load extended dictionary", e)
                return@withDatabase null
            } catch (oom: OutOfMemoryError) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Skipping extended dictionary after OOM", oom)
                return@withDatabase null
            }

            val entries = entriesById.values
                .asSequence()
                .filter { it.phonetics.isNotEmpty() }
                .map {
                    SmartDictionaryEntry(
                        bengali = it.bengali,
                        phonetics = it.phonetics,
                        frequency = it.frequency,
                        category = it.category
                    )
                }
                .toList()

            if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${entries.size} extended dictionary entries")
            entries.ifEmpty { null }
        }
    }

    override suspend fun loadBigramModel(): BigramModelData? {
        if (!loadBigramData) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Skipping bigram model in lite dictionary mode")
            return null
        }
        return withDatabase { db ->
            if (!db.hasTable("bigram_unigrams") || !db.hasTable("bigram_pairs")) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Bigram tables not found")
                return@withDatabase null
            }

            val unigrams = mutableMapOf<String, Int>()
            val bigrams = mutableMapOf<String, Int>()
            try {
                db.rawQuery("SELECT word, count FROM bigram_unigrams", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        unigrams[cursor.getString(0)] = cursor.getInt(1)
                    }
                }

                db.rawQuery("SELECT previous_word, next_word, count FROM bigram_pairs", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val key = "${cursor.getString(0)}\t${cursor.getString(1)}"
                        bigrams[key] = cursor.getInt(2)
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load bigram model", e)
                return@withDatabase null
            } catch (oom: OutOfMemoryError) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Skipping bigram model after OOM", oom)
                return@withDatabase null
            }

            // S20: trigram table (db 3.8.0+). Optional and OOM-guarded — the
            // model works bigram-only when absent or when memory is tight.
            val trigrams = mutableMapOf<String, Int>()
            if (db.hasTable("trigram_triples")) {
                try {
                    db.rawQuery("SELECT w1, w2, w3, count FROM trigram_triples", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val key = "${cursor.getString(0)}	${cursor.getString(1)}	${cursor.getString(2)}"
                            trigrams[key] = cursor.getInt(3)
                        }
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load trigram model", e)
                    trigrams.clear()
                } catch (oom: OutOfMemoryError) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Skipping trigram model after OOM", oom)
                    trigrams.clear()
                }
            }

            val totalUnigrams = loadMetadataInt(db, "total_unigrams") ?: unigrams.values.sum()
            val totalBigrams = loadMetadataInt(db, "total_bigrams") ?: bigrams.values.sum()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Loaded ${unigrams.size} unigrams and ${bigrams.size} bigrams")
            }
            if (unigrams.isNotEmpty() || bigrams.isNotEmpty()) {
                BigramModelData(
                    unigrams = unigrams,
                    bigrams = bigrams,
                    totalUnigrams = totalUnigrams,
                    totalBigrams = totalBigrams,
                    trigrams = trigrams
                )
            } else {
                null
            }
        }
    }

    private data class MutableExtendedEntry(
        val bengali: String,
        val frequency: Int,
        val category: WordCategory,
        val phonetics: MutableList<String> = mutableListOf()
    )

    private fun parseWordCategory(value: String?): WordCategory {
        return value
            ?.uppercase()
            ?.let { runCatching { WordCategory.valueOf(it) }.getOrNull() }
            ?: WordCategory.UNKNOWN
    }

    private fun loadMetadataInt(db: SQLiteDatabase, key: String): Int? {
        return try {
            db.rawQuery("SELECT value FROM metadata WHERE key=? LIMIT 1", arrayOf(key)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).toIntOrNull() else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
