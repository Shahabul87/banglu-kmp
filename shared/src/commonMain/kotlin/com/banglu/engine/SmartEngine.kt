package com.banglu.engine

import com.banglu.engine.ai.AIDisambiguator
import com.banglu.engine.ai.BigramModel
import com.banglu.engine.ai.ViterbiDecoder
import com.banglu.engine.ai.WordCandidate
import com.banglu.engine.dictionary.BengaliWordValidator
import com.banglu.engine.dictionary.PhoneticOverlapScorer
import com.banglu.engine.dictionary.ProgressiveNarrowingEngine
import com.banglu.engine.dictionary.SectionNarrowingEngine
import com.banglu.engine.dictionary.SeedData
import com.banglu.engine.dictionary.SmartDictionary
import com.banglu.engine.disambiguation.DisambiguationScorer
import com.banglu.engine.disambiguation.SwapType
import com.banglu.engine.platform.DictionaryLoader
import com.banglu.engine.platform.PlatformStorage
import com.banglu.engine.rules.ConjunctResolver
import com.banglu.engine.rules.ConjunctTable
import com.banglu.engine.rules.NasalResolver
import com.banglu.engine.rules.NatvaVidhan
import com.banglu.engine.rules.ShatvaVidhan
import com.banglu.engine.rules.StatisticalDefaults
import com.banglu.engine.types.Alternative
import com.banglu.engine.types.ConversionResult
import com.banglu.engine.types.PredictedWord
import com.banglu.engine.types.ResolutionSource
import com.banglu.engine.types.SmartSuggestion
import com.banglu.engine.util.ReverseTransliterator

/**
 * SmartEngine - 7-layer Bengali phonetic conversion orchestrator.
 *
 * Receives English phonetic input and converts to Bengali through:
 *   Layer 1:   Dictionary lookup (PhoneticTrie, ~4K+ seed entries)
 *   Layer 0:   Section narrowing (480K Bengali dictionary via BengaliSectionIndex)
 *   Layer 1.5: Root decomposition (stem + suffix + 480K validation)
 *              English detection (passthrough)
 *   Layer 2-4: Pattern engine (ConjunctResolver, NasalResolver, ShatvaVidhan, NatvaVidhan)
 *   Layer 5:   AIDisambiguator (swap rules: ন↔ণ, শ↔ষ, ত↔ট, etc.)
 *   Layer 5.5: Dictionary validation (character swap fixes against 480K)
 *   Layer 5.7: Conjunct removal recovery (remove hasanta to find valid words)
 *   Layer 6:   Bengali dictionary recovery (search 480K by Bengali similarity)
 */
data class SmartEngineConfig(
    val enableExternalDictionaries: Boolean = true,
    val maxSuggestions: Int = 5,
    val autoAcceptThreshold: Double = 0.90,
    val neuralConfidenceThreshold: Double = 0.70
)

class SmartEngine(private val config: SmartEngineConfig = SmartEngineConfig()) {

    // ======================== Components ========================

    val dictionary = SmartDictionary()
    private val disambiguator = AIDisambiguator()
    private val validator = BengaliWordValidator()
    private val bigramModel = BigramModel()
    private val sectionEngine = SectionNarrowingEngine()
    val narrowingEngine: ProgressiveNarrowingEngine
    private var viterbiDecoder: ViterbiDecoder? = null
    private var disambiguationMap: Map<String, String>? = null
    private var initialized = false

    // ======================== LRU Word Cache ========================

    private val wordCache = object : LinkedHashMap<String, ConversionResult>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ConversionResult>?): Boolean {
            return size > MAX_CACHE
        }
    }

    private data class InflectionalSuffix(val phonetic: String, val bengali: String)

    private val inflectionalSuffixes: List<InflectionalSuffix> = listOf(
        InflectionalSuffix("echchhen", "েচ্ছেন"),
        InflectionalSuffix("echchhe", "েচ্ছে"),
        InflectionalSuffix("echchhi", "েচ্ছি"),
        InflectionalSuffix("chchhen", "চ্ছেন"),
        InflectionalSuffix("chchhe", "চ্ছে"),
        InflectionalSuffix("chchhi", "চ্ছি"),
        InflectionalSuffix("chen", "ছেন"),
        InflectionalSuffix("chi", "ছি"),
        InflectionalSuffix("che", "ছে"),
        InflectionalSuffix("gulo", "গুলো"),
        InflectionalSuffix("guli", "গুলি"),
        InflectionalSuffix("der", "দের"),
        InflectionalSuffix("ter", "তের"),
        InflectionalSuffix("era", "েরা"),
        InflectionalSuffix("er", "ের"),
        InflectionalSuffix("ke", "কে"),
        InflectionalSuffix("te", "তে"),
        InflectionalSuffix("ra", "রা"),
        InflectionalSuffix("e", "ে"),
        InflectionalSuffix("r", "র"),
        InflectionalSuffix("y", "য়"),
    )

    companion object {
        const val MAX_CACHE = 2000

        /** Bengali digits ০-৯ */
        private const val BENGALI_DIGITS = "০১২৩৪৫৬৭৮৯"

        /** Punctuation mapping (longest match first) */
        private val PUNCTUATION = linkedMapOf(
            "..." to "...",
            ".." to "।।",
            "." to "।",
            ":" to "ঃ",
            "^" to "ঁ",
            "$" to "৳",
            "," to ",",
            "!" to "!",
            "?" to "?",
            ";" to ";",
            "(" to "(",
            ")" to ")",
            "[" to "[",
            "]" to "]",
            "{" to "{",
            "}" to "}",
            "\"" to "\"",
            "'" to "'",
            "-" to "-",
            "/" to "/",
            "\\" to "\\",
            "=" to "=",
            "+" to "+",
        )
    }

    init {
        narrowingEngine = ProgressiveNarrowingEngine(dictionary)
    }

    // ======================== INITIALIZATION ========================

    /**
     * Synchronous initialization: loads seed dictionary (~4K words).
     * Call this before any conversions. Safe to call multiple times.
     */
    fun initializeSync() {
        if (initialized) return
        dictionary.initialize()
        // Initialize disambiguator with seed Bengali words
        disambiguator.initialize(SeedData.SEED_DICTIONARY.map { it.bengali })
        initialized = true
    }

    /**
     * Async initialization: loads extended dictionaries, 480K word list,
     * frequency data, disambiguation map, bigram model, and learned words.
     *
     * @param storage Platform-specific storage for learned words (optional)
     * @param loader Platform-specific dictionary loader (optional)
     */
    suspend fun initialize(storage: PlatformStorage? = null, loader: DictionaryLoader? = null) {
        if (!initialized) initializeSync()

        // Load extended dictionary if available
        loader?.loadExtendedDictionary()?.let { entries ->
            dictionary.addEntries(entries)
        }

        // Load 480K word list
        loader?.loadFullDictionary()?.let { words ->
            validator.loadWords(words)
            sectionEngine.initialize(validator)
            disambiguator.addKnownWords(words)
        }

        // Load frequency data
        loader?.loadFrequencyMap()?.let { freqs ->
            validator.loadFrequencies(freqs)
        }

        // Load disambiguation map
        loader?.loadDisambiguationMap()?.let { map ->
            disambiguationMap = map
        }

        // Load bigram model
        loader?.loadBigramModel()?.let { data ->
            bigramModel.loadFromData(data)
            viterbiDecoder = ViterbiDecoder(bigramModel)
        }

        // Load learned words
        storage?.getLearnedWords()?.let { learned ->
            for (word in learned) {
                val primaryResult = convertWord(word.phonetic)
                val freq = if (word.bengali == primaryResult.bengali) 90 else 75
                dictionary.addMapping(word.phonetic, word.bengali, freq)
            }
        }

        clearCache()
    }

    // ======================== SINGLE WORD CONVERSION (7-LAYER PIPELINE) ========================

    /**
     * Convert a single phonetic word to Bengali through the 7-layer pipeline.
     *
     * @param input English phonetic input (e.g., "ami", "bangladesh")
     * @return ConversionResult with Bengali text, confidence, source, and alternatives
     */
    fun convertWord(input: String): ConversionResult {
        val key = input.lowercase().trim()
        if (key.isEmpty()) return ConversionResult("", 0.0, ResolutionSource.RULE)

        // Check cache — invalidate stale entries when 480K validator loads
        wordCache[key]?.let { cached ->
            val shouldInvalidate = validator.isLoaded()
                && cached.source != ResolutionSource.DICTIONARY
                && cached.source != ResolutionSource.ENGLISH_PASSTHROUGH
                && !validator.isValid(cached.bengali)
            if (!shouldInvalidate) return cached
            // Stale cache — re-run conversion with loaded validator
            wordCache.remove(key)
        }

        // Layer 1: Dictionary lookup
        convertByDictionary(key)?.let { result ->
            if (result.confidence >= config.autoAcceptThreshold) {
                cacheResult(key, result); return result
            }
            // Even if below threshold, if dictionary found something, cache and return
            // (gives priority to dictionary over patterns)
            cacheResult(key, result); return result
        }

        // Layer 1.2: Suffix-stripped dictionary lookup
        trySuffixStrippedDictionary(key)?.let { result ->
            cacheResult(key, result); return result
        }

        // Layer 0: Section narrowing (if 480K loaded)
        if (sectionEngine.isReady()) {
            convertBySection(key)?.let { result ->
                if (result.confidence >= 0.95) {
                    val validated = applyDictionaryValidation(result)
                    cacheResult(key, validated); return validated
                }
            }
        }

        // Layer 1.5: Root decomposition
        convertByRootDecomposition(key)?.let { result ->
            cacheResult(key, result); return result
        }

        // English detection: if input looks like English, pass through
        if (isLikelyEnglish(key)) {
            val result = ConversionResult(key, 1.0, ResolutionSource.ENGLISH_PASSTHROUGH)
            cacheResult(key, result); return result
        }

        // Layers 2-4: Pattern conversion
        var result = convertByPatterns(key)

        // Layer 5: AI Disambiguation (if confidence < 0.92)
        if (result.confidence < 0.92) {
            result = applyDisambiguation(result)
        }

        // Layer 5.5: Dictionary validation (if 480K loaded)
        if (validator.isLoaded()) {
            result = applyDictionaryValidation(result)
        }

        // Layer 5.7: Conjunct removal recovery (if 480K loaded and result not valid)
        if (validator.isLoaded() && !validator.isValid(result.bengali)) {
            result = applyConjunctRemovalRecovery(result)
        }

        // Layer 6: Bengali dictionary recovery (if 480K loaded and result not valid)
        if (validator.isLoaded() && !validator.isValid(result.bengali) && result.bengali.length >= 3) {
            applyBengaliRecovery(result)?.let { recovered ->
                cacheResult(key, recovered); return recovered
            }
        }

        cacheResult(key, result)
        return result
    }

    // ======================== MULTI-WORD CONVERSION ========================

    /**
     * Parse multi-word input, converting each word and preserving whitespace.
     * Optionally applies Viterbi optimization if bigram model is loaded.
     *
     * @param input Full phonetic input (may contain spaces)
     * @return Converted Bengali text with whitespace preserved
     */
    fun parse(input: String): String {
        if (input.isEmpty()) return ""

        // Tokenize preserving whitespace
        val allTokens = mutableListOf<String>()
        val wordPattern = Regex("\\S+")
        var lastEnd = 0
        for (match in wordPattern.findAll(input)) {
            if (match.range.first > lastEnd) {
                allTokens.add(input.substring(lastEnd, match.range.first)) // whitespace
            }
            allTokens.add(match.value) // word
            lastEnd = match.range.last + 1
        }
        if (lastEnd < input.length) allTokens.add(input.substring(lastEnd))

        // Track which tokens are words (not whitespace) and their original phonetic
        val wordIndices = mutableListOf<Int>()
        val originalPhonetics = mutableListOf<String>()
        for ((i, token) in allTokens.withIndex()) {
            if (token.isNotBlank()) {
                originalPhonetics.add(token)
                val result = convertWord(token)
                allTokens[i] = result.bengali
                wordIndices.add(i)
            }
        }

        // Viterbi optimization (if bigram model loaded and 2+ words)
        val decoder = viterbiDecoder
        if (decoder != null && wordIndices.size >= 2) {
            val candidateSets = wordIndices.mapIndexed { idx, tokenIdx ->
                val phonetic = originalPhonetics[idx]
                val result = convertWord(phonetic)
                val candidates = mutableListOf(WordCandidate(result.bengali, result.confidence))
                for (alt in result.alternatives.take(4)) {
                    candidates.add(WordCandidate(alt.bengali, alt.confidence))
                }
                candidates
            }
            val viterbiResult = decoder.decode(candidateSets)
            for ((i, wordIdx) in wordIndices.withIndex()) {
                if (i < viterbiResult.words.size) {
                    allTokens[wordIdx] = viterbiResult.words[i]
                }
            }
        }

        return allTokens.joinToString("")
    }

    // ======================== SUGGESTIONS ========================

    /**
     * Get ranked suggestions for the current phonetic input.
     *
     * Tiers:
     *   0: Primary conversion result
     *   1: Exact dictionary matches
     *   2: Prefix matches
     *   3: Fuzzy matches
     *   3.6: Progressive narrowing
     *   3.7: Section narrowing (if 480K loaded)
     *   4: Pattern conversion alternatives
     *
     * @param input Phonetic input
     * @param limit Maximum suggestions to return
     * @return Sorted list of SmartSuggestion
     */
    fun getSuggestions(input: String, limit: Int = 5): List<SmartSuggestion> {
        val key = input.lowercase().trim()
        if (key.isEmpty()) return emptyList()

        val maxResults = limit
        val suggestions = mutableListOf<SmartSuggestion>()
        val seen = mutableSetOf<String>()

        // ── Tier 0: Primary conversion (matching web engine exactly) ──
        val primary = convertWord(key)
        if (primary.bengali.isNotEmpty() && seen.add(primary.bengali)) {
            suggestions.add(SmartSuggestion(primary.bengali, 1.0, "primary", key, "tier0"))
        }

        // Include alternatives from convertWord (user's literal + swap variants)
        for (alt in primary.alternatives) {
            if (seen.add(alt.bengali)) {
                suggestions.add(SmartSuggestion(alt.bengali, alt.confidence, "alternative", key, "tier0"))
            }
        }

        // ো variant: when input ends with 'o', ensure both forms available
        if (key.endsWith("o") && primary.bengali.isNotEmpty()) {
            val bengali = primary.bengali
            if (bengali.endsWith("ো")) {
                val withoutOkar = bengali.dropLast(1)
                if (withoutOkar.isNotEmpty() && seen.add(withoutOkar)) {
                    suggestions.add(SmartSuggestion(withoutOkar, 0.90, "okar_variant", key, "tier0_okar"))
                }
            } else {
                val withOkar = bengali + "ো"
                if (seen.add(withOkar)) {
                    suggestions.add(SmartSuggestion(withOkar, 0.88, "okar_variant", key, "tier0_okar"))
                }
            }
        }

        // ── Bengali variant search (matching web: find related words from 480K by Bengali prefix) ──
        // This is what gives পেপারও, পেপারকে for pepar → পেপার
        if (validator.isLoaded() && primary.bengali.isNotEmpty() && primary.bengali.length >= 2) {
            val prefixLen = maxOf(2, primary.bengali.length - 1)
            val bengaliPrefix = primary.bengali.substring(0, minOf(prefixLen, primary.bengali.length))
            val bengaliVariants = validator.findByPrefix(bengaliPrefix, 10)
            for (variant in bengaliVariants) {
                if (suggestions.size >= maxResults) break
                if (seen.add(variant)) {
                    val lengthDiff = kotlin.math.abs(variant.length - primary.bengali.length)
                    val conf = maxOf(0.70 - lengthDiff * 0.08, 0.45)
                    suggestions.add(SmartSuggestion(variant, conf, "bengali_variant", "", "tier1"))
                }
            }
        }

        // ── Dictionary exact matches ──
        for (result in dictionary.lookup(key).take(maxResults)) {
            if (seen.add(result.bengali)) {
                suggestions.add(SmartSuggestion(result.bengali, result.confidence, "dictionary", result.matchedPhonetic, "tier2"))
            }
        }

        // ── Dictionary prefix matches (phonetic prefix) ──
        if (suggestions.size < maxResults) {
            for (result in dictionary.searchByPrefix(key, maxResults - suggestions.size)) {
                if (seen.add(result.bengali)) {
                    suggestions.add(SmartSuggestion(result.bengali, 0.60, "dictionary_prefix", result.phonetic, "tier3"))
                }
            }
        }

        // ── Fuzzy matches (edit distance 1, first-char anchored) ──
        if (suggestions.size < maxResults) {
            val maxFuzzy = minOf(3, maxResults - suggestions.size)
            for (result in dictionary.fuzzyLookup(key, 1, maxFuzzy, anchorFirst = true)) {
                if (seen.add(result.bengali)) {
                    suggestions.add(SmartSuggestion(result.bengali, result.confidence * 0.65, "dictionary_fuzzy", result.matchedPhonetic, "tier4"))
                }
            }
        }

        // ── Progressive narrowing ──
        if (suggestions.size < maxResults && key.length >= 2) {
            for (result in narrowingEngine.getSuggestions(key, maxResults - suggestions.size)) {
                if (seen.add(result.bengali)) {
                    suggestions.add(SmartSuggestion(result.bengali, result.confidence, "narrowing", result.phonetic, "tier5"))
                }
            }
        }

        // ── Section narrowing (if 480K loaded) ──
        if (suggestions.size < maxResults && sectionEngine.isReady()) {
            for (result in sectionEngine.getSectionSuggestions(key, maxResults)) {
                if (seen.add(result.bengali)) {
                    suggestions.add(SmartSuggestion(result.bengali, result.confidence, "section", "", "tier6"))
                }
            }
        }

        // Global filter: remove hyphenated garbage from 480K dictionary
        return suggestions
            .filter { !it.bengali.contains("-") }
            .sortedByDescending { it.confidence }
            .take(limit)
    }

    // ======================== PRIVATE PIPELINE METHODS ========================

    /**
     * Layer 1: Dictionary lookup via PhoneticTrie.
     */
    private fun convertByDictionary(key: String): ConversionResult? {
        var results = dictionary.lookup(key)
        if (results.isEmpty()) return null

        // Step 1: HARD FILTER for start-of-word consonant violations
        // 's' (not 'sh') → filter out শ-starting results (শ needs 'sh')
        if (key.startsWith("s") && !key.startsWith("sh")) {
            val filtered = results.filter { !it.bengali.startsWith("শ") }
            if (filtered.isNotEmpty()) results = filtered else return null
        }
        // 'sh' → filter out স-starting results (স needs plain 's')
        if (key.startsWith("sh")) {
            val filtered = results.filter { !it.bengali.startsWith("স") }
            if (filtered.isNotEmpty()) results = filtered else return null
        }
        // 'z' → filter out জ-starting results (জ needs 'j')
        if (key.startsWith("z")) {
            val filtered = results.filter { !it.bengali.startsWith("জ") }
            if (filtered.isNotEmpty()) results = filtered else return null
        }
        // 'j' (not 'jh') → PREFER জ over য, but don't filter out য entirely
        // জ and য are both valid for 'j' in Bengali (phonetically similar)
        if (key.startsWith("j") && !key.startsWith("jh")) {
            val jResults = results.filter { it.bengali.startsWith("জ") }
            if (jResults.isNotEmpty()) results = jResults  // Prefer জ if available
            // If only য results, keep them (don't filter to null)
        }
        // t/d: NO hard filter — soft sort below (web engine parity)
        // Web SmartEngine.ts uses soft sort to keep ট/ড but rank ত/দ first
        // 'v' → filter out ব-starting results (ব needs 'b', 'v' maps to ভ)
        if (key.startsWith("v")) {
            val filtered = results.filter { !it.bengali.startsWith("ব") }
            if (filtered.isNotEmpty()) results = filtered else return null
        }

        // Re-rank by real wordfreq frequency — but ONLY when:
        // 1. Top result has low dict frequency (< 85) — not a curated seed entry
        // 2. Wordfreq strongly disagrees (gap > 5)
        var ranked = if (results.size > 1 && validator.isLoaded() && validator.hasFrequencyData()) {
            val topDictFreq = results[0].frequency
            if (topDictFreq < 85) {
                val topWf = validator.getFrequency(results[0].bengali)
                val secondWf = validator.getFrequency(results[1].bengali)
                if (secondWf > topWf + 5) {
                    results.sortedByDescending { validator.getFrequency(it.bengali) }
                } else results
            } else results
        } else results

        // Step 2a: SOFT SORT for t/d start-position — prefer dental (ত/দ) over retroflex (ট/ড)
        // Web parity: SmartEngine.ts uses soft sort, not hard filter, so টাকা stays reachable
        if (key.startsWith("t") && !key.startsWith("th")) {
            ranked = ranked.sortedWith(Comparator { a, b ->
                val aIsRetro = if (a.bengali.startsWith("ট")) 1 else 0
                val bIsRetro = if (b.bengali.startsWith("ট")) 1 else 0
                if (aIsRetro != bIsRetro) aIsRetro - bIsRetro
                else (b.confidence * 100).toInt() - (a.confidence * 100).toInt()
            })
        }
        if (key.startsWith("d") && !key.startsWith("dh")) {
            ranked = ranked.sortedWith(Comparator { a, b ->
                val aIsRetro = if (a.bengali.startsWith("ড")) 1 else 0
                val bIsRetro = if (b.bengali.startsWith("ড")) 1 else 0
                if (aIsRetro != bIsRetro) aIsRetro - bIsRetro
                else (b.confidence * 100).toInt() - (a.confidence * 100).toInt()
            })
        }

        // Step 2b: SOFT SORT for middle/end position consonant violations
        // Prefer results with fewer শ/ষ when phonetic has standalone 's' (not 'sh')
        // Prefer results with fewer ড when phonetic has standalone 'd' (not 'dh')
        // Prefer results with fewer জ when phonetic has 'z'
        if (ranked.size > 1) {
            val hasStandaloneS = Regex("s(?!h)").containsMatchIn(key)
            val hasStandaloneD = Regex("d(?!h)").containsMatchIn(key)
            val hasZ = key.contains("z")
            if (hasStandaloneS || hasStandaloneD || hasZ) {
                ranked = ranked.sortedWith(Comparator { a, b ->
                    var vA = 0; var vB = 0
                    if (hasStandaloneS) {
                        vA += a.bengali.count { it == 'শ' || it == 'ষ' }
                        vB += b.bengali.count { it == 'শ' || it == 'ষ' }
                    }
                    if (hasStandaloneD) {
                        vA += a.bengali.count { it == 'ড' }
                        vB += b.bengali.count { it == 'ড' }
                    }
                    if (hasZ) {
                        vA += a.bengali.count { it == 'জ' }
                        vB += b.bengali.count { it == 'জ' }
                    }
                    if (vA != vB) vA - vB
                    else (b.confidence * 100).toInt() - (a.confidence * 100).toInt()
                })
            }
        }

        // When input ends with 'o', prefer the ো-version of the top result
        if (key.endsWith("o") && ranked.size > 1) {
            val topBengali = ranked[0].bengali
            val expectedOkar = topBengali + "ো"
            val okarIdx = ranked.indexOfFirst { it.bengali == expectedOkar }
            if (okarIdx > 0) {
                val okarResult = ranked[okarIdx]
                ranked = ranked.toMutableList().apply {
                    removeAt(okarIdx)
                    add(0, okarResult)
                }
            }
        }

        val best = ranked[0]
        val alternatives = ranked.drop(1).map { Alternative(it.bengali, it.confidence) }
        return ConversionResult(best.bengali, best.confidence, ResolutionSource.DICTIONARY, alternatives)
    }

    /**
     * Layer 0: Section narrowing using 480K Bengali dictionary sections.
     */
    private fun convertBySection(key: String): ConversionResult? {
        val suggestions = sectionEngine.getSectionSuggestions(key, 5)
        if (suggestions.isEmpty()) return null
        // Score by phonetic overlap, reject hyphenated garbage
        val scored = suggestions
            .filter { !it.bengali.contains("-") }  // Reject hyphenated entries (garbage from 480K)
            .map { s ->
                val overlap = PhoneticOverlapScorer.score(key, ReverseTransliterator.reverseWord(s.bengali))
                s to overlap.score
            }.filter { it.second > 0.85 }
        if (scored.isEmpty()) return null
        val best = scored.maxByOrNull { it.second }!!
        return ConversionResult(best.first.bengali, best.first.confidence, ResolutionSource.SECTION)
    }

    /**
     * Layer 1.5: Root decomposition - try splitting word into dictionary root + pattern suffix.
     */
    private fun convertByRootDecomposition(key: String): ConversionResult? {
        for (splitPoint in key.length - 1 downTo 2) {
            val root = key.substring(0, splitPoint)
            val suffix = key.substring(splitPoint)
            val rootResults = dictionary.lookup(root)
            if (rootResults.isNotEmpty()) {
                val suffixResult = convertByPatterns(suffix)
                val combined = rootResults[0].bengali + suffixResult.bengali
                // Validate compound if validator loaded
                if (validator.isLoaded() && validator.isValid(combined)) {
                    return ConversionResult(combined, 0.85, ResolutionSource.DICTIONARY)
                }
                // Even without validation, if root is confident and suffix is small
                if (rootResults[0].confidence >= 0.85 && suffix.length <= 3) {
                    return ConversionResult(combined, 0.75, ResolutionSource.RULE)
                }
            }
        }
        return null
    }

    /**
     * Layers 2-4: Pattern-based conversion using ConjunctResolver, ConjunctTable,
     * NasalResolver, NatvaVidhan, ShatvaVidhan, and basic consonant/vowel mapping.
     *
     * Uses greedy longest-match strategy:
     * 1. ConjunctResolver (locked patterns like sht→ষ্ট, str→স্ত্র)
     * 2. ConjunctTable entries (kh→খ, gh→ঘ, ch→চ, chh→ছ, etc.)
     * 3. Single consonants with context-aware rules
     * 4. Vowels (independent at start, dependent after consonants)
     */
    private fun convertByPatterns(key: String): ConversionResult {
        val result = StringBuilder()
        var i = 0
        var confidence = 0.85
        val alternatives = mutableListOf<Alternative>()

        while (i < key.length) {
            // --- Punctuation (longest match first) ---
            var punctMatched = false
            for ((phonetic, bengali) in PUNCTUATION) {
                if (key.startsWith(phonetic, i)) {
                    result.append(bengali)
                    i += phonetic.length
                    punctMatched = true
                    break
                }
            }
            if (punctMatched) continue

            // --- Digits → Bengali numerals ---
            val ch = key[i]
            if (ch in '0'..'9') {
                result.append(BENGALI_DIGITS[ch - '0'])
                i++
                continue
            }

            // Try ConjunctResolver first (highest priority, locked patterns)
            val conjunctMatch = ConjunctResolver.matchAt(key, i)
            if (conjunctMatch != null) {
                result.append(conjunctMatch.bengali)
                i += conjunctMatch.consumed
                // Check for dependent vowel after conjunct
                if (i < key.length && key[i] in "aeiou") {
                    val vowelResult = resolveVowel(key, i, false)
                    result.append(vowelResult.first)
                    i += vowelResult.second
                    confidence = minOf(confidence, vowelResult.third)
                }
                continue
            }

            // Try ConjunctTable entries (aspirated consonants, common conjuncts)
            var tableMatched = false
            for (entry in ConjunctTable.TABLE) {
                if (key.startsWith(entry.phonetic, i, ignoreCase = true)) {
                    result.append(entry.bengali)
                    i += entry.phonetic.length
                    // Handle dependent vowel after conjunct table entry
                    if (i < key.length && key[i] in "aeiou") {
                        val vowelResult = resolveVowel(key, i, false)
                        result.append(vowelResult.first)
                        i += vowelResult.second
                        confidence = minOf(confidence, vowelResult.third)
                    }
                    tableMatched = true
                    break
                }
            }
            if (tableMatched) continue

            val char = key[i]

            when {
                // Vowels
                char in "aeiou" -> {
                    val isInitial = result.isEmpty()
                    val vowelResult = resolveVowel(key, i, isInitial)
                    result.append(vowelResult.first)
                    i += vowelResult.second
                    confidence = minOf(confidence, vowelResult.third)
                }

                // Consonants
                char in "bcdfghjklmnpqrstvwxyz" -> {
                    val consonantResult = resolveConsonant(key, i, result.toString())
                    result.append(consonantResult.first)
                    i += consonantResult.second
                    confidence = minOf(confidence, consonantResult.third)

                    // Check for dependent vowel after consonant
                    if (i < key.length && key[i] in "aeiou") {
                        // Smart trailing ো: skip 'o' before 'y' at/near word end
                        // moy → ময় (not মোয়), hoy → হয় (not হোয়)
                        // The 'o' before trailing 'y' is the inherent vowel, not ো
                        val isOBeforeTrailingY = key[i] == 'o' &&
                            i + 1 < key.length && key[i + 1] == 'y' &&
                            (i + 2 >= key.length || key[i + 2] !in "aeiou")

                        if (isOBeforeTrailingY) {
                            // Skip the 'o' — let inherent vowel + য় handle it
                            i++ // consume 'o' without adding ো
                        } else {
                            val vowelResult = resolveVowel(key, i, false)
                            result.append(vowelResult.first)
                            i += vowelResult.second
                            confidence = minOf(confidence, vowelResult.third)
                        }
                    }
                }

                else -> {
                    result.append(char)
                    i++
                }
            }
        }

        return ConversionResult(result.toString(), confidence, ResolutionSource.RULE, getAlternatives(key, result.toString()))
    }

    // ======================== PATTERN ALTERNATIVES GENERATOR ========================

    internal fun getAlternatives(input: String, primary: String): List<Alternative> {
        val alternatives = mutableListOf<Alternative>()
        val seen = mutableSetOf(primary)

        fun addAlt(bengali: String, confidence: Double) {
            if (seen.add(bengali)) {
                alternatives.add(Alternative(bengali, confidence))
            }
        }

        for (alt in generateDiphthongAlternatives(primary)) addAlt(alt.bengali, alt.confidence)
        for (alt in generateInitialVowelAlternatives(primary)) addAlt(alt.bengali, alt.confidence)
        for (alt in generateAmbiguousCharAlternatives(primary)) addAlt(alt.bengali, alt.confidence)

        return alternatives.take(config.maxSuggestions - 1)
    }

    internal fun generateDiphthongAlternatives(bengali: String): List<Alternative> {
        val alts = mutableListOf<Alternative>()

        if (bengali.contains('ৈ')) {
            val split = bengali.replace('ৈ', 'ই')
            if (split != bengali) {
                val isKnown = disambiguator.isKnownWord(split)
                alts.add(Alternative(split, if (isKnown) 0.92 else 0.60))
            }
        }

        if (bengali.contains('ৌ')) {
            val split = bengali.replace('ৌ', 'উ')
            if (split != bengali) {
                val isKnown = disambiguator.isKnownWord(split)
                alts.add(Alternative(split, if (isKnown) 0.92 else 0.55))
            }
        }

        if (bengali.contains('ই') && bengali.length >= 2) {
            val idx = bengali.indexOf('ই')
            if (idx > 0) {
                val prev = bengali[idx - 1].code
                val isBengaliConsonant = (prev in 0x0995..0x09A8) || (prev in 0x09AA..0x09B9)
                if (isBengaliConsonant) {
                    val diphthong = bengali.substring(0, idx) + "ৈ" + bengali.substring(idx + 1)
                    if (disambiguator.isKnownWord(diphthong)) {
                        alts.add(Alternative(diphthong, 0.85))
                    }
                }
            }
        }

        return alts
    }

    internal fun generateInitialVowelAlternatives(bengali: String): List<Alternative> {
        val alts = mutableListOf<Alternative>()

        when {
            bengali.startsWith("অ") -> {
                val oVersion = "ও" + bengali.substring(1)
                val isKnown = disambiguator.isKnownWord(oVersion)
                alts.add(Alternative(oVersion, if (isKnown) 0.90 else 0.55))
            }
            bengali.startsWith("ও") -> {
                val aVersion = "অ" + bengali.substring(1)
                val isKnown = disambiguator.isKnownWord(aVersion)
                alts.add(Alternative(aVersion, if (isKnown) 0.90 else 0.55))
            }
            bengali.startsWith("আ") -> {
                val oVersion = "অ" + bengali.substring(1)
                if (disambiguator.isKnownWord(oVersion)) {
                    alts.add(Alternative(oVersion, 0.88))
                }
            }
        }

        return alts
    }

    internal fun generateAmbiguousCharAlternatives(primary: String): List<Alternative> {
        if (primary.length < 2) return emptyList()

        val candidates = disambiguator.generateCandidates(primary)
        val seen = mutableSetOf(primary)
        val alts = mutableListOf<Alternative>()

        for (candidate in candidates) {
            if (!seen.add(candidate)) continue
            val isKnown = disambiguator.isKnownWord(candidate)
            alts.add(Alternative(candidate, if (isKnown) 0.88 else 0.50))
        }

        return alts.sortedByDescending { it.confidence }.take(5)
    }

    /**
     * Resolve a vowel at position i in the phonetic input.
     *
     * @param key Full phonetic input
     * @param i Current position
     * @param isIndependent True if vowel is at word-start (independent form)
     * @return Triple of (Bengali vowel string, chars consumed, confidence)
     */
    private fun resolveVowel(key: String, i: Int, isIndependent: Boolean): Triple<String, Int, Double> {
        // Check for compound vowels first (longest match)
        if (i + 1 < key.length) {
            val twoChar = key.substring(i, minOf(i + 2, key.length))
            when (twoChar) {
                "ou" -> return if (isIndependent) Triple("ঔ", 2, 0.90) else Triple("ৌ", 2, 0.90)
                "oi" -> return if (isIndependent) Triple("ঐ", 2, 0.90) else Triple("ৈ", 2, 0.90)
                "oo" -> return if (isIndependent) Triple("ঊ", 2, 0.85) else Triple("ূ", 2, 0.85)
                "ee" -> return if (isIndependent) Triple("ঈ", 2, 0.85) else Triple("ী", 2, 0.85)
                "ii" -> return if (isIndependent) Triple("ঈ", 2, 0.85) else Triple("ী", 2, 0.85)
                "aa" -> return if (isIndependent) Triple("আ", 2, 0.90) else Triple("া", 2, 0.90)
            }
        }
        // Single vowels
        return when (key[i]) {
            'a' -> if (isIndependent) Triple("আ", 1, 0.85) else Triple("া", 1, 0.85)
            'i' -> if (isIndependent) Triple("ই", 1, 0.85) else Triple("ি", 1, 0.85)
            'u' -> if (isIndependent) Triple("উ", 1, 0.90) else Triple("ু", 1, 0.90)
            'e' -> if (isIndependent) Triple("এ", 1, 0.90) else Triple("ে", 1, 0.90)
            'o' -> if (isIndependent) Triple("অ", 1, 0.85) else Triple("ো", 1, 0.85)
            else -> Triple(key[i].toString(), 1, 0.50)
        }
    }

    /**
     * Resolve a consonant at position i in the phonetic input.
     * Uses context-aware rules for n (NatvaVidhan), sh (ShatvaVidhan), ng (NasalResolver).
     *
     * @param key Full phonetic input
     * @param i Current position
     * @param bengaliContext Bengali text generated so far (for context-aware rules)
     * @return Triple of (Bengali consonant string, chars consumed, confidence)
     */
    // Consonants that can take ya-phala (্য)
    private val yPhalaConsonants = mapOf(
        'b' to "ব", 'k' to "ক", 'g' to "গ", 't' to "ত", 'd' to "দ",
        'n' to "ন", 'm' to "ম", 'j' to "জ", 'p' to "প", 'l' to "ল",
        'h' to "হ", 'v' to "ভ", 's' to "স", 'r' to "র"
    )

    // Consonants that can take ra-phala (্র)
    private val rPhalaConsonants = mapOf(
        'k' to "ক", 'g' to "গ", 't' to "ত", 'd' to "দ",
        'n' to "ন", 'm' to "ম", 'p' to "প", 'b' to "ব",
        'v' to "ভ", 's' to "স", 'j' to "জ", 'l' to "ল",
        'h' to "হ"
    )

    private val consonantMap = mapOf(
        'k' to "ক", 'g' to "গ", 'c' to "চ", 'j' to "জ",
        't' to "ত", 'd' to "দ", 'p' to "প", 'b' to "ব",
        'f' to "ফ", 'm' to "ম", 'r' to "র", 'l' to "ল",
        's' to "স", 'h' to "হ", 'v' to "ভ", 'w' to "ও",
        'y' to "য়", 'z' to "য", 'q' to "ক", 'x' to "ক্স"
    )

    private fun resolveConsonant(key: String, i: Int, bengaliContext: String): Triple<String, Int, Double> {
        val ch = key[i]

        // 'ng' handling
        if (ch == 'n' && i + 1 < key.length && key[i + 1] == 'g') {
            val nextAfterNg = if (i + 2 < key.length) key[i + 2].toString() else null
            val nasal = NasalResolver.resolve(nextAfterNg)
            return Triple(nasal.toString(), 2, 0.90)
        }

        // 'sh' handling
        if (ch == 's' && i + 1 < key.length && key[i + 1] == 'h') {
            val resolution = ShatvaVidhan.resolve(bengaliContext, key, i)
            return Triple(resolution.bengali.toString(), 2, resolution.confidence)
        }

        // ৃ-কার: consonant + "ri" → consonant + ৃ (when followed by consonant or end)
        if (rPhalaConsonants.containsKey(ch) && i + 2 < key.length && key[i + 1] == 'r' && key[i + 2] == 'i') {
            val afterRI = if (i + 3 < key.length) key[i + 3] else ' '
            if (afterRI !in "aeiou") {
                // consonant + ri + consonant/end → ৃ (কৃষক, সৃষ্টি, বৃক্ষ)
                return Triple(rPhalaConsonants[ch]!! + "ৃ", 3, 0.90)
            }
        }

        // র-ফলা: consonant + "r" → consonant + ্র (প্র, ত্র, ক্র, etc.)
        if (rPhalaConsonants.containsKey(ch) && i + 1 < key.length && key[i + 1] == 'r') {
            val afterR = if (i + 2 < key.length) key[i + 2] else ' '
            if (afterR != 'r') { // Avoid 'rr'
                return Triple(rPhalaConsonants[ch]!! + "্র", 2, 0.85)
            }
        }

        // য-ফলা: consonant + "y" → consonant + ্য (ত্য, ব্য, জ্ঞ, etc.)
        if (yPhalaConsonants.containsKey(ch) && i + 1 < key.length && key[i + 1] == 'y') {
            val afterY = if (i + 2 < key.length) key[i + 2] else ' '
            if (afterY != 'y') { // Avoid 'yy'
                val conf = if (ch in "td") 0.80 else 0.85
                return Triple(yPhalaConsonants[ch]!! + "্য", 2, conf)
            }
        }

        // Reph: "r" + consonant → র্ (রেফ) when 'r' is followed by a non-vowel non-r non-h
        if (ch == 'r' && i + 1 < key.length) {
            val nextCh = key[i + 1]
            if (nextCh !in "aeiour" && nextCh != 'h') {
                return Triple("র্", 1, 0.85) // Only consume 'r', next consonant processed next iteration
            }
        }

        // 'n' (not ng) handling — NatvaVidhan
        if (ch == 'n' && (i + 1 >= key.length || key[i + 1] != 'g')) {
            val resolution = NatvaVidhan.resolve(bengaliContext)
            return Triple(resolution.bengali.toString(), 1, resolution.confidence)
        }

        val bengali = consonantMap[ch] ?: ch.toString()
        val defaultConf = StatisticalDefaults.getDefault(ch.toString())?.confidence ?: 0.80
        return Triple(bengali, 1, defaultConf)
    }

    /**
     * Layer 5: Apply AI disambiguation using character swap rules.
     */
    private fun applyDisambiguation(result: ConversionResult): ConversionResult {
        val disambiguated = disambiguator.disambiguate(result.bengali, result.confidence)
        return if (disambiguated != null) {
            result.copy(
                bengali = disambiguated.bengali,
                confidence = disambiguated.confidence,
                alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
            )
        } else result
    }

    /**
     * Layer 5.5: Apply dictionary validation with systematic character swaps.
     */
    private fun applyDictionaryValidation(result: ConversionResult): ConversionResult {
        // Try disambiguation map first (O(1))
        disambiguationMap?.get(result.bengali)?.let { correct ->
            if (validator.isValid(correct)) {
                return result.copy(
                    bengali = correct, confidence = 0.95,
                    alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
                )
            }
        }

        // Try systematic swaps
        // ন↔ণ and শ↔ষ use per-position scoring via DisambiguationScorer when both forms are valid.
        // All other swap pairs use the original simple "only swap if current is invalid" logic.
        if (!validator.isValid(result.bengali)) {
            var improved = result.bengali

            // ── ন→ণ (per-position with scorer) ──────────────────────────────────────
            for (idx in improved.indices) {
                if (improved[idx] == 'ন') {
                    val candidate = improved.substring(0, idx) + "ণ" + improved.substring(idx + 1)
                    if (candidate.contains("-")) continue
                    val candidateValid = validator.isValid(candidate)
                    val currentValid = validator.isValid(improved)
                    if (candidateValid && !currentValid) {
                        improved = candidate
                    } else if (candidateValid && currentValid) {
                        val scorerResult = DisambiguationScorer.score(
                            current = improved,
                            candidate = candidate,
                            swapIndex = idx,
                            swapType = SwapType.N_NN,
                            frequency = DisambiguationScorer.FrequencyPair(
                                current = validator.getFrequency(improved),
                                candidate = validator.getFrequency(candidate)
                            )
                        )
                        if (scorerResult.recommendation == "candidate") {
                            improved = candidate
                        }
                    }
                }
            }

            // ── ণ→ন (per-position with scorer) ──────────────────────────────────────
            for (idx in improved.indices) {
                if (improved[idx] == 'ণ') {
                    val candidate = improved.substring(0, idx) + "ন" + improved.substring(idx + 1)
                    if (candidate.contains("-")) continue
                    val candidateValid = validator.isValid(candidate)
                    val currentValid = validator.isValid(improved)
                    if (candidateValid && !currentValid) {
                        improved = candidate
                    } else if (candidateValid && currentValid) {
                        val scorerResult = DisambiguationScorer.score(
                            current = improved,
                            candidate = candidate,
                            swapIndex = idx,
                            swapType = SwapType.N_NN,
                            frequency = DisambiguationScorer.FrequencyPair(
                                current = validator.getFrequency(improved),
                                candidate = validator.getFrequency(candidate)
                            )
                        )
                        if (scorerResult.recommendation == "candidate") {
                            improved = candidate
                        }
                    }
                }
            }

            // ── শ→ষ (per-position with scorer) ──────────────────────────────────────
            for (idx in improved.indices) {
                if (improved[idx] == 'শ') {
                    val candidate = improved.substring(0, idx) + "ষ" + improved.substring(idx + 1)
                    if (candidate.contains("-")) continue
                    val candidateValid = validator.isValid(candidate)
                    val currentValid = validator.isValid(improved)
                    if (candidateValid && !currentValid) {
                        improved = candidate
                    } else if (candidateValid && currentValid) {
                        val scorerResult = DisambiguationScorer.score(
                            current = improved,
                            candidate = candidate,
                            swapIndex = idx,
                            swapType = SwapType.SH_SS,
                            frequency = DisambiguationScorer.FrequencyPair(
                                current = validator.getFrequency(improved),
                                candidate = validator.getFrequency(candidate)
                            )
                        )
                        if (scorerResult.recommendation == "candidate") {
                            improved = candidate
                        }
                    }
                }
            }

            // ── ষ→শ (per-position with scorer) ──────────────────────────────────────
            for (idx in improved.indices) {
                if (improved[idx] == 'ষ') {
                    val candidate = improved.substring(0, idx) + "শ" + improved.substring(idx + 1)
                    if (candidate.contains("-")) continue
                    val candidateValid = validator.isValid(candidate)
                    val currentValid = validator.isValid(improved)
                    if (candidateValid && !currentValid) {
                        improved = candidate
                    } else if (candidateValid && currentValid) {
                        val scorerResult = DisambiguationScorer.score(
                            current = improved,
                            candidate = candidate,
                            swapIndex = idx,
                            swapType = SwapType.SH_SS,
                            frequency = DisambiguationScorer.FrequencyPair(
                                current = validator.getFrequency(improved),
                                candidate = validator.getFrequency(candidate)
                            )
                        )
                        if (scorerResult.recommendation == "candidate") {
                            improved = candidate
                        }
                    }
                }
            }

            // Early return if any of the scored swaps fixed the word
            if (validator.isValid(improved) && improved != result.bengali) {
                return result.copy(
                    bengali = improved, confidence = 0.90,
                    alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
                )
            }

            // ── Remaining swaps (simple: only apply when current is invalid) ─────────
            val simpleSwaps = listOf(
                "স" to "ষ", "ষ" to "স",
                "ি" to "ী", "ী" to "ি",
                "ু" to "ূ", "ূ" to "ু",
                "চ" to "ছ", "ছ" to "চ",
                "ত" to "ট", "ট" to "ত",
                "দ" to "ড", "ড" to "দ"
            )

            for ((from, to) in simpleSwaps) {
                if (result.bengali.contains(from)) {
                    val candidate = result.bengali.replace(from, to)
                    // Reject hyphenated candidates (garbage from 480K dictionary)
                    if (validator.isValid(candidate) && !candidate.contains("-")) {
                        return result.copy(
                            bengali = candidate, confidence = 0.90,
                            alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
                        )
                    }
                }
            }
        }

        return result
    }

    /**
     * Layer 5.7: Conjunct removal recovery.
     * When the pattern engine produces an invalid word with hasanta (্),
     * try removing hasantas one at a time or all at once to find a valid word.
     */
    private fun applyConjunctRemovalRecovery(result: ConversionResult): ConversionResult {
        if (!validator.isLoaded()) return result
        if (validator.isValid(result.bengali)) return result  // Already valid, keep it
        if (!result.bengali.contains("্")) return result       // No hasanta, nothing to remove

        // Try removing each hasanta one at a time
        for (i in result.bengali.indices) {
            if (result.bengali[i] == '্') {
                val without = result.bengali.removeRange(i, i + 1)
                if (validator.isValid(without)) {
                    return result.copy(
                        bengali = without,
                        confidence = 0.88,
                        alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
                    )
                }
            }
        }

        // Try removing ALL hasantas at once
        val allRemoved = result.bengali.replace("্", "")
        if (validator.isValid(allRemoved)) {
            return result.copy(
                bengali = allRemoved,
                confidence = 0.85,
                alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
            )
        }

        return result
    }

    /**
     * Layer 6: Bengali dictionary recovery.
     * Searches 480K dictionary by progressively shorter Bengali prefixes,
     * scores candidates by Bengali character similarity (LCS with normalization).
     *
     * For longer inputs, similarity matters MORE and frequency matters LESS
     * (user typed enough characters to disambiguate — trust the input).
     */
    private fun applyBengaliRecovery(result: ConversionResult): ConversionResult? {
        val bengali = result.bengali

        // Collect candidates from multiple prefix lengths for broad coverage.
        // Also try vowel-swapped prefixes (ি↔ী, ু↔ূ) to find words like
        // নীলপরি when pattern gives নিলপৃ (short ি vs long ী)
        val allCandidates = mutableSetOf<String>()
        for (prefixLen in bengali.length downTo 2) {
            val prefix = bengali.substring(0, prefixLen)

            // Search with original prefix — reject hyphenated garbage
            val candidates = validator.findByPrefix(prefix, 30)
            for (c in candidates) {
                if (c != bengali && !c.contains("-")) allCandidates.add(c)
            }

            // Also search with vowel-swapped prefix (ি↔ী, ু↔ূ)
            val swapped = prefix
                .replace('ি', '\u0001').replace('ী', 'ি').replace('\u0001', 'ী')
                .replace('ু', '\u0002').replace('ূ', 'ু').replace('\u0002', 'ূ')
            if (swapped != prefix) {
                val swapCandidates = validator.findByPrefix(swapped, 20)
                for (c in swapCandidates) {
                    if (c != bengali) allCandidates.add(c)
                }
            }

            if (allCandidates.size >= 50) break
        }

        if (allCandidates.isEmpty()) return null

        val originalLen = bengali.length
        // Adaptive weighting: longer input = trust similarity more
        val freqWeight = when {
            originalLen >= 6 -> 0.05
            originalLen >= 4 -> 0.15
            else -> 0.25
        }
        val simWeight = 1.0 - freqWeight

        data class ScoredCandidate(
            val word: String,
            val similarity: Double,
            val frequency: Int,
            val combinedScore: Double
        )

        val scored = allCandidates.map { candidate ->
            val sim = bengaliSimilarity(bengali, candidate)
            val freq = validator.getFrequency(candidate)
            val combinedScore = sim * simWeight + (freq / 100.0) * freqWeight
            ScoredCandidate(candidate, sim, freq, combinedScore)
        }.sortedByDescending { it.combinedScore }

        // Only accept if the top candidate has reasonable similarity (>0.70) and length is close
        if (scored.isNotEmpty() && scored[0].similarity > 0.70 && kotlin.math.abs(scored[0].word.length - bengali.length) <= 3) {
            val best = scored[0]
            return ConversionResult(
                best.word, 0.85, ResolutionSource.DICTIONARY,
                listOf(Alternative(result.bengali, result.confidence)) +
                        scored.drop(1).take(4)
                            .filter { it.similarity > 0.40 }
                            .map { Alternative(it.word, it.combinedScore) }
            )
        }
        return null
    }

    /**
     * Bengali similarity using normalized comparison.
     *
     * Bengali characters like য়া vs আ, ড়া vs রা are different code points
     * but sound very similar. We normalize before LCS comparison:
     *   - Strip nukta (়): য় → য, ড় → ড
     *   - Replace standalone আ with া (same 'a' sound)
     *   - ী → ি, ূ → ু (similar sounds)
     *   - ঙ → ং, ণ → ন, ষ → শ (similar sounds)
     *
     * Returns combined score: primarily input coverage, with small candidate coverage factor.
     */
    private fun bengaliSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val normA = normalizeBengali(a)
        val normB = normalizeBengali(b)

        if (normA == normB) return 1.0

        val m = normA.length
        val n = normB.length
        val prev = IntArray(n + 1)
        val curr = IntArray(n + 1)
        for (i in 1..m) {
            for (j in 1..n) {
                curr[j] = if (normA[i - 1] == normB[j - 1]) prev[j - 1] + 1
                else maxOf(prev[j], curr[j - 1])
            }
            prev.indices.forEach { prev[it] = curr[it] }
            curr.fill(0)
        }
        val lcsLen = prev[n]

        // Input coverage (how much of user's input is explained by candidate)
        val inputCoverage = lcsLen.toDouble() / m
        // Candidate coverage (to prevent matching very long words)
        val candidateCoverage = lcsLen.toDouble() / n
        // Combined: primarily input coverage, with small candidate coverage factor
        return inputCoverage * 0.8 + candidateCoverage * 0.2
    }

    /**
     * Normalize Bengali text for sound-level comparison.
     * Strips modifiers that don't change the sound significantly.
     */
    private fun normalizeBengali(text: String): String {
        val sb = StringBuilder(text.length + 4) // +4 for ৃ→রি expansion
        for (ch in text) {
            when (ch) {
                '\u09BC' -> {} // Strip nukta (়) — য় ≈ য, ড় ≈ ড
                'আ' -> sb.append('া')    // আ ≈ া (same 'a' sound)
                'ৃ' -> sb.append("রি")   // ৃ ≈ রি (ri-kar sounds same as র+ি)
                'ী' -> sb.append('ি')    // ী ≈ ি (similar sounds)
                'ূ' -> sb.append('ু')    // ূ ≈ ু (similar sounds)
                'ঙ' -> sb.append('ং')    // ঙ ≈ ং (similar sounds)
                'ণ' -> sb.append('ন')    // ণ ≈ ন (similar sounds)
                'ষ' -> sb.append('শ')    // ষ ≈ শ (similar sounds)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Detect likely English words that should pass through without conversion.
     */
    private fun isLikelyEnglish(key: String): Boolean {
        val englishWords = setOf(
            "the", "is", "are", "was", "were", "been", "have", "has",
            "had", "do", "does", "did", "will", "would", "could", "should", "may", "might",
            "can", "shall", "must", "need", "dare", "for", "and", "but", "not", "you",
            "all", "any", "few", "her", "him", "his", "how", "its", "let", "new",
            "now", "old", "our", "out", "own", "say", "she", "too", "use", "way",
            "who", "why", "yes", "yet", "day", "get", "got", "end", "off", "see"
        )
        if (key in englishWords) return true
        // Words with patterns uncommon in Bengali phonetics
        if (key.contains("th") && key.contains("e") && key.length <= 4) return true
        return false
    }

    // ======================== PUBLIC UTILITY METHODS ========================

    /**
     * Add a custom word to the dictionary.
     */
    fun addWord(phonetic: String, bengali: String, frequency: Int) {
        dictionary.addMapping(phonetic, bengali, frequency)
    }

    /**
     * Set learned words for section narrowing boosting.
     */
    fun setLearnedWords(words: Map<String, Int>) {
        sectionEngine.setLearnedWords(words)
    }

    /**
     * Get next-word predictions based on bigram model.
     *
     * @param prevBengali The previously committed Bengali word
     * @param limit Maximum predictions to return
     * @return List of predicted words with confidence scores
     */
    fun getNextWordPredictions(prevBengali: String, limit: Int = 5): List<PredictedWord> {
        if (!bigramModel.isLoaded()) return emptyList()
        return bigramModel.getTopPredictions(prevBengali, limit)
    }

    /**
     * Clear the word conversion cache.
     */
    fun clearCache() {
        wordCache.clear()
    }

    /**
     * Layer 1.2: Try suffix-stripped dictionary lookup.
     *
     * Strips inflectional suffixes (দের, ের, রা, গুলো, etc.) from the phonetic key,
     * looks up the stem in the dictionary, and reconstructs the full inflected form
     * by appending the corresponding Bengali suffix.
     *
     * Prefers longer stems and higher-frequency matches when multiple candidates match.
     */
    internal fun trySuffixStrippedDictionary(key: String): ConversionResult? {
        var bestResult: ConversionResult? = null
        var bestStemLength = 0
        var bestFrequency = 0

        for (suffix in inflectionalSuffixes) {
            if (key.length > suffix.phonetic.length && key.endsWith(suffix.phonetic)) {
                val stem = key.substring(0, key.length - suffix.phonetic.length)
                if (stem.length < 2) continue

                val candidates = mutableListOf(stem)
                if (!stem.endsWith("a") && !stem.endsWith("o")) {
                    candidates.add(stem + "a")
                    candidates.add(stem + "o")
                }

                for (candidate in candidates) {
                    var stemResults = dictionary.lookup(candidate)
                    if (stemResults.isNotEmpty()) {
                        // Enforce consonant rules on stem results
                        if (key.startsWith("z")) {
                            stemResults = stemResults.filter { !it.bengali.startsWith("জ") }
                        }
                        if (key.startsWith("j") && !key.startsWith("jh")) {
                            stemResults = stemResults.filter { !it.bengali.startsWith("য") }
                        }
                        if (key.startsWith("s") && !key.startsWith("sh")) {
                            stemResults = stemResults.filter { !it.bengali.startsWith("শ") }
                        }
                        if (key.startsWith("sh")) {
                            stemResults = stemResults.filter { !it.bengali.startsWith("স") }
                        }
                        if (stemResults.isEmpty()) continue

                        val best = stemResults[0]
                        if (stem.length > bestStemLength ||
                            (stem.length == bestStemLength && best.frequency > bestFrequency)) {
                            var bengaliStem = best.bengali
                            if (candidate != stem) {
                                bengaliStem = bengaliStem.trimEnd('া', 'ো')
                            }
                            val combined = bengaliStem + suffix.bengali
                            val isExact = best.matchedPhonetic.isEmpty() || best.matchedPhonetic == candidate
                            val isValid = validator.isLoaded() && validator.isValid(combined)
                            if (isValid || (isExact && best.confidence >= 0.85)) {
                                bestResult = ConversionResult(
                                    bengali = combined,
                                    confidence = best.confidence * if (candidate == stem) 0.95 else 0.90,
                                    source = ResolutionSource.DICTIONARY,
                                    alternatives = emptyList()
                                )
                                bestStemLength = stem.length
                                bestFrequency = best.frequency
                            }
                        }
                        break
                    }
                }
            }
        }
        return bestResult
    }

    private fun cacheResult(key: String, result: ConversionResult) {
        wordCache[key] = result
    }
}
