package com.banglu.compiler

import com.banglu.engine.rules.CleanTransliterator
import com.banglu.engine.util.ReverseTransliterator

data class PhoneticIndexRow(
    val key: String,
    val bengali: String,
    val frequency: Int,
    val tier: Int // 0 = Tier A (suggestible), 1 = Tier B (exact-match only)
)

data class IndexBuildReport(
    val totalWords: Int = 0,
    val roundTripOk: Int = 0,
    val totalRows: Int = 0,
    val droppedKeys: Int = 0,
    val wordsWithNoRows: Int = 0
) {
    val coveragePercent: Double
        get() = if (totalWords == 0) 0.0 else roundTripOk * 100.0 / totalWords
}

object PhoneticIndexBuilder {

    private const val TIER_A = 0
    private const val TIER_B = 1

    /**
     * The most recent report produced by the last [build] call.
     *
     * **Warning:** must be read immediately after the corresponding [build] call.
     * This is a single-threaded build tool — concurrent builds will overwrite
     * this field.
     */
    var lastReport: IndexBuildReport = IndexBuildReport()
        private set

    private val BENGALI_ONLY = Regex("^[\\u0980-\\u09FF]+$")
    private val ROMAN_ONLY = Regex("^[a-z]+$")

    fun build(words: List<String>, frequencies: Map<String, Int>): List<PhoneticIndexRow> {
        val rows = ArrayList<PhoneticIndexRow>(words.size * 2)
        val seen = HashSet<String>(words.size * 2)
        var roundTripOk = 0
        var total = 0
        var droppedKeys = 0
        var wordsWithNoRows = 0

        for (raw in words) {
            val word = raw.trim()
            if (word.length !in 2..18) continue
            if (!BENGALI_ONLY.matches(word)) continue
            if (word.endsWith("্")) continue
            total++

            val canonical = ReverseTransliterator.reverseWord(word).lowercase()
            if (CleanTransliterator.transliterate(canonical) == word) roundTripOk++

            val freq = frequencies[word] ?: 0
            val tier = if (freq > 0) TIER_A else TIER_B
            val aliases = aliasesFor(canonical)
            var rowsForWord = 0
            for (key in aliases) {
                if (key.length !in 2..24 || !ROMAN_ONLY.matches(key)) {
                    droppedKeys++
                    continue
                }
                if (!seen.add("$key $word")) continue
                rows.add(PhoneticIndexRow(key, word, freq, tier))
                rowsForWord++
            }
            if (rowsForWord == 0) wordsWithNoRows++
        }
        lastReport = IndexBuildReport(
            totalWords = total,
            roundTripOk = roundTripOk,
            totalRows = rows.size,
            droppedKeys = droppedKeys,
            wordsWithNoRows = wordsWithNoRows
        )
        return rows
    }

    /**
     * Typing-habit aliases for a canonical phonetic key.
     *
     * Live rules (mirrors SmartEngine.corpusPhoneticAliases):
     * - `chh → c`  : ছ (REVERSE_CONSONANTS emits "chh"; users type "c")
     * - `ii → i`   : ী / ঈ (emitted as "ii"; users omit the doubled vowel)
     * - `uu → u`   : ূ / ঊ (emitted as "uu"; users omit the doubled vowel)
     *
     * Additionally, all three rules are applied in sequence to produce a
     * fully-collapsed combined alias (e.g. "chhotrii" → "chotri"), so words
     * containing both ছ and a long vowel are reachable via the key users type.
     * The [linkedSetOf] deduplicates when the composed result equals an
     * existing alias.
     *
     * Deleted rules (dead — transliterator never emits these patterns):
     * - ~~`ee → i`~~ : transliterator never emits "ee" as a vowel unit
     * - ~~`oo → u`~~ : transliterator never emits "oo" as a vowel unit;
     *                  accidental o+o adjacency would produce a wrong variant
     *
     * Note: runtime query-side normalizations (e.g. "ee → i") are handled
     * by the engine, not by index aliases here.
     */
    private fun aliasesFor(canonical: String): List<String> {
        val aliases = linkedSetOf(canonical)
        if (canonical.contains("chh")) aliases.add(canonical.replace("chh", "c"))
        if (canonical.contains("ii")) aliases.add(canonical.replace("ii", "i"))
        if (canonical.contains("uu")) aliases.add(canonical.replace("uu", "u"))
        // Fully-collapsed combined alias: apply all three rules in sequence
        val collapsed = canonical.replace("chh", "c").replace("ii", "i").replace("uu", "u")
        aliases.add(collapsed) // linkedSet dedupes when equal to an existing entry
        return aliases.toList()
    }
}
