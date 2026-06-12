package com.banglu.engine.dictionary

import com.banglu.engine.util.ReverseTransliterator

/**
 * Validates Bengali words against a loaded dictionary (up to 480K words).
 *
 * Provides O(log n) validation and prefix search via binary search on one
 * sorted word array. Also supports frequency data for ranking purposes.
 *
 * Nukta canonicalization (S2): the compiled `words` table stores the
 * nukta-FOLDED (precomposed ড়/ঢ়/য়) form only, while engine rule layers and
 * seed data still emit decomposed sequences. Every boundary here folds via
 * [ReverseTransliterator.foldNukta] — both loaded data and queries — so
 * membership/frequency checks are encoding-insensitive. foldNukta has an
 * O(n) no-nukta fast path, so non-nukta lookups stay cheap.
 *
 * Memory contract (S4/C2): full-mode load on a 256MB-heap device sits within
 * a few MB of the limit, so this class holds exactly ONE word-list-sized
 * structure — a sorted reference array. S2's `map{fold}` + `sorted()` shape
 * (two extra full list copies) plus the previous always-on HashSet
 * (~20MB of node overhead duplicating the sorted array's content) OOMed
 * 256MB-heap flagships during [loadWords]; membership is now a ~19-step
 * binary search, which is invisible next to the engine work around it.
 */
class BengaliWordValidator {

    private var sortedWords: List<String> = emptyList()
    private var loaded: Boolean = false

    /**
     * Frequencies for member words, parallel to [sortedWords] (S4/C2): an
     * IntArray costs 4 bytes/word; the previous HashMap<String, Int> cost a
     * node + boxed Int per entry (~50MB at 472K rows) and duplicated the
     * cursor's key strings. Entries whose key is NOT a member word land in
     * the small [frequencyOverflow] map (legacy/test sources only — the
     * production frequency source IS the words table).
     */
    private var frequenciesByIndex: IntArray = IntArray(0)
    private val frequencyOverflow: MutableMap<String, Int> = mutableMapOf()
    private var hasFrequencies: Boolean = false

    /**
     * Load a list of Bengali words into the validator.
     * Replaces any previously loaded words.
     *
     * Streaming fold (S4/C2): each word is folded as it is written into the
     * single backing array — no intermediate folded copy of the (472K-row)
     * input list is ever materialized. The array is then sorted in place and
     * deduplicated in place. foldNukta's no-nukta fast path returns the same
     * instance, so entries alias the caller's strings rather than duplicating
     * them (the compiled words table is already stored folded).
     *
     * @param wordList List of Bengali words to load
     */
    fun loadWords(wordList: List<String>) {
        val previousWords = sortedWords
        val previousFreqs = frequenciesByIndex
        val sorted = Array(wordList.size) { ReverseTransliterator.foldNukta(wordList[it]) }
        sorted.sort()
        // In-place dedupe of the sorted array (folding can merge encodings;
        // legacy sources may carry duplicate rows). No extra allocation.
        var unique = 0
        for (i in sorted.indices) {
            if (unique == 0 || sorted[i] != sorted[unique - 1]) {
                sorted[unique] = sorted[i]
                unique++
            }
        }
        val view = sorted.asList()
        sortedWords = if (unique == sorted.size) view else view.subList(0, unique)
        // Frequencies persist across word reloads (legacy contract), but the
        // parallel array is index-aligned — remap onto the new word order.
        if (previousFreqs.isNotEmpty()) {
            val remapped = IntArray(sortedWords.size)
            for (i in previousWords.indices) {
                if (i >= previousFreqs.size) break
                val freq = previousFreqs[i]
                if (freq == 0) continue
                val index = sortedIndexOf(previousWords[i])
                if (index >= 0) {
                    if (freq > remapped[index]) remapped[index] = freq
                } else {
                    val existing = frequencyOverflow[previousWords[i]]
                    if (existing == null || freq > existing) frequencyOverflow[previousWords[i]] = freq
                }
            }
            frequenciesByIndex = remapped
        }
        loaded = true
    }

    /**
     * Check if a Bengali word exists in the dictionary.
     *
     * @param word The Bengali word to validate
     * @return true if the word is in the dictionary
     */
    fun isValid(word: String): Boolean =
        sortedIndexOf(ReverseTransliterator.foldNukta(word)) >= 0

    /** Binary search over [sortedWords]; -1 when absent. [folded] must be nukta-folded. */
    private fun sortedIndexOf(folded: String): Int {
        var lo = 0
        var hi = sortedWords.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = sortedWords[mid].compareTo(folded)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

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
     * Get the total number of (deduplicated) words loaded.
     */
    fun getSize(): Int = sortedWords.size

    /**
     * Check whether words have been loaded.
     */
    fun isLoaded(): Boolean = loaded

    /**
     * Get the sorted word list (used by BengaliSectionIndex for building ranges).
     */
    fun getSortedWords(): List<String> = sortedWords

    /**
     * Load frequency data for ranking. Member words land in the parallel
     * IntArray; non-member keys (legacy/test sources) in the overflow map.
     * Both encodings may appear in legacy sources — max wins, matching the
     * compiler's nukta merge semantics.
     *
     * @param freqMap Map of Bengali word to frequency score
     */
    fun loadFrequencies(freqMap: Map<String, Int>) {
        frequenciesByIndex = IntArray(sortedWords.size)
        frequencyOverflow.clear()
        for ((word, freq) in freqMap) {
            val folded = ReverseTransliterator.foldNukta(word)
            val index = sortedIndexOf(folded)
            if (index >= 0) {
                if (freq > frequenciesByIndex[index]) frequenciesByIndex[index] = freq
            } else {
                val existing = frequencyOverflow[folded]
                if (existing == null || freq > existing) frequencyOverflow[folded] = freq
            }
        }
        hasFrequencies = freqMap.isNotEmpty()
    }

    /**
     * Get the frequency score for a word.
     *
     * @param word The Bengali word
     * @return Frequency score, or 0 if not found
     */
    fun getFrequency(word: String): Int {
        val folded = ReverseTransliterator.foldNukta(word)
        val index = sortedIndexOf(folded)
        if (index >= 0 && index < frequenciesByIndex.size) return frequenciesByIndex[index]
        return frequencyOverflow[folded] ?: 0
    }

    /**
     * Check if frequency data has been loaded.
     */
    fun hasFrequencyData(): Boolean = hasFrequencies
}
