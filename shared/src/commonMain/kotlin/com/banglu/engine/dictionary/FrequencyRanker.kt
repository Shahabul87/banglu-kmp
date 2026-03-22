package com.banglu.engine.dictionary

import com.banglu.engine.types.LookupResult
import com.banglu.engine.types.ResolutionSource
import kotlin.math.min

/**
 * Ranks lookup results by confidence score.
 *
 * Combines phonetic match quality with word frequency to produce
 * a final confidence score for each candidate.
 */
object FrequencyRanker {

    /**
     * Rank raw lookup results into scored LookupResult objects.
     *
     * @param results List of (bengali, phonetic, frequency) triples
     * @param inputPhonetic The user's original phonetic input
     * @return Sorted list of LookupResult by confidence descending
     */
    fun rankResults(
        results: List<Triple<String, String, Int>>,
        inputPhonetic: String
    ): List<LookupResult> {
        return results.map { (bengali, phonetic, frequency) ->
            val confidence = calculateConfidence(phonetic, inputPhonetic, frequency)
            LookupResult(
                bengali = bengali,
                matchedPhonetic = phonetic,
                frequency = frequency,
                confidence = confidence,
                source = ResolutionSource.DICTIONARY
            )
        }.sortedByDescending { it.confidence }
    }

    /**
     * Calculate a confidence score for a match.
     *
     * @param matchedPhonetic The phonetic key that was matched in the dictionary
     * @param inputPhonetic The user's original input
     * @param frequency The word's usage frequency (0-100+)
     * @return Confidence score in [0.0, 1.0]
     */
    fun calculateConfidence(matchedPhonetic: String, inputPhonetic: String, frequency: Int): Double {
        val matched = matchedPhonetic.lowercase()
        val input = inputPhonetic.lowercase()

        // Base confidence from match quality
        val baseConfidence = if (matched == input) {
            0.90
        } else {
            // Partial match: scale by how much of the input is covered
            val ratio = if (matched.isNotEmpty()) {
                input.length.toDouble() / matched.length.toDouble()
            } else {
                0.0
            }
            0.50 + (ratio * 0.30).coerceIn(0.0, 0.30)
        }

        // Frequency bonus: up to 0.10 extra
        val frequencyBonus = min(frequency / 100.0, 1.0) * 0.10

        return min(baseConfidence + frequencyBonus, 1.0)
    }

    /**
     * Pick the single best result by confidence.
     *
     * @param results List of scored lookup results
     * @return The highest-confidence result, or null if empty
     */
    fun pickBest(results: List<LookupResult>): LookupResult? {
        return results.maxByOrNull { it.confidence }
    }

    /**
     * Merge multiple result sets, deduplicating by Bengali text.
     * When duplicates exist, the one with the highest confidence is kept.
     *
     * @param resultSets Variable number of result lists to merge
     * @return Deduplicated, confidence-sorted list
     */
    fun mergeResults(vararg resultSets: List<LookupResult>): List<LookupResult> {
        val bestByBengali = mutableMapOf<String, LookupResult>()

        for (resultSet in resultSets) {
            for (result in resultSet) {
                val existing = bestByBengali[result.bengali]
                if (existing == null || result.confidence > existing.confidence) {
                    bestByBengali[result.bengali] = result
                }
            }
        }

        return bestByBengali.values.sortedByDescending { it.confidence }
    }
}
