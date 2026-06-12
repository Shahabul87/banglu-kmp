package com.banglu.engine.dictionary

import com.banglu.engine.util.ReverseTransliterator

/**
 * Validates Bengali words against a loaded dictionary (up to 480K words).
 *
 * Provides O(1) validation via HashSet and O(log n) prefix search via
 * binary search on a sorted word list. Also supports frequency data
 * for ranking purposes.
 *
 * Nukta canonicalization (S2): the compiled `words` table stores the
 * nukta-FOLDED (precomposed ড়/ঢ়/য়) form only, while engine rule layers and
 * seed data still emit decomposed sequences. Every boundary here folds via
 * [ReverseTransliterator.foldNukta] — both loaded data and queries — so
 * membership/frequency checks are encoding-insensitive. foldNukta has an
 * O(n) no-nukta fast path, so non-nukta lookups stay cheap.
 */
class BengaliWordValidator {

    private val words: MutableSet<String> = mutableSetOf()
    private var sortedWords: List<String> = emptyList()
    private var loaded: Boolean = false
    private val frequencies: MutableMap<String, Int> = mutableMapOf()

    /**
     * Load a list of Bengali words into the validator.
     * Replaces any previously loaded words.
     *
     * @param wordList List of Bengali words to load
     */
    fun loadWords(wordList: List<String>) {
        val folded = wordList.map { ReverseTransliterator.foldNukta(it) }
        words.clear()
        words.addAll(folded)
        sortedWords = folded.sorted()
        loaded = true
    }

    /**
     * Check if a Bengali word exists in the dictionary.
     *
     * @param word The Bengali word to validate
     * @return true if the word is in the dictionary
     */
    fun isValid(word: String): Boolean = words.contains(ReverseTransliterator.foldNukta(word))

    /**
     * Find words starting with the given Bengali prefix using binary search.
     *
     * @param prefix The Bengali prefix to search for
     * @param limit Maximum number of results to return
     * @return List of words starting with the prefix, up to limit
     */
    fun findByPrefix(prefix: String, limit: Int = 10): List<String> {
        if (sortedWords.isEmpty() || prefix.isEmpty()) return emptyList()
        @Suppress("NAME_SHADOWING") val prefix = ReverseTransliterator.foldNukta(prefix)

        // Binary search for first word >= prefix
        var lo = 0
        var hi = sortedWords.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (sortedWords[mid] < prefix) lo = mid + 1 else hi = mid
        }

        val result = mutableListOf<String>()
        var i = lo
        while (i < sortedWords.size && result.size < limit && sortedWords[i].startsWith(prefix)) {
            result.add(sortedWords[i])
            i++
        }
        return result
    }

    /**
     * Get the total number of words loaded.
     */
    fun getSize(): Int = words.size

    /**
     * Check whether words have been loaded.
     */
    fun isLoaded(): Boolean = loaded

    /**
     * Get the sorted word list (used by BengaliSectionIndex for building ranges).
     */
    fun getSortedWords(): List<String> = sortedWords

    /**
     * Load frequency data for ranking.
     *
     * @param freqMap Map of Bengali word to frequency score
     */
    fun loadFrequencies(freqMap: Map<String, Int>) {
        frequencies.clear()
        for ((word, freq) in freqMap) {
            val folded = ReverseTransliterator.foldNukta(word)
            // Both encodings may appear in legacy sources — max wins, matching
            // the compiler's nukta merge semantics.
            val existing = frequencies[folded]
            if (existing == null || freq > existing) frequencies[folded] = freq
        }
    }

    /**
     * Get the frequency score for a word.
     *
     * @param word The Bengali word
     * @return Frequency score, or 0 if not found
     */
    fun getFrequency(word: String): Int = frequencies[ReverseTransliterator.foldNukta(word)] ?: 0

    /**
     * Check if frequency data has been loaded.
     */
    fun hasFrequencyData(): Boolean = frequencies.isNotEmpty()
}
