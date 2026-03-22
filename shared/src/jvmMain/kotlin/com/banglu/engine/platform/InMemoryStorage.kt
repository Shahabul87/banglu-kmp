package com.banglu.engine.platform

import com.banglu.engine.types.LearnedWord

/**
 * In-memory implementation of PlatformStorage for JVM tests.
 *
 * Stores all data in memory - no persistence across process restarts.
 * Suitable for unit tests and development.
 */
class InMemoryStorage : PlatformStorage {

    private val learnedWords = mutableListOf<LearnedWord>()
    private var cachedDict: CachedDictionary? = null

    override suspend fun getLearnedWords(): List<LearnedWord> = learnedWords.toList()

    override suspend fun saveLearnedWord(phonetic: String, bengali: String, frequency: Int) {
        val id = "$phonetic::$bengali"
        val existingIndex = learnedWords.indexOfFirst { it.id == id }
        if (existingIndex >= 0) {
            val existing = learnedWords[existingIndex]
            learnedWords[existingIndex] = existing.copy(
                frequency = existing.frequency + 1,
                lastUsed = System.currentTimeMillis()
            )
        } else {
            learnedWords.add(
                LearnedWord(
                    id = id,
                    phonetic = phonetic,
                    bengali = bengali,
                    frequency = frequency,
                    lastUsed = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun clearLearnedWords() {
        learnedWords.clear()
    }

    override suspend fun getDictionaryVersion(): String? = cachedDict?.version

    override suspend fun cacheDictionary(
        words: List<String>,
        frequencies: Map<String, Int>?,
        disambigMap: Map<String, String>?,
        version: String
    ) {
        cachedDict = CachedDictionary(words, frequencies, disambigMap, version)
    }

    override suspend fun getCachedDictionary(currentVersion: String): CachedDictionary? {
        return cachedDict?.takeIf { it.version == currentVersion }
    }
}
