package com.banglu.engine

import com.banglu.engine.platform.DictionaryLoader
import com.banglu.engine.platform.PlatformStorage
import com.banglu.engine.types.ConversionResult
import com.banglu.engine.types.SmartSuggestion
import kotlinx.coroutines.GlobalScope
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

    private fun getEngine(): SmartEngine {
        if (engine == null) {
            engine = SmartEngine()
            engine!!.initializeSync()
        }
        return engine!!
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
    suspend fun initialize(storage: PlatformStorage, loader: DictionaryLoader) {
        this.storage = storage
        val eng = getEngine()
        eng.initialize(storage, loader)
        // Clear cache so stale seed-only conversions are re-evaluated with 480K data
        eng.clearCache()
    }

    /**
     * Convert a single phonetic word to Bengali text.
     *
     * @param word English phonetic input (e.g., "ami")
     * @return Bengali text (e.g., "আমি")
     */
    fun convert(word: String): String = getEngine().convertWord(word).bengali

    /**
     * Convert a single phonetic word with full result metadata.
     *
     * @param word English phonetic input
     * @return ConversionResult with Bengali text, confidence, source, and alternatives
     */
    fun convertWord(word: String): ConversionResult = getEngine().convertWord(word)

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
        getEngine().getSuggestions(input, limit)

    /**
     * Record a user's word selection for learning.
     * Adds the word to the dictionary and persists asynchronously.
     *
     * @param phonetic The phonetic input the user typed
     * @param bengali The Bengali word the user selected
     */
    fun onWordSelected(phonetic: String, bengali: String) {
        val primaryResult = getEngine().convertWord(phonetic)
        val freq = if (bengali == primaryResult.bengali) 90 else 75
        getEngine().addWord(phonetic, bengali, freq)
        // Clear cache so the new word takes effect on next lookup
        getEngine().clearCache()
        // Fire and forget persistence
        storage?.let { s ->
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            GlobalScope.launch {
                try {
                    s.saveLearnedWord(phonetic, bengali, freq)
                } catch (_: Exception) {
                    // Persistence failure is non-critical
                }
            }
        }
    }

    /**
     * Reset all learned words and reinitialize the engine.
     */
    suspend fun resetLearning() {
        storage?.clearLearnedWords()
        engine = null
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
    }
}
