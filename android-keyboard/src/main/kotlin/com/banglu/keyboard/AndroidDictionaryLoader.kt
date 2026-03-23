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

    /**
     * Open the SQLite database, copying from assets on first access.
     * Returns null if the database cannot be opened (graceful degradation).
     */
    private fun openDatabase(): SQLiteDatabase? {
        val dbFile = File(context.filesDir, DB_FILENAME)

        if (!dbFile.exists()) {
            try {
                Log.d(TAG, "Copying $DB_FILENAME from assets to ${dbFile.absolutePath}")
                context.assets.open(DB_FILENAME).use { input ->
                    dbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copy complete (${dbFile.length() / 1024 / 1024}MB)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy database from assets", e)
                return null
            }
        }

        return try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open database", e)
            null
        }
    }

    override suspend fun loadFullDictionary(): List<String>? {
        val db = openDatabase() ?: return null
        val words = mutableListOf<String>()
        try {
            db.rawQuery("SELECT bengali FROM words", null).use { cursor ->
                while (cursor.moveToNext()) {
                    words.add(cursor.getString(0))
                }
            }
            Log.d(TAG, "Loaded ${words.size} words from dictionary")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load full dictionary", e)
            return null
        } finally {
            db.close()
        }
        return if (words.isNotEmpty()) words else null
    }

    override suspend fun loadFrequencyMap(): Map<String, Int>? {
        val db = openDatabase() ?: return null
        val freqs = mutableMapOf<String, Int>()
        try {
            db.rawQuery("SELECT bengali, frequency FROM words WHERE frequency > 0", null).use { cursor ->
                while (cursor.moveToNext()) {
                    freqs[cursor.getString(0)] = cursor.getInt(1)
                }
            }
            Log.d(TAG, "Loaded ${freqs.size} frequency entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load frequency map", e)
            return null
        } finally {
            db.close()
        }
        return if (freqs.isNotEmpty()) freqs else null
    }

    override suspend fun loadDisambiguationMap(): Map<String, String>? {
        val db = openDatabase() ?: return null
        val map = mutableMapOf<String, String>()
        try {
            db.rawQuery("SELECT wrong_form, correct_form FROM disambiguation", null).use { cursor ->
                while (cursor.moveToNext()) {
                    map[cursor.getString(0)] = cursor.getString(1)
                }
            }
            Log.d(TAG, "Loaded ${map.size} disambiguation entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load disambiguation map", e)
            return null
        } finally {
            db.close()
        }
        return if (map.isNotEmpty()) map else null
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
