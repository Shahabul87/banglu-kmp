package com.banglu.engine.dictionary

import com.banglu.engine.types.DictionaryStats
import com.banglu.engine.types.LookupResult
import com.banglu.engine.types.PrefixResult
import com.banglu.engine.types.SmartDictionaryEntry
import com.banglu.engine.types.TopWord
import kotlin.math.roundToInt

/**
 * SmartDictionary - Core dictionary for no-shift Bengali phonetic typing
 *
 * All-lowercase phonetic keys mapped to correct Bengali words.
 * Uses a Trie for O(k) lookup and supports exact, prefix, and fuzzy matching.
 */
class SmartDictionary {

    private var trie: PhoneticTrie = PhoneticTrie()
    var entryCount: Int = 0
        private set
    var initialized: Boolean = false
        private set

    /** LRU cache for repeated exact lookups */
    private val cache: LinkedHashMap<String, List<LookupResult>> = object :
        LinkedHashMap<String, List<LookupResult>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LookupResult>>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }
    private val bengaliToPhonetic: MutableMap<String, String> = mutableMapOf()

    /**
     * Initialize the dictionary with seed data.
     * Call this once before using the dictionary.
     */
    fun initialize() {
        if (initialized) return
        loadSeedData()
        initialized = true
    }

    private fun loadSeedData() {
        for (entry in SeedData.SEED_DICTIONARY) {
            addEntry(entry)
        }
    }

    /**
     * Add a single dictionary entry.
     */
    fun addEntry(entry: SmartDictionaryEntry) {
        for (phonetic in entry.phonetics) {
            trie.insert(phonetic, entry.bengali, entry.frequency)
        }
        // Store the first phonetic as the "canonical" spelling for reverse lookup
        if (entry.phonetics.isNotEmpty() && entry.bengali !in bengaliToPhonetic) {
            bengaliToPhonetic[entry.bengali] = entry.phonetics[0]
        }
        entryCount++
    }

    /**
     * Bulk add entries from an external source.
     */
    fun addEntries(entries: List<SmartDictionaryEntry>) {
        for (entry in entries) {
            addEntry(entry)
        }
    }

    /**
     * Add a simple phonetic -> Bengali mapping.
     */
    fun addMapping(phonetic: String, bengali: String, frequency: Int = 50) {
        trie.insert(phonetic.lowercase(), bengali, frequency)
        entryCount++
    }

    /**
     * Exact lookup: find Bengali words for an exact phonetic input.
     * Returns ranked results (best first).
     */
    fun lookup(phonetic: String): List<LookupResult> {
        ensureInitialized()

        val key = phonetic.lowercase().trim()
        if (key.isEmpty()) return emptyList()

        // Check cache
        val cached = cache[key]
        if (cached != null) return cached

        // Trie exact match
        var trieResults = trie.exactMatch(key)

        // If no exact match, try phonetic normalization
        if (trieResults.isEmpty()) {
            val normalized = normalizePhonetic(key)
            if (normalized != key) {
                trieResults = trie.exactMatch(normalized)
            }

            // Try additional common variant rewrites if still no match
            if (trieResults.isEmpty()) {
                for (variant in generatePhoneticVariants(key)) {
                    trieResults = trie.exactMatch(variant)
                    if (trieResults.isNotEmpty()) break
                }
            }
        }

        if (trieResults.isEmpty()) return emptyList()

        val results = FrequencyRanker.rankResults(
            trieResults.map { Triple(it.bengali, key, it.frequency) },
            key
        )

        // Cache the result
        cache[key] = results

        return results
    }

    /**
     * Prefix search: find Bengali words whose phonetic starts with the given prefix.
     */
    fun searchByPrefix(prefix: String, limit: Int = 10): List<PrefixResult> {
        ensureInitialized()
        return trie.prefixSearch(prefix, limit)
    }

    /**
     * Fuzzy match: find Bengali words that approximately match the phonetic input.
     */
    fun fuzzyLookup(
        phonetic: String,
        maxDistance: Int = 1,
        limit: Int = 5,
        anchorFirst: Boolean = false
    ): List<LookupResult> {
        ensureInitialized()

        val key = phonetic.lowercase().trim()
        if (key.isEmpty()) return emptyList()

        val fuzzyResults = trie.fuzzyMatch(key, maxDistance, limit, anchorFirst)

        return FrequencyRanker.rankResults(
            fuzzyResults.map { Triple(it.bengali, it.phonetic, it.frequency) },
            key
        )
    }

    /**
     * Best match: returns the single most confident result, or null.
     */
    fun bestMatch(phonetic: String): LookupResult? {
        val results = lookup(phonetic)
        return FrequencyRanker.pickBest(results)
    }

    /**
     * Check if a phonetic key has any exact match.
     */
    fun has(phonetic: String): Boolean {
        ensureInitialized()
        return trie.exactMatch(phonetic.lowercase().trim()).isNotEmpty()
    }

    /**
     * Check if any key starts with this prefix.
     */
    fun hasPrefix(prefix: String): Boolean {
        ensureInitialized()
        return trie.hasPrefix(prefix)
    }

    /**
     * Reverse lookup: get the canonical phonetic spelling for a Bengali word.
     */
    fun getPhoneticForBengali(bengali: String): String? {
        ensureInitialized()
        return bengaliToPhonetic[bengali]
    }

    /**
     * Get dictionary statistics.
     */
    fun getStats(): DictionaryStats {
        ensureInitialized()

        var longestKey = ""
        val topWords = mutableListOf<TopWord>()

        for (entry in SeedData.SEED_DICTIONARY) {
            for (phonetic in entry.phonetics) {
                if (phonetic.length > longestKey.length) {
                    longestKey = phonetic
                }
                topWords.add(TopWord(entry.bengali, phonetic, entry.frequency))
            }
        }

        topWords.sortByDescending { it.frequency }

        val totalKeys = trie.getKeyCount()
        val avgLength = if (totalKeys > 0) {
            SeedData.SEED_DICTIONARY.sumOf { e ->
                e.phonetics.sumOf { it.length }
            }.toDouble() / totalKeys
        } else {
            0.0
        }

        return DictionaryStats(
            totalEntries = entryCount,
            totalPhoneticKeys = totalKeys,
            trieNodeCount = trie.getNodeCount(),
            averageKeyLength = (avgLength * 10).roundToInt() / 10.0,
            longestKey = longestKey,
            topFrequencyWords = topWords.take(10)
        )
    }

    /** Clear all data and cache */
    fun clear() {
        trie.clear()
        cache.clear()
        bengaliToPhonetic.clear()
        entryCount = 0
        initialized = false
    }

    private fun ensureInitialized() {
        if (!initialized) {
            initialize()
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 2000

        /**
         * Normalize common phonetic equivalent spellings.
         * Collapses variations like sh/s, ou/o, ee/i, oo/u etc.
         */
        fun normalizePhonetic(key: String): String {
            var result = key
            // Vowel equivalences
            result = result.replace("ow", "ou")
            result = result.replace("ee", "i")
            result = result.replace("oo", "u")
            result = result.replace("aa", "a")
            // ou->o at word end only
            result = result.replace(Regex("ou$"), "o")
            // NOTE: v→bh normalization REMOVED — v now stays as v and maps to ভ (not ব)
            // Consonant simplifications
            result = result.replace("shh", "sh")
            result = result.replace("tth", "th")
            // Doubled consonants
            result = result.replace("kk", "k")
            result = result.replace("tt", "t")
            result = result.replace("dd", "d")
            result = result.replace("pp", "p")
            result = result.replace("ss", "s")
            result = result.replace("ll", "l")
            result = result.replace("mm", "m")
            result = result.replace("nn", "n")
            return result
        }

        /**
         * Generate phonetic variants that real users commonly type.
         */
        fun generatePhoneticVariants(key: String): List<String> {
            val variants = mutableSetOf<String>()

            // ow <-> ou <-> au
            if ("ow" in key) {
                variants.add(key.replace("ow", "ou"))
                variants.add(key.replace("ow", "au"))
            }
            if ("ou" in key && "ow" !in key) {
                variants.add(key.replace("ou", "ow"))
            }
            if ("au" in key) {
                variants.add(key.replace("au", "ou"))
            }

            // o <-> ou at word end
            if (key.endsWith("o") && !key.endsWith("ou") && !key.endsWith("ow")) {
                variants.add(key.dropLast(1) + "ou")
            }
            if (key.endsWith("ou")) {
                variants.add(key.dropLast(2) + "o")
            }

            // i <-> ee <-> ii
            if ("i" in key && "ii" !in key && "ee" !in key) {
                variants.add(key.replace("i", "ii"))
            }
            if ("ee" in key) {
                variants.add(key.replace("ee", "i"))
            }

            // c prefers ছ, while ch remains the primary spelling for চ.
            if ("ch" in key && "chh" !in key && "cch" !in key) {
                variants.add(key.replace("ch", "c"))
            }
            if (Regex("c[^ch]").containsMatchIn(key) || key.endsWith("c")) {
                variants.add(key.replace(Regex("c(?!h)"), "chh"))
                variants.add(key.replace(Regex("c(?!h)"), "ch"))
            }

            // v <-> bh
            if ("v" in key) {
                variants.add(key.replace("v", "bh"))
            }
            if ("bh" in key) {
                variants.add(key.replace("bh", "v"))
            }

            // sh <-> s
            if ("sh" in key) {
                variants.add(key.replace("sh", "s"))
            } else if ("s" in key && "sh" !in key) {
                variants.add(key.replace(Regex("s(?!h)"), "sh"))
            }

            // z and j are now separate consonants (z→য, j→জ)
            // Do NOT generate z↔j variants — they are different sounds

            // ng <-> n
            if ("ng" in key) {
                variants.add(key.replace("ng", "n"))
            }

            // w <-> o
            if ("w" in key && "ow" !in key) {
                variants.add(key.replace("w", "o"))
            }

            // ph <-> f
            if ("ph" in key) {
                variants.add(key.replace("ph", "f"))
            }
            if ("f" in key && "ph" !in key) {
                variants.add(key.replace("f", "ph"))
            }

            // Remove original key
            variants.remove(key)

            return variants.toList().take(12)
        }
    }
}
