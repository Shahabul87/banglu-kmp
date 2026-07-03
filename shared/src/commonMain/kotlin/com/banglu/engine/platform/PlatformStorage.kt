package com.banglu.engine.platform

import com.banglu.engine.types.LearnedWord

/**
 * Platform-specific storage abstraction.
 *
 * Each platform (JVM, Android, iOS, JS) provides its own implementation:
 * - JVM: InMemoryStorage (for tests)
 * - Android: SharedPreferences / Room-backed
 * - iOS: UserDefaults / CoreData-backed
 * - JS/Web: IndexedDB-backed (like DictionaryCache.ts)
 */
interface PlatformStorage {
    /** Retrieve all learned words from persistent storage. */
    suspend fun getLearnedWords(): List<LearnedWord>

    /** Save or update a learned word in persistent storage. */
    suspend fun saveLearnedWord(phonetic: String, bengali: String, frequency: Int)

    /** Clear all learned words from persistent storage. */
    suspend fun clearLearnedWords()

    /**
     * Retrieve user-typed next-word pairs: previous word -> (next word -> count).
     * Powers personalized next-word prediction. Default: no persistence.
     */
    suspend fun getUserBigrams(): Map<String, Map<String, Int>> = emptyMap()

    /** Save or update one user bigram pair. Default: no persistence. */
    suspend fun saveUserBigram(previous: String, next: String, count: Int) {}

    /** Clear all user bigram pairs. Default: no-op. */
    suspend fun clearUserBigrams() {}

    /** Get the version string of the cached dictionary, or null if not cached. */
    suspend fun getDictionaryVersion(): String?

    /** Cache the large dictionary data for fast subsequent loads. */
    suspend fun cacheDictionary(
        words: List<String>,
        frequencies: Map<String, Int>?,
        disambigMap: Map<String, String>?,
        version: String
    )

    /** Retrieve the cached dictionary if the version matches. */
    suspend fun getCachedDictionary(currentVersion: String): CachedDictionary?
}

/**
 * Represents a cached copy of the large Bengali dictionary.
 */
data class CachedDictionary(
    val words: List<String>,
    val frequencies: Map<String, Int>?,
    val disambigMappings: Map<String, String>?,
    val version: String
)
