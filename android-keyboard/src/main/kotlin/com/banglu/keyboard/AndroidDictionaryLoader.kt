package com.banglu.keyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.banglu.engine.platform.DictionaryLoader
import com.banglu.engine.types.BigramModelData
import com.banglu.engine.types.SmartDictionaryEntry
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
class AndroidDictionaryLoader(private val context: Context) : DictionaryLoader {

    companion object {
        private const val TAG = "BangluDictLoader"
        private const val DB_FILENAME = "dictionary.sqlite"
    }

    /** Lazily ensure the database file exists in internal storage (copy from assets once). */
    private val dbFile: File by lazy {
        val file = File(context.filesDir, DB_FILENAME)
        if (!file.exists()) {
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "Copying $DB_FILENAME from assets to ${file.absolutePath}")
                context.assets.open(DB_FILENAME).use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
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

    override suspend fun loadFullDictionary(): List<String>? = withDatabase { db ->
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
        }
        if (words.isNotEmpty()) words else null
    }

    override suspend fun loadFrequencyMap(): Map<String, Int>? = withDatabase { db ->
        val freqs = mutableMapOf<String, Int>()
        try {
            db.rawQuery("SELECT bengali, frequency FROM words WHERE frequency > 0", null).use { cursor ->
                while (cursor.moveToNext()) {
                    freqs[cursor.getString(0)] = cursor.getInt(1)
                }
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${freqs.size} frequency entries")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load frequency map", e)
            return@withDatabase null
        }
        if (freqs.isNotEmpty()) freqs else null
    }

    override suspend fun loadDisambiguationMap(): Map<String, String>? = withDatabase { db ->
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
        // Extended phonetic entries are not in the SQLite database yet.
        // The seed dictionary covers this for now.
        return null
    }

    override suspend fun loadBigramModel(): BigramModelData? {
        // Bigram model is not in the SQLite database yet.
        return null
    }
}
