package com.banglu.compiler

import com.banglu.engine.util.ReverseTransliterator
import java.io.File

/**
 * S20 corpus trigram model (register study 2026-07-06 follow-up).
 *
 * The register study showed the dominant residual miss class is inherent
 * homophones (মত/মোট, হাত/হাট, পরে/পড়ে) where one-word bigram context is
 * often not discriminating enough. Trigram evidence — the two previous
 * committed words — is the next lever.
 *
 * Sources: any `*_trigrams.tsv` file with lines `w1<TAB>w2<TAB>w3<TAB>count`.
 * News/modern sources weighted [MODERN_WEIGHT]x over literature, triples kept
 * at weighted count >= [MIN_TRIPLE_COUNT] and capped at [MAX_TRIPLES] by
 * weighted count (heap safety: every row is resident on a 256MB-heap device).
 * All three words must be dictionary words, mirroring CorpusBigrams, so the
 * context reranker can never promote an out-of-dictionary string.
 */
object CorpusTrigrams {

    const val MODERN_WEIGHT = 4L
    const val MIN_TRIPLE_COUNT = 12L
    const val MAX_TRIPLES = 120_000

    data class Model(
        val triples: Map<Triple<String, String, String>, Int>,
        val totalTriples: Int
    )

    fun build(
        sources: List<Pair<File, Long>>,
        dictionaryWords: Set<String>
    ): Model? {
        val existing = sources.filter { it.first.exists() }
        if (existing.isEmpty()) return null

        val weighted = HashMap<Triple<String, String, String>, Long>(2_000_000)
        for ((file, weight) in existing) addTriples(file, weight, weighted)

        val kept = weighted.entries
            .asSequence()
            .filter { it.value >= MIN_TRIPLE_COUNT }
            .filter {
                it.key.first in dictionaryWords &&
                    it.key.second in dictionaryWords &&
                    it.key.third in dictionaryWords
            }
            .sortedByDescending { it.value }
            .take(MAX_TRIPLES)
            .toList()

        if (kept.isEmpty()) return null
        val triples = HashMap<Triple<String, String, String>, Int>(kept.size * 2)
        var total = 0L
        for (entry in kept) {
            val scaled = (entry.value / MODERN_WEIGHT)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
            triples[entry.key] = scaled
            total += scaled
        }
        return Model(triples, total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    private fun addTriples(
        file: File,
        weight: Long,
        into: HashMap<Triple<String, String, String>, Long>
    ) {
        file.forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size != 4) return@forEachLine
            val count = parts[3].toLongOrNull() ?: return@forEachLine
            val triple = Triple(
                ReverseTransliterator.foldNukta(parts[0]),
                ReverseTransliterator.foldNukta(parts[1]),
                ReverseTransliterator.foldNukta(parts[2])
            )
            into[triple] = (into[triple] ?: 0L) + count * weight
        }
    }
}
