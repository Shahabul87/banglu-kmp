package com.banglu.compiler

import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * S5 corpus-authority frequency refresh (study W2,
 * docs/engine-conjunct-study-2026-07-03.md).
 *
 * The legacy word-frequency.json scale carries stale twin-spelling orderings
 * (খন্ড outranking খণ্ড, তৈরী vs তৈরি). This module rebuilds every word's
 * frequency from observed web usage — 12.6M running tokens across two
 * registers — so ranking is evidence, not legacy:
 *
 * - modern register (bnwiki + news), weight 1: the spelling authority
 * - literature register (bnwikisource), weight 1/4: coverage for সাধু forms
 *   without letting archaic spellings outvote modern ones
 *
 * Scale contract: evidenced words (weighted usage >= [TIER_A_MIN_USAGE]) map
 * log-scale into [EVIDENCED_FLOOR]..100, which keeps them Tier-A via the
 * builder's SUGGESTIBLE_MIN_FREQUENCY=60 rule and comparable with seed-layer
 * frequencies. Unevidenced words are capped at [UNEVIDENCED_CAP] so they can
 * never outrank an evidenced word, and fall to Tier B unless the legacy
 * usage list vouches for them.
 */
object CorpusAuthority {

    /** Weighted usage at or above which a word counts as corpus-evidenced. */
    const val TIER_A_MIN_USAGE = 3L

    /** Frequency floor for evidenced words (== builder SUGGESTIBLE_MIN_FREQUENCY). */
    const val EVIDENCED_FLOOR = 60

    /** Frequency cap for words with no corpus evidence. */
    const val UNEVIDENCED_CAP = 55

    /**
     * Add weighted counts from a `word<TAB>count` TSV into [into], nukta-folding
     * words so counts recorded under either encoding merge. Missing files are
     * skipped (fail-soft: the compiler still builds from legacy frequencies).
     * @return number of lines consumed, 0 when the file is absent.
     */
    fun addCounts(file: File, weightNumerator: Long, weightDenominator: Long, into: MutableMap<String, Long>): Int {
        if (!file.exists()) return 0
        var lines = 0
        file.forEachLine { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEachLine
            val count = line.substring(tab + 1).trim().toLongOrNull() ?: return@forEachLine
            val word = ReverseTransliterator.foldNukta(line.substring(0, tab).trim())
            if (word.isEmpty()) return@forEachLine
            val weighted = count * weightNumerator / weightDenominator
            if (weighted > 0) {
                into[word] = (into[word] ?: 0L) + weighted
                lines++
            }
        }
        return lines
    }

    /** Minimum news-register count for a word absent from the base dictionary
     *  to be added as a new dictionary word. News (professionally edited) is
     *  the anchor register: wiki-only candidates carry template artifacts
     *  (বিষয়শ্রেণী, তথ্যছক) and OCR noise, so they are NOT admitted. */
    const val MIN_NEWS_COUNT_FOR_NEW_WORD = 2L

    /** Bengali letters only — excludes digits ০-৯ (U+09E6..U+09EF). */
    private val BENGALI_LETTERS = Regex("^[\\u0980-\\u09E5\\u09F0-\\u09FF]+$")

    /**
     * Vocabulary expansion (data-refresh round): corpus words missing from the
     * base 484k list, anchored in the news register. Covers modern names
     * (ট্রাম্পের, এমবাপ্পে), Academy-modern spellings (শ্রেণির), and compound
     * inflections (অবাস্তবায়নযোগ্য, নিত্যপণ্যে) that real articles use.
     * Returned nukta-folded, count-descending.
     */
    fun newsAnchoredNewWords(newsFile: File, existing: Set<String>): List<String> {
        if (!newsFile.exists()) return emptyList()
        val counts = HashMap<String, Long>(120_000)
        addCounts(newsFile, 1, 1, counts)
        return counts.entries
            .asSequence()
            .filter { (w, c) ->
                c >= MIN_NEWS_COUNT_FOR_NEW_WORD &&
                    w !in existing &&
                    w.length in 2..18 &&
                    BENGALI_LETTERS.matches(w) &&
                    !w.endsWith('্')
            }
            .sortedByDescending { it.value }
            .map { it.key }
            .toList()
    }

    /**
     * Rebuild the frequency map: corpus-evidenced words get a log-scaled
     * [EVIDENCED_FLOOR]..100 frequency ordered by usage; everything else keeps
     * its legacy frequency capped at [UNEVIDENCED_CAP]. Keys of [legacy] are
     * expected nukta-folded already (foldAndDedupe output).
     */
    fun refreshFrequencies(legacy: Map<String, Int>, usage: Map<String, Long>): Map<String, Int> {
        val maxUsage = usage.values.maxOrNull() ?: 0L
        if (maxUsage < TIER_A_MIN_USAGE) return legacy
        val logRange = ln(maxUsage.toDouble() / TIER_A_MIN_USAGE)
        val out = HashMap<String, Int>(legacy.size + usage.size)
        for ((word, freq) in legacy) {
            out[word] = minOf(freq, UNEVIDENCED_CAP)
        }
        for ((word, count) in usage) {
            if (count < TIER_A_MIN_USAGE) continue
            val scaled = if (logRange <= 0.0) EVIDENCED_FLOOR else {
                EVIDENCED_FLOOR +
                    ((100 - EVIDENCED_FLOOR) * ln(count.toDouble() / TIER_A_MIN_USAGE) / logRange).roundToInt()
            }
            out[word] = scaled.coerceIn(EVIDENCED_FLOOR, 100)
        }
        return out
    }
}
