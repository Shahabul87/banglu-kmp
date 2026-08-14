package com.banglu.engine.english

/**
 * S96: the English typing suite — word completion, next-word prediction, and
 * personal learning for the EN keyboard mode. Pure Kotlin, all platforms.
 *
 * Data model:
 *  - [EnglishWordData.WORDS] — 15K common words in frequency order (index =
 *    rank), the "Samsung dictionary" baseline.
 *  - user words — every word the user actually commits in EN mode, counted.
 *    Words the wordlist doesn't know (names, slang) become suggestable once
 *    typed twice — or immediately when the personal-dictionary setting is on
 *    (the "save words" contract). Counts also boost known words, so the
 *    user's own vocabulary rises above the global ranking over time.
 *  - bigrams — prev-word -> follower counts for next-word prediction, same
 *    mechanism as the Bangla personal bigrams (S78), bounded.
 *
 * Concurrency: NOT internally synchronized — every caller must serialize
 * through the platform's single engine lane (the S75 law; JS is
 * single-threaded). Persistence is the caller's job via [serialize]/[load].
 */
class EnglishTypingEngine {

    private companion object {
        const val MAX_USER_WORDS = 2000
        const val MAX_BIGRAM_PREVS = 500
        const val MAX_FOLLOWERS_PER_PREV = 8
        const val MAX_WORD_LENGTH = 24
        const val MAX_COUNT = 10_000
        /** Below this count an out-of-wordlist word is not yet suggestable. */
        const val UNKNOWN_WORD_MIN_COUNT = 2
        /** Score weight of one personal commit vs the global ranking. */
        const val USER_COUNT_WEIGHT = 250.0
        /** Base score of a saved out-of-wordlist word (≈ rank 300 territory). */
        const val UNKNOWN_WORD_BASE = 300.0
    }

    private val ranks = HashMap<String, Int>(EnglishWordData.WORDS.size * 2)
    private val sorted: Array<String>
    private val userCounts = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()

    init {
        EnglishWordData.WORDS.forEachIndexed { i, w -> if (w !in ranks) ranks[w] = i }
        sorted = EnglishWordData.WORDS.toTypedArray().also { it.sort() }
    }

    // ── suggestions ──────────────────────────────────────────────────────

    /**
     * Completions for the word being typed. Empty prefix returns nothing —
     * use [predictions] between words.
     */
    fun completions(prefixRaw: String, limit: Int = 3, personalDictionary: Boolean = true): List<String> {
        val prefix = normalize(prefixRaw) ?: return emptyList()
        if (prefix.isEmpty() || limit <= 0) return emptyList()

        data class Cand(val word: String, val score: Double)
        val best = ArrayList<Cand>(limit + 1)
        fun offer(word: String, score: Double) {
            if (word == prefix) return               // completing, not echoing
            if (best.any { it.word == word }) return
            if (best.size < limit) {
                best.add(Cand(word, score)); best.sortByDescending { it.score }
            } else if (score > best.last().score) {
                best[best.size - 1] = Cand(word, score); best.sortByDescending { it.score }
            }
        }

        // Wordlist range scan (lexicographic slice of the sorted array).
        var i = lowerBound(prefix)
        while (i < sorted.size && sorted[i].startsWith(prefix)) {
            val w = sorted[i]
            offer(w, baseScore(w) + USER_COUNT_WEIGHT * (userCounts[w] ?: 0))
            i++
        }
        // Personal words the wordlist doesn't know (names, slang).
        for ((w, c) in userCounts) {
            if (!w.startsWith(prefix) || w in ranks) continue
            if (c >= UNKNOWN_WORD_MIN_COUNT || (personalDictionary && c >= 1)) {
                offer(w, UNKNOWN_WORD_BASE + USER_COUNT_WEIGHT * c)
            }
        }
        return best.map { applyCase(prefixRaw, it.word) }
    }

    /** Next-word predictions after a committed word (or sentence start). */
    fun predictions(prevRaw: String?, limit: Int = 3): List<String> {
        if (limit <= 0) return emptyList()
        val out = LinkedHashSet<String>()
        val prev = prevRaw?.let { normalize(it) }
        if (prev != null) {
            bigrams[prev]?.entries
                ?.sortedByDescending { it.value }
                ?.forEach { if (out.size < limit) out.add(it.key) }
        }
        // Fill with the most common sentence-position words.
        var i = 0
        while (out.size < limit && i < EnglishWordData.WORDS.size) {
            out.add(EnglishWordData.WORDS[i]); i++
        }
        return out.map { displayWord(it) }
    }

    // ── learning ─────────────────────────────────────────────────────────

    /**
     * A word the user committed in EN mode. The caller gates this on the
     * learning setting; [personalDictionary] only affects how fast unknown
     * words become suggestable (see [completions]).
     */
    fun recordCommit(wordRaw: String, prevRaw: String? = null) {
        val word = normalize(wordRaw) ?: return
        if (word.length < 2 && word != "i" && word != "a") return
        userCounts[word] = ((userCounts[word] ?: 0) + 1).coerceAtMost(MAX_COUNT)
        evictUserWordsIfNeeded()

        val prev = prevRaw?.let { normalize(it) } ?: return
        if (prev.isEmpty()) return
        val followers = bigrams.getOrPut(prev) { HashMap() }
        followers[word] = ((followers[word] ?: 0) + 1).coerceAtMost(MAX_COUNT)
        if (followers.size > MAX_FOLLOWERS_PER_PREV) {
            followers.remove(followers.minByOrNull { it.value }?.key)
        }
        if (bigrams.size > MAX_BIGRAM_PREVS) {
            bigrams.remove(bigrams.minByOrNull { e -> e.value.values.sum() }?.key)
        }
    }

    fun userWordCount(word: String): Int {
        val key = normalize(word) ?: return 0
        return userCounts[key] ?: 0
    }

    fun clearLearning() {
        userCounts.clear()
        bigrams.clear()
    }

    // ── persistence (caller-owned) ───────────────────────────────────────

    /** Line format: `w<TAB>word<TAB>count` and `b<TAB>prev<TAB>next<TAB>count`. */
    fun serialize(): String = buildString {
        for ((w, c) in userCounts) append("w\t").append(w).append('\t').append(c).append('\n')
        for ((p, followers) in bigrams) for ((n, c) in followers) {
            append("b\t").append(p).append('\t').append(n).append('\t').append(c).append('\n')
        }
    }

    /** Tolerant load — malformed lines are skipped, never fatal. */
    fun load(data: String) {
        clearLearning()
        for (line in data.lineSequence()) {
            val parts = line.split('\t')
            when {
                parts.size == 3 && parts[0] == "w" -> {
                    val w = normalize(parts[1]) ?: continue
                    val c = parts[2].toIntOrNull()?.coerceIn(1, MAX_COUNT) ?: continue
                    if (userCounts.size < MAX_USER_WORDS) userCounts[w] = c
                }
                parts.size == 4 && parts[0] == "b" -> {
                    val p = normalize(parts[1]) ?: continue
                    val n = normalize(parts[2]) ?: continue
                    val c = parts[3].toIntOrNull()?.coerceIn(1, MAX_COUNT) ?: continue
                    if (bigrams.size < MAX_BIGRAM_PREVS || p in bigrams) {
                        val followers = bigrams.getOrPut(p) { HashMap() }
                        if (followers.size < MAX_FOLLOWERS_PER_PREV) followers[n] = c
                    }
                }
            }
        }
    }

    // ── internals ────────────────────────────────────────────────────────

    /** Lowercased; only a-z and internal apostrophes; null if not a word. */
    private fun normalize(raw: String): String? {
        val t = raw.trim().lowercase()
        if (t.isEmpty() || t.length > MAX_WORD_LENGTH) return null
        if (!t.all { it in 'a'..'z' || it == '\'' }) return null
        if (t.first() == '\'' || t.last() == '\'') return null
        return t
    }

    private fun baseScore(word: String): Double {
        val rank = ranks[word] ?: return 0.0
        return 10_000.0 / (rank + 10)
    }

    /** First index in [sorted] not lexicographically before [prefix]. */
    private fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun evictUserWordsIfNeeded() {
        if (userCounts.size <= MAX_USER_WORDS) return
        // Drop the rarest words first; ties broken arbitrarily. One compact
        // pass — this runs at most once per commit past the cap.
        val toDrop = userCounts.size - MAX_USER_WORDS
        userCounts.entries.sortedBy { it.value }.take(toDrop).forEach { userCounts.remove(it.key) }
    }

    /** Mirror the typed case: First-cap and ALL-CAPS prefixes carry over. */
    private fun applyCase(typedPrefix: String, word: String): String {
        val display = displayWord(word)
        val p = typedPrefix.trim()
        return when {
            p.length >= 2 && p.all { it.isUpperCase() } -> display.uppercase()
            p.firstOrNull()?.isUpperCase() == true ->
                display.replaceFirstChar { it.uppercaseChar() }
            else -> display
        }
    }

    /** "i" and its contractions always display with a capital I. */
    private fun displayWord(word: String): String = when {
        word == "i" -> "I"
        word.startsWith("i'") -> "I" + word.substring(1)
        else -> word
    }
}
