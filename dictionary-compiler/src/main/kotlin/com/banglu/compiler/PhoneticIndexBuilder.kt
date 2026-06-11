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
    val totalRows: Int = 0
) {
    val coveragePercent: Double
        get() = if (totalWords == 0) 0.0 else roundTripOk * 100.0 / totalWords
}

object PhoneticIndexBuilder {

    var lastReport: IndexBuildReport = IndexBuildReport()
        private set

    private val BENGALI_ONLY = Regex("^[ঀ-৿]+$")
    private val ROMAN_ONLY = Regex("^[a-z]+$")

    fun build(words: List<String>, frequencies: Map<String, Int>): List<PhoneticIndexRow> {
        val rows = ArrayList<PhoneticIndexRow>(words.size * 2)
        val seen = HashSet<String>(words.size * 2)
        var roundTripOk = 0
        var total = 0

        for (raw in words) {
            val word = raw.trim()
            if (word.length !in 2..18) continue
            if (!BENGALI_ONLY.matches(word)) continue
            if (word.endsWith("্")) continue
            total++

            val canonical = ReverseTransliterator.reverseWord(word).lowercase()
            if (CleanTransliterator.transliterate(canonical) == word) roundTripOk++

            val freq = frequencies[word] ?: 0
            val tier = if (freq > 0) 0 else 1
            for (key in aliasesFor(canonical)) {
                if (key.length !in 2..24 || !ROMAN_ONLY.matches(key)) continue
                if (!seen.add("$key $word")) continue
                rows.add(PhoneticIndexRow(key, word, freq, tier))
            }
        }
        lastReport = IndexBuildReport(totalWords = total, roundTripOk = roundTripOk, totalRows = rows.size)
        return rows
    }

    /**
     * Typing-habit aliases. Mirrors SmartEngine.corpusPhoneticAliases (chh->c)
     * plus the vowel-length collapses users actually type. Bounded and
     * deterministic; every alias maps back to the same word.
     */
    private fun aliasesFor(canonical: String): List<String> {
        val aliases = linkedSetOf(canonical)
        if (canonical.contains("chh")) aliases.add(canonical.replace("chh", "c"))
        if (canonical.contains("ii")) aliases.add(canonical.replace("ii", "i"))
        if (canonical.contains("ee")) aliases.add(canonical.replace("ee", "i"))
        if (canonical.contains("uu")) aliases.add(canonical.replace("uu", "u"))
        if (canonical.contains("oo")) aliases.add(canonical.replace("oo", "u"))
        return aliases.toList()
    }
}
