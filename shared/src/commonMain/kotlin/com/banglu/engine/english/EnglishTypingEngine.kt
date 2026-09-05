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
        /**
         * S97: minimum weighted score for an autocorrect replacement.
         * score = editWeight * (12 - ln(rank+10)) — log-scale, so commonness
         * matters but doesn't steamroll the edit shape (linear scoring made
         * help@132 beat hello@194 for "helo"). Admits roughly: trusted edits
         * (transposition/doubled-letter) to rank ~6500, substitution to
         * ~1400 — a rare word never replaces what the user typed.
         */
        const val MIN_AUTOCORRECT_SCORE = 3.2
        /** S185: an inflection of the typed word outranks any plain prefix completion. */
        const val INFLECTION_BASE = 5_000.0
    }

    private val ranks = HashMap<String, Int>(EnglishWordData.WORDS.size * 2)
    private val sorted: Array<String>
    private val userCounts = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()

    /** S185: corpus next-word context (EnglishBigramData), parsed on first use. */
    private val corpusBigrams: HashMap<String, List<String>> by lazy {
        HashMap<String, List<String>>(EnglishBigramData.ENTRIES.size * 2).also { m ->
            for (e in EnglishBigramData.ENTRIES) {
                val i = e.indexOf(':')
                if (i > 0) m[e.substring(0, i)] = e.substring(i + 1).split(',')
            }
        }
    }

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
        val typedIsWord = prefix in ranks
        while (i < sorted.size && sorted[i].startsWith(prefix)) {
            val w = sorted[i]
            // S185: the typed word's own contractions (can → can't) are
            // variations too and rank with the inflections.
            val contraction = if (typedIsWord && w.length > prefix.length && w[prefix.length] == '\'') INFLECTION_BASE else 0.0
            offer(w, contraction + baseScore(w) + USER_COUNT_WEIGHT * (userCounts[w] ?: 0))
            i++
        }
        // S185 (user: "when typing receive show the variations — receiving,
        // received"): a fully typed known word offers its inflections first,
        // including the forms a prefix scan cannot reach (e-drop receiving,
        // y→i tries, doubled stopped). Each form must itself be a known word.
        if (typedIsWord) {
            for (form in inflections(prefix)) offer(form, INFLECTION_BASE + baseScore(form) + USER_COUNT_WEIGHT * (userCounts[form] ?: 0))
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
            // S185: corpus context after the personal pairs (thank → you,
            // how → to/much, i → have/am).
            corpusBigrams[prev]?.forEach { if (out.size < limit) out.add(it) }
        }
        // Fill with the most common sentence-position words.
        var i = 0
        while (out.size < limit && i < EnglishWordData.WORDS.size) {
            out.add(EnglishWordData.WORDS[i]); i++
        }
        return out.map { displayWord(it) }
    }

    /**
     * S97: autocorrect-on-commit. Returns the replacement for a mistyped
     * word, or null when the typed word must be left alone. The contract:
     *
     *  - KNOWN words are never touched — wordlist membership, ANY personal
     *    use (count ≥ 1, which is what the undo chip writes), contractions.
     *  - ALL-CAPS tokens (acronyms) and words under 3 letters are never
     *    touched.
     *  - The replacement must be a COMMON word (or an established personal
     *    word) within ONE weighted edit — deletion/transposition are the
     *    most trusted typo shapes, substitution the least (S22 weighting);
     *    apostrophe insertion is an edit too (dont -> don't).
     *  - Case mirrors the typed word (Teh -> The).
     */
    fun autocorrect(typedRaw: String): String? {
        val typed = normalize(typedRaw) ?: return null
        if (typed.length < 3) return null
        if (typed in ranks) return null
        if ((userCounts[typed] ?: 0) > 0) return null
        val trimmed = typedRaw.trim()
        if (trimmed.length >= 2 && trimmed.all { it.isUpperCase() }) return null

        var best: String? = null
        var bestScore = 0.0
        fun consider(candidate: String, editWeight: Double) {
            if (candidate == typed || candidate.length < 2) return
            val rank = ranks[candidate]
            val userCount = userCounts[candidate] ?: 0
            val effectiveRank = when {
                rank != null -> rank
                // The user's established words correct toward too (names).
                userCount >= UNKNOWN_WORD_MIN_COUNT -> 300
                else -> return
            }
            val score = editWeight * (12.0 - kotlin.math.ln((effectiveRank + 10).toDouble()))
            if (score > bestScore) { bestScore = score; best = candidate }
        }
        forEachEditOneVariant(typed) { variant, weight -> consider(variant, weight) }
        if (bestScore < MIN_AUTOCORRECT_SCORE) return null
        return best?.let { applyCase(typedRaw, it) }
    }

    /**
     * The S22-shaped weighted edit-distance-1 enumeration (deletion 0.9,
     * doubled-letter deletion + transposition 1.0, substitution 0.7,
     * insertion 0.95 vowel / 0.75 consonant / 0.95 apostrophe — the
     * apostrophe is what turns dont into don't).
     */
    private inline fun forEachEditOneVariant(word: String, action: (variant: String, weight: Double) -> Unit) {
        val n = word.length
        val alphabet = "abcdefghijklmnopqrstuvwxyz'"
        for (i in 0 until n) {
            action(word.removeRange(i, i + 1), if (i > 0 && word[i] == word[i - 1]) 1.0 else 0.9)
            if (i < n - 1 && word[i] != word[i + 1]) {
                val t = word.toCharArray().also { val c = it[i]; it[i] = it[i + 1]; it[i + 1] = c }
                action(t.concatToString(), 1.0)
            }
            for (ch in alphabet) if (ch != word[i] && ch != '\'') {
                action(word.substring(0, i) + ch + word.substring(i + 1), 0.7)
            }
        }
        for (i in 0..n) for (ch in alphabet) {
            // Inserting a letter NEXT TO ITS TWIN is the missed-double-press
            // typo (helo -> hello) — as trusted as a doubled-letter deletion.
            // An OMITTED APOSTROPHE (wasnt -> wasn't) is the most common
            // English typo of all — more trusted than any other edit, or the
            // s-deletion reading (want) outscores the obvious contraction.
            val doublesNeighbor = (i < n && word[i] == ch) || (i > 0 && word[i - 1] == ch)
            val w = when {
                ch == '\'' -> 1.15
                doublesNeighbor -> 1.0
                ch in "aeiou" -> 0.95
                else -> 0.75
            }
            action(word.substring(0, i) + ch + word.substring(i), w)
        }
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

    /**
     * S185: candidate inflected forms of [word] that are themselves known words
     * — plural/3rd person (-s/-es/-ies), past (-ed/-d/-ied/doubled), -ing
     * (with e-drop, doubling, ie→ying), comparative (-er/-r/-ier) and adverb
     * (-ly/-ily). Ordered by the form's own frequency rank.
     */
    internal fun inflections(word: String): List<String> {
        if (word.length < 2 || !word.all { it in 'a'..'z' }) return emptyList()
        val c = ArrayList<String>(12)
        val last = word.last()
        val vowels = "aeiou"
        val endsCY = last == 'y' && word.length >= 2 && word[word.length - 2] !in vowels
        val endsE = last == 'e'
        val cvc = word.length >= 3 && last !in vowels && last !in "wxy" &&
            word[word.length - 2] in vowels && word[word.length - 3] !in vowels
        val stem = word.dropLast(1)
        // -s
        c += word + "s"; c += word + "es"; if (endsCY) c += stem + "ies"
        // -ed
        c += word + "ed"; if (endsE) c += word + "d"; if (endsCY) c += stem + "ied"; if (cvc) c += word + last + "ed"
        // -ing
        c += word + "ing"; if (endsE && !word.endsWith("ee")) c += stem + "ing"; if (cvc) c += word + last + "ing"; if (word.endsWith("ie")) c += word.dropLast(2) + "ying"
        // -er / -ly
        c += word + "er"; if (endsE) c += word + "r"; if (endsCY) c += stem + "ier"; if (cvc) c += word + last + "er"
        c += word + "ly"; if (endsCY) c += stem + "ily"
        return c.distinct().filter { it != word && it in ranks }.sortedBy { ranks[it] }
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
