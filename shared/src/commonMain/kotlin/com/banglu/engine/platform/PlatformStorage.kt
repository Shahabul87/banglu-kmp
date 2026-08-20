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
     * S78: remove the learned rows for ONE phonetic key. This is NOT
     * sanitation (which is skip-on-load only, F5b) — it is the user's explicit
     * correction: tapping the engine's own primary back means "stop overriding
     * this key", and the stored divergent preference must not resurrect on the
     * next initialize. Custom user-dictionary entries are never touched.
     * Default: no-op (platforms without a preference surface).
     */
    suspend fun removeLearnedWord(phonetic: String) {}

    /**
     * Retrieve user-typed next-word pairs: previous word -> (next word -> count).
     * Powers personalized next-word prediction. Default: no persistence.
     */
    suspend fun getUserBigrams(): Map<String, Map<String, Int>> = emptyMap()

    /** Save or update one user bigram pair. Default: no persistence. */
    suspend fun saveUserBigram(previous: String, next: String, count: Int) {}

    /** Clear all user bigram pairs. Default: no-op. */
    suspend fun clearUserBigrams() {}

    /**
     * S128 (production audit): the ONE authoritative erasure — every piece
     * of learned personal data: learned words, custom conversions, user
     * bigrams, English-typing learning, and saved identities (emails).
     * The default composes the existing operations so every platform gets
     * complete semantics; platforms may override to remove keys outright.
     */
    suspend fun clearAllLearningData() {
        clearLearnedWords()
        clearUserBigrams()
        saveEnglishUserData("")
        saveIdentityUserData("")
    }

    /**
     * S96: the English typing suite's learning blob (user word counts +
     * next-word bigrams, EnglishTypingEngine.serialize format). One bounded
     * string; platforms without an EN keyboard surface keep the no-op.
     */
    suspend fun saveEnglishUserData(data: String) {}

    /** S96: load the English learning blob. Default: nothing saved. */
    suspend fun loadEnglishUserData(): String? = null

    /**
     * S98: the identity-assist blob (saved emails + domains, IdentityAssist
     * serialize format). On-device only; cleared with learned words.
     */
    suspend fun saveIdentityUserData(data: String) {}

    /** S98: load the identity-assist blob. Default: nothing saved. */
    suspend fun loadIdentityUserData(): String? = null

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
