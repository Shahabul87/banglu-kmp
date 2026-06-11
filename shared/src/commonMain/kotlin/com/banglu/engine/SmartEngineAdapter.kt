package com.banglu.engine

import com.banglu.engine.dictionary.EnglishPronunciationVariantData
import com.banglu.engine.platform.DictionaryLoader
import com.banglu.engine.platform.PlatformStorage
import com.banglu.engine.types.ConversionResult
import com.banglu.engine.types.PredictedWord
import com.banglu.engine.types.SmartSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * SmartEngineAdapter - Public API singleton wrapping SmartEngine with learned-word persistence.
 *
 * This is the primary entry point for all phonetic conversion operations.
 * It manages the SmartEngine lifecycle, initialization, and user learning persistence.
 *
 * Usage:
 *   SmartEngineAdapter.initializeSync()          // Seed dictionary (instant)
 *   SmartEngineAdapter.initialize(storage, loader) // Full async init
 *   SmartEngineAdapter.convert("ami")             // Returns "আমি"
 */
object SmartEngineAdapter {
    private var engine: SmartEngine? = null
    private var storage: PlatformStorage? = null
    private val customPreferenceMap = mutableMapOf<String, String>()
    private val selectedPreferenceMap = mutableMapOf<String, String>()
    private var learningEnabled = true
    private var personalDictionaryEnabled = true
    private var persistenceScope: CoroutineScope? = null

    @Synchronized
    internal fun getEngine(): SmartEngine {
        val existing = engine
        if (existing != null) return existing
        val newEngine = SmartEngine()
        newEngine.initializeSync()
        engine = newEngine
        return newEngine
    }

    /**
     * Synchronous initialization: loads seed dictionary (~4K words).
     * Safe to call multiple times. Call before any conversions.
     */
    fun initializeSync() {
        getEngine()
    }

    /**
     * Full async initialization: loads extended dictionaries, 480K word list,
     * frequency data, disambiguation map, bigram model, and learned words.
     *
     * @param storage Platform-specific storage for learned words and dictionary cache
     * @param loader Platform-specific dictionary loader for large files
     */
    suspend fun initialize(storage: PlatformStorage, loader: DictionaryLoader? = null) {
        this.storage = storage
        val eng = getEngine()
        eng.initialize(storage, loader)
        val learnedWords = storage.getLearnedWords()
        customPreferenceMap.clear()
        selectedPreferenceMap.clear()
        learnedWords
            .groupBy { it.phonetic.normalizedPhonetic() }
            .forEach { (phonetic, words) ->
                val customBest = words
                    .filter { it.frequency >= CUSTOM_CONVERSION_FREQUENCY }
                    .maxWithOrNull(
                        compareBy<com.banglu.engine.types.LearnedWord> { it.frequency }
                            .thenBy { it.lastUsed }
                    )
                val learnedBest = words.maxWithOrNull(
                    compareBy<com.banglu.engine.types.LearnedWord> { it.frequency }
                        .thenBy { it.lastUsed }
                )
                if (customBest != null) {
                    customPreferenceMap[phonetic] = customBest.bengali
                } else if (learnedBest != null) {
                    selectedPreferenceMap[phonetic] = learnedBest.bengali
                }
            }
        // Clear cache so stale seed-only conversions are re-evaluated with 480K data
        eng.clearCache()
    }

    /**
     * Engine v3: attach the precompiled phonetic index store (Android: sqlite-backed).
     * Call AFTER [initialize] so dictionary frequencies and the db file are ready;
     * see [SmartEngine.setPhoneticIndex] for null-detach semantics.
     */
    fun setPhoneticIndex(store: com.banglu.engine.platform.PhoneticIndexStore?) {
        getEngine().setPhoneticIndex(store)
    }

    /**
     * Convert a single phonetic word to Bengali text.
     *
     * @param word English phonetic input (e.g., "ami")
     * @return Bengali text (e.g., "আমি")
     */
    fun convert(word: String): String = convertWord(word).bengali

    /**
     * Convert a single phonetic word with full result metadata.
     *
     * @param word English phonetic input
     * @return ConversionResult with Bengali text, confidence, source, and alternatives
     */
    fun convertWord(word: String): ConversionResult =
        applyUserPreference(word, getEngine().convertWord(word))

    /**
     * Convert with committed context available.
     *
     * The base conversion still comes from the normal engine, then the previous
     * Bengali word can promote one of the existing alternatives via the bigram
     * model. Explicit user dictionary conversions and learned choices remain the
     * final ranking layer.
     */
    fun convertWordWithContext(word: String, context: List<String>): ConversionResult {
        val lastContextWord = context.asReversed().firstOrNull { it.isNotBlank() }
        val eng = getEngine()
        val result = eng.convertWord(word)
        val contextRanked = eng.rerankWithPreviousContext(lastContextWord, result)
        return applyUserPreference(word, contextRanked)
    }

    /**
     * Convert for live IME composing text. This is deliberately more
     * conservative than convertWord() so incomplete words do not jump through
     * fuzzy/recovery dictionary candidates while the user is still typing.
     */
    fun convertForComposing(word: String): ConversionResult = getEngine().convertForComposing(word)

    fun getCompositionPreview(word: String): String = getEngine().getCompositionPreview(word)

    /**
     * Parse multi-word input, converting each word and preserving whitespace.
     *
     * @param input Full phonetic input (may contain spaces)
     * @return Converted Bengali text with whitespace preserved
     */
    fun parse(input: String): String = getEngine().parse(input)

    /**
     * Get ranked suggestions for the current phonetic input.
     *
     * @param input Phonetic input
     * @param limit Maximum suggestions to return (default 5)
     * @return Sorted list of SmartSuggestion
     */
    fun getSuggestions(input: String, limit: Int = 5): List<SmartSuggestion> =
        enforceCuratedLoanwordPrimary(
            input,
            rerankSuggestionsByPreference(input, getEngine().getSuggestions(input, limit))
        )

    /**
     * Get suggestions with committed context available.
     *
     * The normal suggestion generator remains the source of candidates. Context
     * can only promote the same result that convertWordWithContext() would
     * commit, so the suggestion strip and Space commit do not disagree.
     */
    fun getSuggestionsWithContext(
        input: String,
        context: List<String>,
        limit: Int = 5
    ): List<SmartSuggestion> {
        val suggestions = getSuggestions(input, limit)
        val contextResult = convertWordWithContext(input, context)
        if (contextResult.bengali.isBlank()) return suggestions

        val index = suggestions.indexOfFirst { it.bengali == contextResult.bengali }
        val contextSuggestion = if (index >= 0) {
            suggestions[index].copy(
                confidence = maxOf(suggestions[index].confidence, contextResult.confidence),
                source = if (suggestions[index].source == "primary") "context_primary" else suggestions[index].source
            )
        } else {
            SmartSuggestion(
                bengali = contextResult.bengali,
                confidence = contextResult.confidence,
                source = "context_primary",
                phonetic = input.normalizedPhonetic(),
                tier = "tier0_context"
            )
        }

        return listOf(contextSuggestion) + suggestions.filterIndexed { i, suggestion ->
            i != index && suggestion.bengali != contextSuggestion.bengali
        }.take((limit - 1).coerceAtLeast(0))
    }

    /**
     * Get next-word predictions based on the bigram model.
     * Returns predicted Bengali words that commonly follow the given word.
     *
     * @param prevBengali The previously committed Bengali word
     * @param limit Maximum predictions to return (default 5)
     * @return List of PredictedWord with bengali text and confidence
     */
    fun getNextWordPredictions(prevBengali: String, limit: Int = 5): List<PredictedWord> =
        getEngine().getNextWordPredictions(prevBengali, limit)

    /**
     * Record a user's word selection for learning.
     * Stores ranking preference and persists asynchronously.
     *
     * @param phonetic The phonetic input the user typed
     * @param bengali The Bengali word the user selected
     * @param learnAsWord When true (commit of a clean-transliterated OOV word such
     *        as a name: rafsan -> রাফসান), also add the word to the engine
     *        dictionary so the next conversion is dictionary-backed instead of
     *        gate-floored.
     */
    fun onWordSelected(phonetic: String, bengali: String, learnAsWord: Boolean = false) {
        rememberPreferredConversion(phonetic, bengali, baseFrequency = 94)
        if (learnAsWord) {
            val key = phonetic.normalizedPhonetic()
            val cleanBengali = bengali.trim()
            if (key.isEmpty() || cleanBengali.isBlank()) return
            // OOV learning is implicit typing learning that mutates the personal
            // dictionary, so it respects BOTH runtime settings — matching
            // rememberPreferredConversion (learningEnabled) and
            // addCustomConversion (personalDictionaryEnabled).
            if (!learningEnabled || !personalDictionaryEnabled) return
            getEngine().addWord(key, cleanBengali, 94)
            // REQUIRED after addWord: commit-gated cache entries must re-evaluate.
            getEngine().clearCache()
            persistLearnedWord(key, cleanBengali, 94)
        }
    }

    /**
     * Add a user-authored conversion such as ottadhunik -> অত্যাধুনিক.
     * This uses the same ranking path as suggestion learning, but with a
     * stronger frequency so explicit dictionary entries win immediately.
     */
    fun addCustomConversion(phonetic: String, bengali: String) {
        val key = phonetic.normalizedPhonetic()
        val cleanBengali = bengali.trim()
        if (key.isEmpty() || cleanBengali.isBlank()) return
        if (!personalDictionaryEnabled) return

        customPreferenceMap[key] = cleanBengali
        getEngine().addWord(key, cleanBengali, CUSTOM_CONVERSION_FREQUENCY)
        getEngine().clearCache()
        persistLearnedWord(key, cleanBengali, CUSTOM_CONVERSION_FREQUENCY)
    }

    fun configurePersistenceScope(scope: CoroutineScope?) {
        persistenceScope = scope
    }

    private fun persistLearnedWord(phonetic: String, bengali: String, frequency: Int) {
        val s = storage ?: return
        val scope = persistenceScope ?: return
        scope.launch {
            try {
                s.saveLearnedWord(phonetic, bengali, frequency)
            } catch (_: Exception) {
                // Persistence failure is non-critical
            }
        }
    }

    private fun rememberPreferredConversion(phonetic: String, bengali: String, baseFrequency: Int) {
        val key = phonetic.normalizedPhonetic()
        val cleanBengali = bengali.trim()
        if (key.isEmpty() || cleanBengali.isBlank()) return
        if (!learningEnabled || !personalDictionaryEnabled) return

        val primaryResult = getEngine().convertWord(key)
        val freq = if (cleanBengali == primaryResult.bengali) maxOf(baseFrequency, 96) else baseFrequency
        selectedPreferenceMap[key] = cleanBengali
        // Suggestion taps are preferences, not dictionary mutations. Explicit
        // user dictionary formulas still go through addCustomConversion().
        getEngine().clearCache()
        persistLearnedWord(key, cleanBengali, freq)
    }

    /**
     * Reset all learned words and reinitialize the engine.
     */
    suspend fun resetLearning() {
        storage?.clearLearnedWords()
        customPreferenceMap.clear()
        selectedPreferenceMap.clear()
        engine = null
    }

    /**
     * Runtime settings from Android preferences. The IME calls this whenever
     * settings are reloaded so learning can be disabled without rebuilding.
     */
    fun configureLearning(enabled: Boolean, personalDictionary: Boolean) {
        learningEnabled = enabled
        personalDictionaryEnabled = personalDictionary
    }

    /**
     * Clear the word conversion cache.
     */
    fun clearCache() {
        engine?.clearCache()
    }

    /**
     * Reset the adapter to its initial state. For testing only.
     */
    fun reset() {
        engine = null
        storage = null
        customPreferenceMap.clear()
        selectedPreferenceMap.clear()
        learningEnabled = true
        personalDictionaryEnabled = true
        persistenceScope = null
    }

    private fun applyUserPreference(phonetic: String, result: ConversionResult): ConversionResult {
        if (!personalDictionaryEnabled) return result

        val key = phonetic.normalizedPhonetic()
        val curatedPrimary = CURATED_LOANWORD_PRIMARY[key]
        if (curatedPrimary != null && result.bengali == curatedPrimary) return result

        val learnedPreferred = if (learningEnabled) selectedPreferenceMap[key] else null
        val preferred: String = customPreferenceMap[key]
            ?: learnedPreferred
            ?: return result
        if (preferred == result.bengali) return result

        val alternatives = buildList {
            add(com.banglu.engine.types.Alternative(result.bengali, minOf(result.confidence, 0.88)))
            result.alternatives
                .filter { it.bengali != preferred && it.bengali != result.bengali }
                .forEach { add(it) }
        }
        return result.copy(
            bengali = preferred,
            confidence = maxOf(result.confidence, 0.96),
            alternatives = alternatives
        )
    }

    private fun rerankSuggestionsByPreference(
        phonetic: String,
        suggestions: List<SmartSuggestion>
    ): List<SmartSuggestion> {
        if (!personalDictionaryEnabled) return suggestions

        val key = phonetic.normalizedPhonetic()
        val learnedPreferred = if (learningEnabled) selectedPreferenceMap[key] else null
        val preferred: String = customPreferenceMap[key]
            ?: learnedPreferred
            ?: return suggestions
        val index = suggestions.indexOfFirst { it.bengali == preferred }
        if (index < 0) {
            return listOf(
                SmartSuggestion(
                    bengali = preferred,
                    confidence = 1.0,
                    source = "user_preference",
                    phonetic = phonetic.normalizedPhonetic(),
                    tier = "user"
                )
            ) + suggestions.take((suggestions.size - 1).coerceAtLeast(0))
        }

        val selected = suggestions[index].copy(confidence = maxOf(suggestions[index].confidence, 1.0))
        return listOf(selected) + suggestions.take(index) + suggestions.drop(index + 1)
    }

    private fun enforceCuratedLoanwordPrimary(
        phonetic: String,
        suggestions: List<SmartSuggestion>
    ): List<SmartSuggestion> {
        val primary = CURATED_LOANWORD_PRIMARY[phonetic.normalizedPhonetic()] ?: return suggestions
        val index = suggestions.indexOfFirst { it.bengali == primary }
        val primarySuggestion = if (index >= 0) {
            suggestions[index].copy(confidence = maxOf(suggestions[index].confidence, 1.0))
        } else {
            SmartSuggestion(
                bengali = primary,
                confidence = 1.0,
                source = "dictionary_exact",
                phonetic = phonetic.normalizedPhonetic(),
                tier = "tier0_curated"
            )
        }

        return listOf(primarySuggestion) + suggestions.filterIndexed { i, _ -> i != index }
    }

    private fun String.normalizedPhonetic(): String = lowercase().trim()

    private val CURATED_LOANWORD_PRIMARY = buildMap {
        for (entry in EnglishPronunciationVariantData.ENTRIES) {
            for (phonetic in entry.phonetics) putIfAbsent(phonetic.normalizedPhonetic(), entry.bengali)
        }
        put("application", "এপ্লিকেশন")
        put("database", "ডেটাবেস")
        put("facebook", "ফেসবুক")
        put("google", "গুগল")
        put("honeymoon", "হানিমুন")
        put("whatsapp", "হোয়াটসঅ্যাপ")
        put("youtube", "ইউটিউব")
    }

    private const val CUSTOM_CONVERSION_FREQUENCY = 120
}
