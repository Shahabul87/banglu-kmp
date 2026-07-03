package com.banglu.keyboard

import android.content.Context
import android.content.SharedPreferences
import com.banglu.engine.platform.CachedDictionary
import com.banglu.engine.platform.PlatformStorage
import com.banglu.engine.types.LearnedWord
import kotlin.math.absoluteValue

data class CustomConversion(
    val phonetic: String,
    val bengali: String,
    val createdAt: Long
)

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
    private val appPrefs: SharedPreferences =
        context.getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LEARNED_WORDS = "learned_words"
        private const val KEY_CUSTOM_CONVERSIONS = "custom_conversions"
        private const val KEY_USER_BIGRAMS = "user_bigrams"
        private const val KEY_DICT_VERSION = "dict_version"
        private const val SEPARATOR = "::"
        private const val MAX_LEARNED_WORDS = 500
        private const val MAX_CUSTOM_CONVERSIONS = 300
        private const val MAX_USER_BIGRAMS = 800
    }

    override suspend fun getLearnedWords(): List<LearnedWord> {
        val learnedRaw = getScopedString(KEY_LEARNED_WORDS) ?: ""
        val customWords = getCustomConversions().map {
            LearnedWord(
                id = "${it.phonetic}$SEPARATOR${it.bengali}",
                phonetic = it.phonetic,
                bengali = it.bengali,
                frequency = 120,
                lastUsed = it.createdAt
            )
        }
        val learnedWords = learnedRaw.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(SEPARATOR)
                if (parts.size >= 3) {
                    LearnedWord(
                        id = "${parts[0]}$SEPARATOR${parts[1]}",
                        phonetic = parts[0],
                        bengali = parts[1],
                        frequency = parts[2].toIntOrNull() ?: 1,
                        lastUsed = parts.getOrNull(3)?.toLongOrNull() ?: System.currentTimeMillis()
                    )
                } else {
                    null
                }
            }
        return (customWords + learnedWords)
            .distinctBy { it.id }
    }

    override suspend fun saveLearnedWord(phonetic: String, bengali: String, frequency: Int) {
        val now = System.currentTimeMillis()
        val learned = getLearnedWords()
            .associateBy { it.id }
            .toMutableMap()
        val id = "$phonetic$SEPARATOR$bengali"
        val existing = learned[id]
        learned[id] = LearnedWord(
            id = id,
            phonetic = phonetic,
            bengali = bengali,
            frequency = if (existing == null) frequency else maxOf(existing.frequency + 1, frequency),
            lastUsed = now
        )

        // FIFO eviction: keep only the most recent entries
        val lines = learned.values
            .sortedByDescending { it.lastUsed }
            .take(MAX_LEARNED_WORDS)
            .map { "${it.phonetic}$SEPARATOR${it.bengali}$SEPARATOR${it.frequency}$SEPARATOR${it.lastUsed}" }

        prefs.edit()
            .putString(scopedKey(KEY_LEARNED_WORDS), lines.joinToString("\n"))
            .apply()
    }

    override suspend fun clearLearnedWords() {
        prefs.edit()
            .remove(scopedKey(KEY_LEARNED_WORDS))
            .remove(scopedKey(KEY_CUSTOM_CONVERSIONS))
            .apply()
    }

    override suspend fun getUserBigrams(): Map<String, Map<String, Int>> {
        val raw = getScopedString(KEY_USER_BIGRAMS) ?: return emptyMap()
        val pairs = mutableMapOf<String, MutableMap<String, Int>>()
        raw.lines()
            .filter { it.isNotBlank() }
            .forEach { line ->
                val parts = line.split(SEPARATOR)
                if (parts.size >= 3) {
                    val count = parts[2].toIntOrNull() ?: return@forEach
                    pairs.getOrPut(parts[0]) { mutableMapOf() }[parts[1]] = count
                }
            }
        return pairs
    }

    override suspend fun saveUserBigram(previous: String, next: String, count: Int) {
        val prev = previous.trim()
        val follower = next.trim()
        if (prev.isEmpty() || follower.isEmpty() || count <= 0) return
        val now = System.currentTimeMillis()

        data class BigramLine(val prev: String, val next: String, val count: Int, val lastUsed: Long)

        val raw = getScopedString(KEY_USER_BIGRAMS) ?: ""
        val entries = raw.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(SEPARATOR)
                if (parts.size >= 3) {
                    BigramLine(
                        prev = parts[0],
                        next = parts[1],
                        count = parts[2].toIntOrNull() ?: 1,
                        lastUsed = parts.getOrNull(3)?.toLongOrNull() ?: now
                    )
                } else {
                    null
                }
            }
            .associateBy { "${it.prev}$SEPARATOR${it.next}" }
            .toMutableMap()

        entries["$prev$SEPARATOR$follower"] = BigramLine(prev, follower, count, now)

        // Recency eviction, matching learned-word behavior.
        val lines = entries.values
            .sortedByDescending { it.lastUsed }
            .take(MAX_USER_BIGRAMS)
            .map { "${it.prev}$SEPARATOR${it.next}$SEPARATOR${it.count}$SEPARATOR${it.lastUsed}" }

        prefs.edit()
            .putString(scopedKey(KEY_USER_BIGRAMS), lines.joinToString("\n"))
            .apply()
    }

    override suspend fun clearUserBigrams() {
        prefs.edit()
            .remove(scopedKey(KEY_USER_BIGRAMS))
            .apply()
    }

    fun getCustomConversions(): List<CustomConversion> {
        val raw = getScopedString(KEY_CUSTOM_CONVERSIONS) ?: return emptyList()
        return raw.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(SEPARATOR)
                if (parts.size >= 3) {
                    CustomConversion(
                        phonetic = parts[0],
                        bengali = parts[1],
                        createdAt = parts[2].toLongOrNull() ?: System.currentTimeMillis()
                    )
                } else {
                    null
                }
            }
    }

    fun saveCustomConversion(phonetic: String, bengali: String) {
        val key = phonetic.lowercase().trim()
        val value = bengali.trim()
        if (key.isEmpty() || value.isEmpty()) return
        val now = System.currentTimeMillis()
        val conversions = getCustomConversions()
            .filterNot { it.phonetic == key }
            .toMutableList()
        conversions.add(0, CustomConversion(key, value, now))
        persistCustomConversions(conversions.take(MAX_CUSTOM_CONVERSIONS))
    }

    fun deleteCustomConversion(phonetic: String, bengali: String) {
        persistCustomConversions(
            getCustomConversions().filterNot {
                it.phonetic == phonetic && it.bengali == bengali
            }
        )
    }

    private fun persistCustomConversions(conversions: List<CustomConversion>) {
        val lines = conversions.map { "${it.phonetic}$SEPARATOR${it.bengali}$SEPARATOR${it.createdAt}" }
        prefs.edit()
            .putString(scopedKey(KEY_CUSTOM_CONVERSIONS), lines.joinToString("\n"))
            .apply()
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

    private fun getScopedString(baseKey: String): String? {
        val scoped = prefs.getString(scopedKey(baseKey), null)
        if (scoped != null) return scoped
        return if (activeUserId() == "anonymous") prefs.getString(baseKey, null) else null
    }

    private fun scopedKey(baseKey: String): String = "${baseKey}_${activeUserId()}"

    private fun activeUserId(): String {
        val explicit = appPrefs.getString("auth_user_id", null)?.trim().orEmpty()
        if (explicit.isNotEmpty()) return explicit
        val email = appPrefs.getString("auth_email", null)?.trim().orEmpty()
        if (email.isNotEmpty()) return "user_${email.lowercase().hashCode().absoluteValue}"
        return "anonymous"
    }
}
