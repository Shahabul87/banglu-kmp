package com.banglu.keyboard

import android.content.Context
import android.content.SharedPreferences
import com.banglu.engine.platform.CachedDictionary
import com.banglu.engine.platform.PlatformStorage
import com.banglu.engine.types.LearnedWord

/**
 * Android-specific storage implementation using SharedPreferences.
 *
 * Handles:
 * - Learned words persistence (user's word selection history)
 * - Dictionary version tracking
 *
 * The full dictionary is loaded directly from SQLite (via AndroidDictionaryLoader),
 * so cacheDictionary/getCachedDictionary are no-ops -- the database file itself
 * serves as the persistent cache.
 */
class AndroidStorage(context: Context) : PlatformStorage {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("banglu_learning", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LEARNED_WORDS = "learned_words"
        private const val KEY_DICT_VERSION = "dict_version"
        private const val SEPARATOR = "::"
    }

    override suspend fun getLearnedWords(): List<LearnedWord> {
        val raw = prefs.getString(KEY_LEARNED_WORDS, null) ?: return emptyList()
        return raw.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(SEPARATOR)
                if (parts.size >= 3) {
                    LearnedWord(
                        id = "${parts[0]}$SEPARATOR${parts[1]}",
                        phonetic = parts[0],
                        bengali = parts[1],
                        frequency = parts[2].toIntOrNull() ?: 1,
                        lastUsed = System.currentTimeMillis()
                    )
                } else {
                    null
                }
            }
    }

    override suspend fun saveLearnedWord(phonetic: String, bengali: String, frequency: Int) {
        val existing = prefs.getString(KEY_LEARNED_WORDS, "") ?: ""
        val line = "$phonetic$SEPARATOR$bengali$SEPARATOR$frequency"
        if (!existing.contains(line)) {
            prefs.edit()
                .putString(KEY_LEARNED_WORDS, existing + line + "\n")
                .apply()
        }
    }

    override suspend fun clearLearnedWords() {
        prefs.edit().remove(KEY_LEARNED_WORDS).apply()
    }

    override suspend fun getDictionaryVersion(): String? {
        return prefs.getString(KEY_DICT_VERSION, null)
    }

    override suspend fun cacheDictionary(
        words: List<String>,
        frequencies: Map<String, Int>?,
        disambigMap: Map<String, String>?,
        version: String
    ) {
        // The dictionary is bundled as SQLite in assets -- no need to cache separately.
        // Just record the version so the engine knows it has been loaded.
        prefs.edit().putString(KEY_DICT_VERSION, version).apply()
    }

    override suspend fun getCachedDictionary(currentVersion: String): CachedDictionary? {
        // Dictionary is loaded directly from SQLite, not from a cache layer.
        return null
    }
}
