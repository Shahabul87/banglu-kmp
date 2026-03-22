package com.banglu.engine.dictionary

import com.banglu.engine.types.NarrowingCandidate
import com.banglu.engine.types.SmartSuggestion
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time suggestion narrowing using PhoneticTrie prefix search,
 * TypingHabitNormalizer variant expansion, and PhoneticOverlapScorer ranking.
 *
 * As the user types more characters, candidates narrow progressively.
 * Each keystroke re-scores candidates using overlap + frequency + length ratio.
 */
class ProgressiveNarrowingEngine(private val dictionary: SmartDictionary) {

    companion object {
        const val MAX_CANDIDATES = 30
        const val MIN_INPUT_LENGTH = 2
    }

    /**
     * Get ranked suggestions for the current phonetic input.
     *
     * Pipeline:
     * 1. Prefix search in PhoneticTrie
     * 2. Expand input via TypingHabitNormalizer, search those variants too
     * 3. Score each candidate with PhoneticOverlapScorer
     * 4. Deduplicate by Bengali word (keep highest score)
     * 5. Sort by combined score, return top N
     *
     * @param input The user's current phonetic input
     * @param limit Maximum number of suggestions to return
     * @return List of SmartSuggestion sorted by relevance
     */
    fun getSuggestions(input: String, limit: Int): List<SmartSuggestion> {
        val key = input.lowercase().trim()
        if (key.length < MIN_INPUT_LENGTH) return emptyList()

        // 1. Get prefix results from dictionary trie
        val prefixResults = dictionary.searchByPrefix(key, limit * 3)

        // 2. Expand with TypingHabitNormalizer variants
        val variants = TypingHabitNormalizer.expand(key)
        val allResults = prefixResults.toMutableList()
        for (variant in variants) {
            if (variant != key) {
                allResults.addAll(dictionary.searchByPrefix(variant, limit))
            }
        }

        // 3. Score each candidate with PhoneticOverlapScorer
        val scored = allResults.map { result ->
            val overlap = PhoneticOverlapScorer.score(key, result.phonetic)
            NarrowingCandidate(
                bengali = result.bengali,
                phonetic = result.phonetic,
                frequency = result.frequency,
                overlapScore = overlap.score,
                combinedScore = computeScore(overlap.score, result.frequency, key.length, result.phonetic.length),
                source = "narrowing"
            )
        }

        // 4. Deduplicate by bengali (keep highest score)
        val deduped = scored.groupBy { it.bengali }
            .mapValues { (_, candidates) -> candidates.maxByOrNull { it.combinedScore }!! }
            .values.toList()

        // 5. Sort by combined score, return top N as SmartSuggestion
        return deduped.sortedByDescending { it.combinedScore }
            .take(limit)
            .map {
                SmartSuggestion(
                    bengali = it.bengali,
                    confidence = it.combinedScore,
                    source = "narrowing",
                    phonetic = it.phonetic,
                    tier = "narrowing"
                )
            }
    }

    /**
     * Compute a combined relevance score from overlap, frequency, and length ratio.
     *
     * @param overlapScore PhoneticOverlapScorer score [0.0, 1.0]
     * @param frequency Word frequency (0-100+)
     * @param inputLength Length of user's phonetic input
     * @param candidatePhoneticLength Length of the candidate's phonetic key
     * @return Combined score (higher is better)
     */
    fun computeScore(overlapScore: Double, frequency: Int, inputLength: Int, candidatePhoneticLength: Int): Double {
        val overlapWeight = min(0.40 + inputLength * 0.05, 0.70)
        val frequencyWeight = 1.0 - overlapWeight
        val normalizedFreq = frequency / 100.0
        val lengthRatio = min(inputLength, candidatePhoneticLength).toDouble() /
            max(inputLength, candidatePhoneticLength).toDouble()
        val lengthBonus = lengthRatio * 0.10
        return overlapScore * overlapWeight + normalizedFreq * frequencyWeight + lengthBonus
    }
}
