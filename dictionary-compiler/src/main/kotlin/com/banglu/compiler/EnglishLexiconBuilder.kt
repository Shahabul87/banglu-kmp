package com.banglu.compiler

/**
 * Builds a list of [EnglishLexiconEntry] by intersecting CMUdict pronunciations with a top-N
 * English word frequency list and converting ARPABET phoneme sequences to Bengali script via
 * [ArpabetToBengali].
 *
 * Data sources:
 *   - CMUdict (BSD-2-Clause): https://github.com/cmusphinx/cmudict
 *   - HermitDave FrequencyWords (MIT): https://github.com/hermitdave/FrequencyWords
 */
data class EnglishLexiconEntry(val key: String, val bengali: String)

object EnglishLexiconBuilder {

    private val KEY_RE = Regex("^[a-z]+$")

    /**
     * Words whose ARPABET pronunciation could not be converted by [ArpabetToBengali] in the
     * most recent [build] call (i.e. [ArpabetToBengali.convert] returned null).
     * Reset to zero at the start of each [build] call.
     *
     * **Warning:** must be read immediately after the corresponding [build] call.
     * This is a single-threaded build tool — concurrent builds will overwrite
     * this field.
     */
    var lastSkippedUnconvertible: Int = 0
        private set

    /**
     * Intersects [cmudictLines] with [topWords] and converts each matching word's pronunciation
     * to Bengali.
     *
     * CMUdict format: `word  PH ON EM ES` per line; comment lines start with `;;;`;
     * alternate pronunciations have the form `word(2) ...` and are skipped.
     *
     * Only the first (primary) pronunciation is used for each word — explicit deduplication
     * via a [HashSet] ensures the first CMUdict entry wins even if the file contains
     * duplicate base entries. Words with keys that contain non a-z characters (apostrophes,
     * digits, parentheses) are skipped. Words whose pronunciation fails
     * [ArpabetToBengali.convert] (null return) are skipped and counted in
     * [lastSkippedUnconvertible].
     */
    fun build(cmudictLines: List<String>, topWords: Set<String>): List<EnglishLexiconEntry> {
        lastSkippedUnconvertible = 0
        val entries = ArrayList<EnglishLexiconEntry>()
        val seen = HashSet<String>()
        for (line in cmudictLines) {
            if (line.startsWith(";;;")) continue
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2) continue
            val word = parts[0].lowercase()
            // Skip alternate pronunciations (e.g. "bus(2)")
            if (word.contains("(")) continue
            // Skip words with non a-z characters (apostrophes, hyphens, etc.)
            if (!KEY_RE.matches(word)) continue
            // Skip words not in the top-frequency set
            if (word !in topWords) continue
            // First pronunciation wins; skip subsequent duplicate base entries
            if (!seen.add(word)) continue
            val bengali = ArpabetToBengali.convert(parts.drop(1), word)
            if (bengali == null) {
                lastSkippedUnconvertible++
                continue
            }
            entries.add(EnglishLexiconEntry(word, bengali))
        }
        return entries
    }

    /**
     * Parses a HermitDave-format frequency list ("word count" per line, descending by count)
     * and returns the top-[limit] accepted words as a [Set].
     *
     * Acceptance criteria:
     *   - Word must match `^[a-z]+$` (all lowercase ASCII letters; no apostrophes, hyphens,
     *     digits, or uppercase variants after lowercasing)
     *   - Word must be at least 2 characters long. Single-character English keys ("a", "i")
     *     collide with Bengali phonetic vowel typing (a→আ, i→ই) and must never shadow them.
     *
     * [limit] caps the number of **accepted** words (i.e. `take(limit)` is applied AFTER
     * filtering out rejected entries). Words beyond the limit are ignored.
     */
    fun parseTopWords(lines: List<String>, limit: Int = 50_000): Set<String> =
        lines.asSequence()
            .mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() }
            .filter { KEY_RE.matches(it) && it.length >= 2 }
            .take(limit)
            .toSet()
}
