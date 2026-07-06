package com.banglu.engine.ai

import com.banglu.engine.types.BigramModelData
import com.banglu.engine.types.PredictedWord
import kotlin.math.ln

class BigramModel {
    private val unigrams = mutableMapOf<String, Int>()
    private val bigrams = mutableMapOf<String, Int>()
    private val trigrams = mutableMapOf<String, Int>()
    private var totalUnigrams = 0
    private var totalBigrams = 0
    private var loaded = false
    private val bigramIndex = mutableMapOf<String, MutableList<Pair<String, Int>>>()
    // (w1\tw2) -> followers, for two-word next-word prediction.
    private val trigramIndex = mutableMapOf<String, MutableList<Pair<String, Int>>>()

    fun isLoaded(): Boolean = loaded

    fun loadFromData(data: BigramModelData) {
        unigrams.clear()
        bigrams.clear()
        bigramIndex.clear()
        trigrams.clear()
        trigramIndex.clear()
        unigrams.putAll(data.unigrams)
        bigrams.putAll(data.bigrams)
        trigrams.putAll(data.trigrams)
        totalUnigrams = data.totalUnigrams
        totalBigrams = data.totalBigrams
        for ((key, count) in data.trigrams) {
            val lastTab = key.lastIndexOf('\t')
            if (lastTab <= 0) continue
            trigramIndex.getOrPut(key.substring(0, lastTab)) { mutableListOf() }
                .add(key.substring(lastTab + 1) to count)
        }
        for ((_, followers) in trigramIndex) {
            followers.sortByDescending { it.second }
            if (followers.size > 30) {
                val trimmed = followers.take(30).toMutableList()
                followers.clear(); followers.addAll(trimmed)
            }
        }

        // Build inverted index
        for ((key, count) in data.bigrams) {
            val parts = key.split("\t")
            if (parts.size == 2) {
                bigramIndex.getOrPut(parts[0]) { mutableListOf() }.add(parts[1] to count)
            }
        }
        // Sort followers by count desc, keep top 50
        for ((_, followers) in bigramIndex) {
            followers.sortByDescending { it.second }
            if (followers.size > 50) {
                val trimmed = followers.take(50).toMutableList()
                followers.clear()
                followers.addAll(trimmed)
            }
        }
        loaded = true
    }

    /**
     * Raw observed count for the (word1, word2) pair — the context-evidence
     * oracle for promotion decisions (S4): [bigramProb]'s unigram
     * interpolation always returns SOMETHING, so callers that flip a primary
     * word must check that the pair was actually observed in the corpus.
     */
    fun bigramCount(word1: String, word2: String): Int = bigrams["$word1\t$word2"] ?: 0

    /**
     * S20: raw observed trigram count — the promotion oracle for two-word
     * context, mirroring [bigramCount]'s role (S4: probability interpolation
     * always returns SOMETHING; primary flips need observed evidence).
     */
    fun trigramCount(w1: String, w2: String, w3: String): Int =
        trigrams["$w1\t$w2\t$w3"] ?: 0

    fun hasTrigrams(): Boolean = trigrams.isNotEmpty()

    /**
     * S20 backoff score for candidate [w3] after [w1] [w2]: trigram evidence
     * dominates, bigram next, unigram floor.
     */
    fun contextProb(w1: String, w2: String, w3: String): Double {
        val triCount = trigrams["$w1\t$w2\t$w3"] ?: 0
        // Condition on the trigram context itself: total observed continuations
        // of (w1, w2). A context the corpus never saw contributes nothing and
        // the score backs off to the bigram layer cleanly.
        val contextTotal = trigramIndex["$w1\t$w2"]?.sumOf { it.second } ?: 0
        val pTri = if (contextTotal > 0) triCount.toDouble() / contextTotal else 0.0
        return 0.55 * pTri + 0.45 * bigramProb(w2, w3)
    }

    /** Followers of the exact (w1, w2) context, frequency-descending. */
    fun getTopTrigramPredictions(w1: String, w2: String, limit: Int = 5): List<PredictedWord> {
        if (!loaded) return emptyList()
        val followers = trigramIndex["$w1\t$w2"] ?: return emptyList()
        val total = followers.sumOf { it.second }.toDouble()
        return followers.take(limit).map { PredictedWord(it.first, it.second / total) }
    }

    fun unigramProb(word: String): Double {
        val count = unigrams[word] ?: 0
        val vocabSize = maxOf(unigrams.size, 1)
        return (count + 1).toDouble() / (totalUnigrams + vocabSize)
    }

    fun bigramProb(word1: String, word2: String): Double {
        val bigramKey = "$word1\t$word2"
        val bigramCount = bigrams[bigramKey] ?: 0
        val word1Count = unigrams[word1] ?: 0
        val vocabSize = maxOf(unigrams.size, 1)

        val pBigram = if (word1Count > 0) {
            (bigramCount + 1).toDouble() / (word1Count + vocabSize)
        } else {
            1.0 / vocabSize
        }
        val pUnigram = unigramProb(word2)
        return 0.7 * pBigram + 0.3 * pUnigram
    }

    fun scoreSequence(words: List<String>): Double {
        var logProb = 0.0
        for ((i, word) in words.withIndex()) {
            logProb += if (i == 0) ln(unigramProb(word))
            else ln(bigramProb(words[i - 1], word))
        }
        return logProb
    }

    fun getTopPredictions(prevWord: String, limit: Int = 5): List<PredictedWord> {
        if (!loaded) return emptyList()
        val followers = bigramIndex[prevWord] ?: return emptyList()
        val totalCount = followers.sumOf { it.second }.toDouble()
        return followers.take(limit).map { PredictedWord(it.first, it.second / totalCount) }
    }
}
