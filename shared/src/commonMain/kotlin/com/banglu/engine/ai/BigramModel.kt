package com.banglu.engine.ai

import com.banglu.engine.types.BigramModelData
import com.banglu.engine.types.PredictedWord
import kotlin.math.ln

/**
 * Corpus n-gram model.
 *
 * S101 memory layout: the previous implementation held five string-keyed
 * HashMaps ("w1\tw2" keys, boxed counts) — ~35MB of the full-mode Java heap
 * and the second-largest resident structure after the dictionary trie. Every
 * n-gram word is stored ONCE in a sorted pool; pairs and triples are packed
 * 21-bit pool ids in sorted LongArrays with parallel IntArray counts
 * (binary-search lookup), and the follower indexes are flattened primitive
 * arrays built with the exact grouping/sort/trim semantics of the old maps
 * (stable count-descending sort over data-iteration order, top 50 bigram /
 * top 30 trigram, probability denominators over the TRIMMED sets). Public
 * behavior is unchanged; only the storage is.
 */
class BigramModel {

    private var pool: Array<String> = emptyArray()
    private var unigramCounts: IntArray = IntArray(0)
    private var unigramEntryCount = 0
    private var totalUnigrams = 0

    private var pairKeys: LongArray = LongArray(0)
    private var pairCounts: IntArray = IntArray(0)
    private var triKeys: LongArray = LongArray(0)
    private var triCounts: IntArray = IntArray(0)

    // Followers of one previous word (bigram) / one packed (w1,w2) context
    // (trigram): contexts sorted for binary search, offsets delimit each
    // context's slice in the flattened id/count arrays.
    private var bigramPrevIds: IntArray = IntArray(0)
    private var bigramFollowerOffsets: IntArray = IntArray(1)
    private var bigramFollowerIds: IntArray = IntArray(0)
    private var bigramFollowerCounts: IntArray = IntArray(0)

    private var triContexts: LongArray = LongArray(0)
    private var triFollowerOffsets: IntArray = IntArray(1)
    private var triFollowerIds: IntArray = IntArray(0)
    private var triFollowerCounts: IntArray = IntArray(0)

    private var loaded = false

    fun isLoaded(): Boolean = loaded

    private fun wordId(word: String): Int {
        var lo = 0
        var hi = pool.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = pool[mid].compareTo(word)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun pack2(a: Int, b: Int): Long = (a.toLong() shl ID_BITS) or b.toLong()

    private fun pack3(a: Int, b: Int, c: Int): Long =
        (a.toLong() shl (2 * ID_BITS)) or (b.toLong() shl ID_BITS) or c.toLong()

    private fun longIndexOf(keys: LongArray, key: Long): Int {
        var lo = 0
        var hi = keys.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = keys[mid]
            when {
                v < key -> lo = mid + 1
                v > key -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun intIndexOf(keys: IntArray, key: Int): Int {
        var lo = 0
        var hi = keys.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = keys[mid]
            when {
                v < key -> lo = mid + 1
                v > key -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    fun loadFromData(data: BigramModelData) {
        // ── word pool ────────────────────────────────────────────────────
        val words = HashSet<String>(data.unigrams.size * 2)
        words.addAll(data.unigrams.keys)
        for (key in data.bigrams.keys) {
            val tab = key.indexOf('\t')
            if (tab <= 0) continue
            words.add(key.substring(0, tab))
            words.add(key.substring(tab + 1))
        }
        for (key in data.trigrams.keys) {
            val t1 = key.indexOf('\t')
            val t2 = if (t1 >= 0) key.indexOf('\t', t1 + 1) else -1
            if (t1 <= 0 || t2 <= t1) continue
            words.add(key.substring(0, t1))
            words.add(key.substring(t1 + 1, t2))
            words.add(key.substring(t2 + 1))
        }
        pool = words.toTypedArray().also { it.sort() }

        unigramCounts = IntArray(pool.size)
        for ((word, count) in data.unigrams) {
            val id = wordId(word)
            if (id >= 0) unigramCounts[id] = count
        }
        unigramEntryCount = data.unigrams.size
        totalUnigrams = data.totalUnigrams

        // ── exact-count tables (untrimmed, sorted for binary search) ─────
        run {
            val keys = LongArray(data.bigrams.size)
            val counts = IntArray(data.bigrams.size)
            var n = 0
            for ((key, count) in data.bigrams) {
                val tab = key.indexOf('\t')
                if (tab <= 0) continue
                val a = wordId(key.substring(0, tab))
                val b = wordId(key.substring(tab + 1))
                if (a < 0 || b < 0) continue
                keys[n] = pack2(a, b)
                counts[n] = count
                n++
            }
            val order = (0 until n).sortedBy { keys[it] }
            pairKeys = LongArray(n) { keys[order[it]] }
            pairCounts = IntArray(n) { counts[order[it]] }
        }
        run {
            val keys = LongArray(data.trigrams.size)
            val counts = IntArray(data.trigrams.size)
            var n = 0
            for ((key, count) in data.trigrams) {
                val t1 = key.indexOf('\t')
                val t2 = if (t1 >= 0) key.indexOf('\t', t1 + 1) else -1
                if (t1 <= 0 || t2 <= t1) continue
                val a = wordId(key.substring(0, t1))
                val b = wordId(key.substring(t1 + 1, t2))
                val c = wordId(key.substring(t2 + 1))
                if (a < 0 || b < 0 || c < 0) continue
                keys[n] = pack3(a, b, c)
                counts[n] = count
                n++
            }
            val order = (0 until n).sortedBy { keys[it] }
            triKeys = LongArray(n) { keys[order[it]] }
            triCounts = IntArray(n) { counts[order[it]] }
        }

        // ── follower indexes (legacy grouping/trim semantics) ────────────
        run {
            val byPrev = LinkedHashMap<Int, MutableList<Pair<Int, Int>>>()
            for ((key, count) in data.bigrams) {
                val tab = key.indexOf('\t')
                if (tab <= 0) continue
                val prev = wordId(key.substring(0, tab))
                val next = wordId(key.substring(tab + 1))
                if (prev < 0 || next < 0) continue
                byPrev.getOrPut(prev) { mutableListOf() }.add(next to count)
            }
            flattenFollowers(byPrev.mapKeys { it.key.toLong() }, MAX_BIGRAM_FOLLOWERS) { prevs, offsets, ids, counts ->
                bigramPrevIds = IntArray(prevs.size) { prevs[it].toInt() }
                bigramFollowerOffsets = offsets
                bigramFollowerIds = ids
                bigramFollowerCounts = counts
            }
        }
        run {
            val byContext = LinkedHashMap<Long, MutableList<Pair<Int, Int>>>()
            for ((key, count) in data.trigrams) {
                val t1 = key.indexOf('\t')
                val t2 = if (t1 >= 0) key.indexOf('\t', t1 + 1) else -1
                if (t1 <= 0 || t2 <= t1) continue
                val a = wordId(key.substring(0, t1))
                val b = wordId(key.substring(t1 + 1, t2))
                val c = wordId(key.substring(t2 + 1))
                if (a < 0 || b < 0 || c < 0) continue
                byContext.getOrPut(pack2(a, b)) { mutableListOf() }.add(c to count)
            }
            flattenFollowers(byContext, MAX_TRIGRAM_FOLLOWERS) { contexts, offsets, ids, counts ->
                triContexts = contexts
                triFollowerOffsets = offsets
                triFollowerIds = ids
                triFollowerCounts = counts
            }
        }

        loaded = true
    }

    private inline fun flattenFollowers(
        grouped: Map<Long, MutableList<Pair<Int, Int>>>,
        maxPerContext: Int,
        assign: (LongArray, IntArray, IntArray, IntArray) -> Unit,
    ) {
        // Stable count-descending sort per context (ties keep data order —
        // the old maps' behavior), then trim before flattening.
        val contexts = grouped.keys.toLongArray().also { it.sort() }
        val offsets = IntArray(contexts.size + 1)
        var total = 0
        for (i in contexts.indices) {
            offsets[i] = total
            total += minOf(grouped.getValue(contexts[i]).size, maxPerContext)
        }
        offsets[contexts.size] = total
        val ids = IntArray(total)
        val counts = IntArray(total)
        for (i in contexts.indices) {
            val followers = grouped.getValue(contexts[i]).sortedByDescending { it.second }
            var out = offsets[i]
            for (j in 0 until minOf(followers.size, maxPerContext)) {
                ids[out] = followers[j].first
                counts[out] = followers[j].second
                out++
            }
        }
        assign(contexts, offsets, ids, counts)
    }

    /**
     * Raw observed count for the (word1, word2) pair — the context-evidence
     * oracle for promotion decisions (S4): [bigramProb]'s unigram
     * interpolation always returns SOMETHING, so callers that flip a primary
     * word must check that the pair was actually observed in the corpus.
     */
    fun bigramCount(word1: String, word2: String): Int {
        val a = wordId(word1)
        if (a < 0) return 0
        val b = wordId(word2)
        if (b < 0) return 0
        val idx = longIndexOf(pairKeys, pack2(a, b))
        return if (idx >= 0) pairCounts[idx] else 0
    }

    /**
     * S20: raw observed trigram count — the promotion oracle for two-word
     * context, mirroring [bigramCount]'s role (S4: probability interpolation
     * always returns SOMETHING; primary flips need observed evidence).
     */
    fun trigramCount(w1: String, w2: String, w3: String): Int {
        val a = wordId(w1)
        if (a < 0) return 0
        val b = wordId(w2)
        if (b < 0) return 0
        val c = wordId(w3)
        if (c < 0) return 0
        val idx = longIndexOf(triKeys, pack3(a, b, c))
        return if (idx >= 0) triCounts[idx] else 0
    }

    fun hasTrigrams(): Boolean = triKeys.isNotEmpty()

    /** Sum of the TRIMMED follower slice for a packed trigram context —
     *  matches the old trigramIndex's post-trim totals exactly. */
    private fun trigramContextTotal(context: Long): Int {
        val idx = longIndexOf(triContexts, context)
        if (idx < 0) return 0
        var sum = 0
        for (i in triFollowerOffsets[idx] until triFollowerOffsets[idx + 1]) {
            sum += triFollowerCounts[i]
        }
        return sum
    }

    /**
     * S20 backoff score for candidate [w3] after [w1] [w2]: trigram evidence
     * dominates, bigram next, unigram floor.
     */
    fun contextProb(w1: String, w2: String, w3: String): Double {
        val triCount = trigramCount(w1, w2, w3)
        val a = wordId(w1)
        val b = wordId(w2)
        // Condition on the trigram context itself: total observed continuations
        // of (w1, w2). A context the corpus never saw contributes nothing and
        // the score backs off to the bigram layer cleanly.
        val contextTotal = if (a >= 0 && b >= 0) trigramContextTotal(pack2(a, b)) else 0
        val pTri = if (contextTotal > 0) triCount.toDouble() / contextTotal else 0.0
        return 0.55 * pTri + 0.45 * bigramProb(w2, w3)
    }

    /** Followers of the exact (w1, w2) context, frequency-descending. */
    fun getTopTrigramPredictions(w1: String, w2: String, limit: Int = 5): List<PredictedWord> {
        if (!loaded) return emptyList()
        val a = wordId(w1)
        if (a < 0) return emptyList()
        val b = wordId(w2)
        if (b < 0) return emptyList()
        val idx = longIndexOf(triContexts, pack2(a, b))
        if (idx < 0) return emptyList()
        val from = triFollowerOffsets[idx]
        val until = triFollowerOffsets[idx + 1]
        var total = 0.0
        for (i in from until until) total += triFollowerCounts[i]
        if (total <= 0.0) return emptyList()
        val out = ArrayList<PredictedWord>(minOf(limit, until - from))
        for (i in from until minOf(from + limit, until)) {
            out.add(PredictedWord(pool[triFollowerIds[i]], triFollowerCounts[i] / total))
        }
        return out
    }

    fun unigramProb(word: String): Double {
        val id = wordId(word)
        val count = if (id >= 0) unigramCounts[id] else 0
        val vocabSize = maxOf(unigramEntryCount, 1)
        return (count + 1).toDouble() / (totalUnigrams + vocabSize)
    }

    fun bigramProb(word1: String, word2: String): Double {
        val a = wordId(word1)
        val b = wordId(word2)
        val bigramCount = if (a >= 0 && b >= 0) {
            val idx = longIndexOf(pairKeys, pack2(a, b))
            if (idx >= 0) pairCounts[idx] else 0
        } else 0
        val word1Count = if (a >= 0) unigramCounts[a] else 0
        val vocabSize = maxOf(unigramEntryCount, 1)

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
        val prev = wordId(prevWord)
        if (prev < 0) return emptyList()
        val idx = intIndexOf(bigramPrevIds, prev)
        if (idx < 0) return emptyList()
        val from = bigramFollowerOffsets[idx]
        val until = bigramFollowerOffsets[idx + 1]
        var total = 0.0
        for (i in from until until) total += bigramFollowerCounts[i]
        if (total <= 0.0) return emptyList()
        val out = ArrayList<PredictedWord>(minOf(limit, until - from))
        for (i in from until minOf(from + limit, until)) {
            out.add(PredictedWord(pool[bigramFollowerIds[i]], bigramFollowerCounts[i] / total))
        }
        return out
    }

    private companion object {
        /** 21-bit pool ids: 3 ids pack into 63 bits, pool capacity 2M words. */
        const val ID_BITS = 21
        const val MAX_BIGRAM_FOLLOWERS = 50
        const val MAX_TRIGRAM_FOLLOWERS = 30
    }
}
