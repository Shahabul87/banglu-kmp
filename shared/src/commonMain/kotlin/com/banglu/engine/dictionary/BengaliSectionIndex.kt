package com.banglu.engine.dictionary

/**
 * Range within the sorted word list for a given prefix section.
 *
 * @param start Inclusive start index in sorted word list
 * @param end Exclusive end index in sorted word list
 * @param count Number of words in this range (end - start)
 */
data class SectionRange(val start: Int, val end: Int, val count: Int = end - start)

/**
 * Maps 1-char and 2-char Bengali prefixes to ranges in the sorted word list.
 *
 * Builds in O(n) single pass over the sorted list, then provides O(1) range
 * lookups by prefix. Used by SectionNarrowingEngine to quickly narrow the
 * 480K dictionary to relevant sections.
 */
class BengaliSectionIndex {

    private val ranges: MutableMap<String, SectionRange> = mutableMapOf()
    private var ready: Boolean = false

    /**
     * Build the section index from a sorted list of Bengali words.
     * Single O(n) pass tracks 1-char and 2-char prefix boundaries.
     *
     * @param sortedWords Lexicographically sorted list of Bengali words
     */
    fun buildIndex(sortedWords: List<String>) {
        ranges.clear()

        if (sortedWords.isEmpty()) {
            ready = true
            return
        }

        var prev1 = ""
        var start1 = 0
        var prev2 = ""
        var start2 = 0

        for (i in sortedWords.indices) {
            val word = sortedWords[i]
            if (word.isEmpty()) continue

            val cur1 = word.substring(0, 1)
            val cur2 = if (word.length >= 2) word.substring(0, 2) else ""

            if (cur1 != prev1) {
                if (prev1.isNotEmpty()) ranges[prev1] = SectionRange(start1, i)
                if (prev2.isNotEmpty()) ranges[prev2] = SectionRange(start2, i)
                prev1 = cur1
                start1 = i
                prev2 = cur2
                start2 = i
            } else if (cur2 != prev2) {
                if (prev2.isNotEmpty()) ranges[prev2] = SectionRange(start2, i)
                prev2 = cur2
                start2 = i
            }
        }

        // Close the final sections
        if (prev1.isNotEmpty()) ranges[prev1] = SectionRange(start1, sortedWords.size)
        if (prev2.isNotEmpty()) ranges[prev2] = SectionRange(start2, sortedWords.size)

        ready = true
    }

    /**
     * Whether the index has been built.
     */
    fun isReady(): Boolean = ready

    /**
     * Get the range for a given prefix (1-char or 2-char).
     *
     * @param prefix Bengali prefix to look up
     * @return SectionRange or null if prefix not found
     */
    fun getRange(prefix: String): SectionRange? = ranges[prefix]

    /**
     * Get the count of words in a section.
     *
     * @param prefix Bengali prefix to look up
     * @return Number of words in this section, or 0 if not found
     */
    fun countWordsInRange(prefix: String): Int = ranges[prefix]?.count ?: 0

    /**
     * Get words from a section range, optionally filtering by max word length.
     *
     * @param prefix Bengali prefix to look up
     * @param sortedWords The sorted word list (same one used to build index)
     * @param limit Maximum number of words to return
     * @param maxWordLen Optional maximum word length filter
     * @return List of words in the section matching criteria
     */
    fun getWordsInRange(
        prefix: String,
        sortedWords: List<String>,
        limit: Int,
        maxWordLen: Int? = null
    ): List<String> {
        val range = ranges[prefix] ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in range.start until minOf(range.end, sortedWords.size)) {
            if (result.size >= limit) break
            val word = sortedWords[i]
            if (!word.startsWith(prefix)) continue
            if (maxWordLen != null && word.length > maxWordLen) continue
            result.add(word)
        }
        return result
    }

    /**
     * Get the number of indexed sections (prefixes).
     */
    fun getIndexSize(): Int = ranges.size
}
