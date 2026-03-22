package com.banglu.engine.dictionary

import com.banglu.engine.types.OverlapResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Scores how well user input matches a candidate phonetic key using LCS.
 *
 * Used by ProgressiveNarrowingEngine to rank candidates as user types.
 */
object PhoneticOverlapScorer {

    /**
     * Score the overlap between user input and a candidate phonetic string.
     *
     * @param userInput The user's current phonetic input
     * @param candidatePhonetic The candidate dictionary phonetic key
     * @return OverlapResult with score [0.0, 1.0], inputCoverage, and isPrefix flag
     */
    fun score(userInput: String, candidatePhonetic: String): OverlapResult {
        val input = userInput.lowercase()
        val candidate = candidatePhonetic.lowercase()

        // Empty check
        if (input.isEmpty() || candidate.isEmpty()) {
            return OverlapResult(score = 0.0, inputCoverage = 0.0, isPrefix = false)
        }

        // Exact match
        if (input == candidate) {
            return OverlapResult(score = 1.0, inputCoverage = 1.0, isPrefix = false)
        }

        // candidate starts with input (input is a prefix of candidate)
        if (candidate.startsWith(input)) {
            val ratio = input.length.toDouble() / candidate.length.toDouble()
            val prefixScore = 0.60 + ratio * 0.35
            return OverlapResult(score = prefixScore, inputCoverage = 1.0, isPrefix = true)
        }

        // input starts with candidate (candidate is a prefix of input)
        if (input.startsWith(candidate)) {
            val ratio = candidate.length.toDouble() / input.length.toDouble()
            val overextendScore = ratio * 0.80
            return OverlapResult(score = overextendScore, inputCoverage = ratio, isPrefix = false)
        }

        // Partial overlap
        val lcsLen = lcsLength(input, candidate)
        val lcsRatio = (2.0 * lcsLen) / (input.length + candidate.length)

        val prefixLen = commonPrefixLength(input, candidate)
        val startBonus = min(prefixLen / 3.0, 0.15)
        val lengthPenalty = abs(input.length - candidate.length).toDouble() /
            max(input.length, candidate.length).toDouble() * 0.10

        val rawScore = lcsRatio * 0.75 + startBonus - lengthPenalty
        val clampedScore = rawScore.coerceIn(0.0, 1.0)

        val inputCoverage = prefixLen.toDouble() / input.length.toDouble()

        return OverlapResult(score = clampedScore, inputCoverage = inputCoverage, isPrefix = false)
    }

    /**
     * Compute the length of the Longest Common Subsequence using single-row DP.
     * O(n*m) time, O(min(n,m)) space.
     */
    private fun lcsLength(a: String, b: String): Int {
        // Ensure b is the shorter string for space optimization
        val (short, long) = if (a.length <= b.length) a to b else b to a
        val n = long.length
        val m = short.length

        val prev = IntArray(m + 1)

        for (i in 1..n) {
            var prevDiag = 0
            for (j in 1..m) {
                val temp = prev[j]
                if (long[i - 1] == short[j - 1]) {
                    prev[j] = prevDiag + 1
                } else {
                    prev[j] = max(prev[j], prev[j - 1])
                }
                prevDiag = temp
            }
        }

        return prev[m]
    }

    /**
     * Compute the length of the common prefix between two strings.
     */
    private fun commonPrefixLength(a: String, b: String): Int {
        val minLen = min(a.length, b.length)
        for (i in 0 until minLen) {
            if (a[i] != b[i]) return i
        }
        return minLen
    }
}
