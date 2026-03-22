package com.banglu.engine.dictionary

import com.banglu.engine.types.SectionSuggestion
import kotlin.math.max
import kotlin.math.min

/**
 * Uses BengaliSectionIndex + PhoneticSectionMapper to narrow the 480K dictionary
 * by Bengali sections.
 *
 * Given a phonetic input, maps it to Bengali prefix sections, then retrieves
 * words from those sections. Supports ambiguous mappings (e.g., "s" -> শ/ষ/স)
 * by interleaving results from multiple sections.
 */
class SectionNarrowingEngine {

    private val index = BengaliSectionIndex()
    private var validator: BengaliWordValidator? = null
    private var ready = false
    private var learnedWords: Map<String, Int> = emptyMap()

    /**
     * Initialize the engine with a loaded BengaliWordValidator.
     * Builds the section index from the validator's sorted word list.
     *
     * @param validator A BengaliWordValidator with words already loaded
     */
    fun initialize(validator: BengaliWordValidator) {
        this.validator = validator
        index.buildIndex(validator.getSortedWords())
        ready = true
    }

    /**
     * Whether the engine is ready for queries.
     */
    fun isReady(): Boolean = ready

    /**
     * Set learned words with their frequencies for boosting.
     *
     * @param words Map of Bengali word to learned frequency count
     */
    fun setLearnedWords(words: Map<String, Int>) {
        learnedWords = words
    }

    /**
     * Get section-based suggestions for a phonetic input.
     *
     * Maps phonetic input to Bengali sections, retrieves words from those sections,
     * and ranks by confidence with learned-word boosting.
     *
     * @param phoneticInput The user's phonetic input
     * @param limit Maximum number of suggestions to return
     * @return List of SectionSuggestion sorted by confidence
     */
    fun getSectionSuggestions(phoneticInput: String, limit: Int): List<SectionSuggestion> {
        if (!ready || validator == null) return emptyList()
        val input = phoneticInput.lowercase().trim()
        if (input.isEmpty()) return emptyList()

        val mapping = PhoneticSectionMapper.mapToSections(input)
        if (mapping.bengaliPrefixes.isEmpty()) return emptyList()

        val maxWordLen = max(input.length * 2, 4)
        val results = mutableListOf<SectionSuggestion>()

        if (mapping.isAmbiguous) {
            // Interleave results from ambiguous sections
            val perSection = mapping.bengaliPrefixes.map { prefix ->
                index.getWordsInRange(prefix, validator!!.getSortedWords(), limit, maxWordLen)
            }
            var i = 0
            while (results.size < limit) {
                var added = false
                for (section in perSection) {
                    if (i < section.size && results.size < limit) {
                        val word = section[i]
                        var confidence = mapping.confidence * (1.0 - i * 0.05)
                        val learnedFreq = learnedWords[word] ?: 0
                        if (learnedFreq > 0) confidence += min(0.20, learnedFreq / 500.0)
                        results.add(
                            SectionSuggestion(
                                bengali = word,
                                confidence = confidence,
                                source = "section",
                                section = mapping.bengaliPrefixes.joinToString(",")
                            )
                        )
                        added = true
                    }
                }
                if (!added) break
                i++
            }
        } else {
            val prefix = mapping.bengaliPrefixes[0]
            val words = index.getWordsInRange(prefix, validator!!.getSortedWords(), limit, maxWordLen)
            for ((idx, word) in words.withIndex()) {
                var confidence = mapping.confidence * (1.0 - idx * 0.03)
                val learnedFreq = learnedWords[word] ?: 0
                if (learnedFreq > 0) confidence += min(0.20, learnedFreq / 500.0)
                results.add(
                    SectionSuggestion(
                        bengali = word,
                        confidence = confidence,
                        source = "section",
                        section = prefix
                    )
                )
            }
        }

        return results.take(limit)
    }
}
