package com.banglu.engine.dictionary

import com.banglu.engine.util.ReverseTransliterator

/**
 * Validates Bengali words against a loaded dictionary (up to 480K words).
 *
 * Provides O(log n) validation and prefix search via binary search on one
 * sorted word array. Also supports frequency data for ranking purposes.
 *
 * Nukta canonicalization (S2): the compiled `words` table stores the
 * nukta-FOLDED (precomposed ড়/ঢ়/য়) form only, while engine rule layers and
 * seed data still emit decomposed sequences. Every boundary here folds via
 * [ReverseTransliterator.foldNukta] — both loaded data and queries — so
 * membership/frequency checks are encoding-insensitive. foldNukta has an
 * O(n) no-nukta fast path, so non-nukta lookups stay cheap.
 *
 * Memory contract (S4/C2): full-mode load on a 256MB-heap device sits within
 * a few MB of the limit, so this class holds exactly ONE word-list-sized
 * structure — a sorted reference array. S2's `map{fold}` + `sorted()` shape
 * (two extra full list copies) plus the previous always-on HashSet
 * (~20MB of node overhead duplicating the sorted array's content) OOMed
 * 256MB-heap flagships during [loadWords]; membership is now a ~19-step
 * binary search, which is invisible next to the engine work around it.
 */
class BengaliWordValidator {

    private var sortedWords: List<String> = emptyList()
    private var loaded: Boolean = false

    // ── S104 compact storage ─────────────────────────────────────────────
    // 476K Java strings cost ~23MB (24B object overhead + UTF-16 payload
    // each). Folded Bengali words live almost entirely in U+0980–U+09FF, so
    // each char packs into ONE byte via a strictly order-preserving table —
    // the whole list becomes a ~4MB blob + ~1.9MB offsets, decoded on
    // demand. SAFETY: if any word carries a character outside the table
    // (impossible in today's compiled db — jvmTest gates it), the load
    // silently keeps the legacy string storage; correctness never depends
    // on the encoding.
    private var compactBlob: ByteArray = ByteArray(0)
    private var compactOffsets: IntArray = IntArray(0)
    private var compact: Boolean = false

    /** Word count independent of storage mode. */
    private val wordCount: Int
        get() = if (compact) compactOffsets.size - 1 else sortedWords.size

    /** True when the compact byte-blob storage is active (test observability). */
    fun isCompactStorage(): Boolean = compact

    /**
     * Frequencies for member words, parallel to [sortedWords] (S4/C2): an
     * IntArray costs 4 bytes/word; the previous HashMap<String, Int> cost a
     * node + boxed Int per entry (~50MB at 472K rows) and duplicated the
     * cursor's key strings. Entries whose key is NOT a member word land in
     * the small [frequencyOverflow] map (legacy/test sources only — the
     * production frequency source IS the words table).
     */
    private var frequenciesByIndex: IntArray = IntArray(0)
    private val frequencyOverflow: MutableMap<String, Int> = mutableMapOf()
    private var hasFrequencies: Boolean = false

    /**
     * Load a list of Bengali words into the validator.
     * Replaces any previously loaded words.
     *
     * Streaming fold (S4/C2): each word is folded as it is written into the
     * single backing array — no intermediate folded copy of the (472K-row)
     * input list is ever materialized. The array is then sorted in place and
     * deduplicated in place. foldNukta's no-nukta fast path returns the same
     * instance, so entries alias the caller's strings rather than duplicating
     * them (the compiled words table is already stored folded).
     *
     * @param wordList List of Bengali words to load
     */
    fun loadWords(wordList: List<String>) {
        val previousWords = if (loaded) getSortedWords().toList() else emptyList()
        val previousFreqs = frequenciesByIndex
        val sorted = Array(wordList.size) { ReverseTransliterator.foldNukta(wordList[it]) }
        sorted.sort()
        // In-place dedupe of the sorted array (folding can merge encodings;
        // legacy sources may carry duplicate rows). No extra allocation.
        var unique = 0
        for (i in sorted.indices) {
            if (unique == 0 || sorted[i] != sorted[unique - 1]) {
                sorted[unique] = sorted[i]
                unique++
            }
        }
        // S104: try the compact encoding first — the monotone char->byte
        // table preserves the exact sort order, so the string array can be
        // dropped entirely. Any unmappable character anywhere keeps the
        // legacy string storage for the WHOLE load (fail-safe).
        var byteLength = 0
        var encodable = true
        outer@ for (i in 0 until unique) {
            val w = sorted[i]
            for (ch in w) {
                if (encodeChar(ch) < 0) { encodable = false; break@outer }
            }
            byteLength += w.length
        }
        if (encodable) {
            val blob = ByteArray(byteLength)
            val offsets = IntArray(unique + 1)
            var out = 0
            for (i in 0 until unique) {
                offsets[i] = out
                val w = sorted[i]
                for (ch in w) blob[out++] = encodeChar(ch).toByte()
            }
            offsets[unique] = out
            compactBlob = blob
            compactOffsets = offsets
            compact = true
            sortedWords = emptyList()
        } else {
            val view = sorted.asList()
            sortedWords = if (unique == sorted.size) view else view.subList(0, unique)
            compact = false
            compactBlob = ByteArray(0)
            compactOffsets = IntArray(0)
        }
        // Frequencies persist across word reloads (legacy contract), but the
        // parallel array is index-aligned — remap onto the new word order.
        if (previousFreqs.isNotEmpty()) {
            val remapped = IntArray(wordCount)
            for (i in previousWords.indices) {
                if (i >= previousFreqs.size) break
                val freq = previousFreqs[i]
                if (freq == 0) continue
                val index = sortedIndexOf(previousWords[i])
                if (index >= 0) {
                    if (freq > remapped[index]) remapped[index] = freq
                } else {
                    val existing = frequencyOverflow[previousWords[i]]
                    if (existing == null || freq > existing) frequencyOverflow[previousWords[i]] = freq
                }
            }
            frequenciesByIndex = remapped
        }
        loaded = true
    }

    /**
     * S104 char->byte table. STRICTLY increasing in code point, so byte-wise
     * unsigned comparison of encoded words equals the String sort order used
     * by [loadWords]. Bengali block U+0980–U+09FF plus the 11 characters the
     * compiled word list actually contains outside it (spaces, hyphen,
     * punctuation, দাঁড়ি, ZWNJ/ZWJ, dashes/quotes). Returns -1 for anything
     * else — which routes the whole load to legacy string storage.
     */
    private fun encodeChar(ch: Char): Int = when {
        ch.code in 0x0980..0x09FF -> 64 + (ch.code - 0x0980)
        ch == ' ' -> 1
        ch == ',' -> 2
        ch == '-' -> 3
        ch == '.' -> 4
        ch == ':' -> 5
        ch.code == 0x0964 -> 6 // দাঁড়ি
        ch.code == 0x200C -> 192
        ch.code == 0x200D -> 193
        ch.code == 0x2013 -> 194
        ch.code == 0x2019 -> 195
        ch.code == 0x201D -> 196
        else -> -1
    }

    private fun decodeByte(b: Byte): Char {
        val v = b.toInt() and 0xFF
        return when {
            v in 64..191 -> (0x0980 + (v - 64)).toChar()
            v == 1 -> ' '
            v == 2 -> ','
            v == 3 -> '-'
            v == 4 -> '.'
            v == 5 -> ':'
            v == 6 -> '।'
            v == 192 -> '‌'
            v == 193 -> '‍'
            v == 194 -> '–'
            v == 195 -> '’'
            v == 196 -> '”'
            else -> '�'
        }
    }

    /** Decode the word at sorted [index] (compact mode). */
    private fun decodeWordAt(index: Int): String {
        val from = compactOffsets[index]
        val until = compactOffsets[index + 1]
        val chars = CharArray(until - from)
        for (i in from until until) chars[i - from] = decodeByte(compactBlob[i])
        return chars.concatToString()
    }

    /**
     * Compare the word at sorted [index] against [query] (already encoded,
     * -1 entries impossible — callers bail on unmappable queries).
     * Byte-wise unsigned comparison == String order by table construction.
     */
    private fun compareAt(index: Int, query: IntArray): Int {
        val from = compactOffsets[index]
        val until = compactOffsets[index + 1]
        val len = until - from
        val n = minOf(len, query.size)
        for (i in 0 until n) {
            val diff = (compactBlob[from + i].toInt() and 0xFF) - query[i]
            if (diff != 0) return diff
        }
        return len - query.size
    }

    /** Encode [folded] for compact search, or null when unmappable (such a
     *  word cannot be in the blob — a definitive miss). */
    private fun encodeQuery(folded: String): IntArray? {
        val out = IntArray(folded.length)
        for (i in folded.indices) {
            val v = encodeChar(folded[i])
            if (v < 0) return null
            out[i] = v
        }
        return out
    }

    /**
     * Check if a Bengali word exists in the dictionary.
     *
     * @param word The Bengali word to validate
     * @return true if the word is in the dictionary
     */
    fun isValid(word: String): Boolean =
        sortedIndexOf(ReverseTransliterator.foldNukta(word)) >= 0

    /** Binary search over the sorted words; -1 when absent. [folded] must be nukta-folded. */
    private fun sortedIndexOf(folded: String): Int {
        if (compact) {
            val query = encodeQuery(folded) ?: return -1
            var lo = 0
            var hi = compactOffsets.size - 2
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val cmp = compareAt(mid, query)
                when {
                    cmp < 0 -> lo = mid + 1
                    cmp > 0 -> hi = mid - 1
                    else -> return mid
                }
            }
            return -1
        }
        var lo = 0
        var hi = sortedWords.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = sortedWords[mid].compareTo(folded)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    /**
     * Find words starting with the given Bengali prefix using binary search.
     *
     * @param prefix The Bengali prefix to search for
     * @param limit Maximum number of results to return
     * @return List of words starting with the prefix, up to limit
     */
    fun findByPrefix(prefix: String, limit: Int = 10): List<String> {
        if (wordCount == 0 || prefix.isEmpty()) return emptyList()
        @Suppress("NAME_SHADOWING") val prefix = ReverseTransliterator.foldNukta(prefix)

        if (compact) {
            // An unmappable prefix cannot start any encodable word.
            val query = encodeQuery(prefix) ?: return emptyList()
            // Binary search for first word >= prefix. A word that extends the
            // prefix compares > 0 (longer), so "strictly before prefix" is
            // exactly the legacy `word < prefix` condition.
            var lo = 0
            var hi = wordCount
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (compareAt(mid, query) < 0) lo = mid + 1 else hi = mid
            }
            val result = mutableListOf<String>()
            var i = lo
            while (i < wordCount && result.size < limit && startsWithAt(i, query)) {
                result.add(decodeWordAt(i))
                i++
            }
            return result
        }

        // Binary search for first word >= prefix
        var lo = 0
        var hi = sortedWords.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (sortedWords[mid] < prefix) lo = mid + 1 else hi = mid
        }

        val result = mutableListOf<String>()
        var i = lo
        while (i < sortedWords.size && result.size < limit && sortedWords[i].startsWith(prefix)) {
            result.add(sortedWords[i])
            i++
        }
        return result
    }

    /** True when the stored word at [index] starts with the encoded [query]. */
    private fun startsWithAt(index: Int, query: IntArray): Boolean {
        val from = compactOffsets[index]
        val until = compactOffsets[index + 1]
        if (until - from < query.size) return false
        for (i in query.indices) {
            if ((compactBlob[from + i].toInt() and 0xFF) != query[i]) return false
        }
        return true
    }

    /**
     * Get the total number of (deduplicated) words loaded.
     */
    fun getSize(): Int = wordCount

    /**
     * Check whether words have been loaded.
     */
    fun isLoaded(): Boolean = loaded

    /**
     * Get the sorted word list (used by BengaliSectionIndex for building ranges).
     * In compact mode this is a LAZY view — each access decodes one word from
     * the blob, so callers that only touch a range (section suggestions) never
     * materialize the full list.
     */
    fun getSortedWords(): List<String> =
        if (compact) {
            object : AbstractList<String>() {
                override val size: Int get() = wordCount
                override fun get(index: Int): String = decodeWordAt(index)
            }
        } else sortedWords

    /**
     * Load frequency data for ranking. Member words land in the parallel
     * IntArray; non-member keys (legacy/test sources) in the overflow map.
     * Both encodings may appear in legacy sources — max wins, matching the
     * compiler's nukta merge semantics.
     *
     * @param freqMap Map of Bengali word to frequency score
     */
    fun loadFrequencies(freqMap: Map<String, Int>) {
        frequenciesByIndex = IntArray(wordCount)
        frequencyOverflow.clear()
        for ((word, freq) in freqMap) {
            val folded = ReverseTransliterator.foldNukta(word)
            val index = sortedIndexOf(folded)
            if (index >= 0) {
                if (freq > frequenciesByIndex[index]) frequenciesByIndex[index] = freq
            } else {
                val existing = frequencyOverflow[folded]
                if (existing == null || freq > existing) frequencyOverflow[folded] = freq
            }
        }
        hasFrequencies = freqMap.isNotEmpty()
    }

    /**
     * Get the frequency score for a word.
     *
     * @param word The Bengali word
     * @return Frequency score, or 0 if not found
     */
    fun getFrequency(word: String): Int {
        val folded = ReverseTransliterator.foldNukta(word)
        val index = sortedIndexOf(folded)
        if (index >= 0 && index < frequenciesByIndex.size) return frequenciesByIndex[index]
        return frequencyOverflow[folded] ?: 0
    }

    /**
     * Check if frequency data has been loaded.
     */
    fun hasFrequencyData(): Boolean = hasFrequencies
}
