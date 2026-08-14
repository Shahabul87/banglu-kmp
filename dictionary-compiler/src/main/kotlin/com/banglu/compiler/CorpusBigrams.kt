package com.banglu.compiler

import com.banglu.engine.util.ReverseTransliterator
import java.io.File

/**
 * S9 corpus bigram model (docs/engine-conjunct-study-2026-07-03.md follow-up).
 *
 * Builds the bigram tables from observed pairs in the 2026-07 web corpus:
 * modern register (bnwiki) weighted [MODERN_WEIGHT]x over literature
 * (bnwikisource), pairs kept at weighted count >= [MIN_PAIR_COUNT]
 * (~115k pairs — 4.5x the legacy model, bounded for 256MB-heap devices).
 * Both words of a pair must be dictionary words so the engine's context
 * reranker never promotes an out-of-dictionary string.
 *
 * Unigram counts come from the same weighted corpus counts used for S5
 * frequency authority, so bigramProb's unigram interpolation is consistent
 * with the words-table frequencies.
 */
object CorpusBigrams {

    const val MODERN_WEIGHT = 4L

    /**
     * S100: the conversational register (OpenSubtitles dialogue) is the
     * TARGET register for a chat keyboard — one observed subtitle pair is
     * worth four wiki pairs. The subtitle corpus is small (2.8M tokens), so
     * this authority only decides conversational rows (বই -> পড়তে); heavy
     * wiki rows (করা -> হয় @13.7K) are untouched by it.
     */
    const val CHAT_WEIGHT = 16L
    const val MIN_PAIR_COUNT = 24L
    const val MIN_UNIGRAM_COUNT = 10L

    /** S100: device-heap bound on the pair table (mirrors the trigram cap). */
    const val MAX_PAIRS = 150_000

    data class Model(
        val unigrams: Map<String, Int>,
        val pairs: Map<Pair<String, String>, Int>,
        val totalUnigrams: Int,
        val totalBigrams: Int
    )

    fun build(
        modern: File,
        literature: File,
        unigramCounts: Map<String, Long>,
        dictionaryWords: Set<String>,
        chat: File? = null,
    ): Model? {
        if (!modern.exists() && !literature.exists()) return null

        val weighted = HashMap<Pair<String, String>, Long>(1_500_000)
        addPairs(modern, MODERN_WEIGHT, weighted)
        addPairs(literature, 1L, weighted)
        chat?.let { addPairs(it, CHAT_WEIGHT, weighted) }

        // S100: the chat register's 16x weight admits far more pairs than
        // the old wiki-only blend (317K vs 98K on the first S100 build) —
        // the pair table is memory the 256MB-heap devices must load, so it
        // is bounded like the trigram table: top [MAX_PAIRS] by weighted
        // count, register-blind.
        val eligible = ArrayList<Pair<Pair<String, String>, Long>>(400_000)
        for ((pair, count) in weighted) {
            if (count < MIN_PAIR_COUNT) continue
            if (pair.first !in dictionaryWords || pair.second !in dictionaryWords) continue
            eligible.add(pair to count)
        }
        eligible.sortByDescending { it.second }
        val pairs = HashMap<Pair<String, String>, Int>(MAX_PAIRS * 2)
        var totalBigrams = 0L
        for ((pair, count) in eligible.take(MAX_PAIRS)) {
            val scaled = (count / MODERN_WEIGHT).coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
            pairs[pair] = scaled
            totalBigrams += scaled
        }

        val unigrams = HashMap<String, Int>(150_000)
        var totalUnigrams = 0L
        for ((word, count) in unigramCounts) {
            if (count < MIN_UNIGRAM_COUNT || word !in dictionaryWords) continue
            val c = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            unigrams[word] = c
            totalUnigrams += c
        }

        return Model(
            unigrams = unigrams,
            pairs = pairs,
            totalUnigrams = totalUnigrams.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            totalBigrams = totalBigrams.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
    }

    private fun addPairs(file: File, weight: Long, into: MutableMap<Pair<String, String>, Long>) {
        if (!file.exists()) return
        file.forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@forEachLine
            val count = parts[2].toLongOrNull() ?: return@forEachLine
            val a = ReverseTransliterator.foldNukta(parts[0])
            val b = ReverseTransliterator.foldNukta(parts[1])
            if (a.isEmpty() || b.isEmpty()) return@forEachLine
            val key = a to b
            into[key] = (into[key] ?: 0L) + count * weight
        }
    }
}
