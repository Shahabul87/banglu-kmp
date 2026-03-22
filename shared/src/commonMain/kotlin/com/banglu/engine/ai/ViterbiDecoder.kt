package com.banglu.engine.ai

import kotlin.math.ln

data class WordCandidate(
    val bengali: String,
    val confidence: Double
)

data class ViterbiResult(
    val words: List<String>,
    val logProb: Double
)

class ViterbiDecoder(
    private val bigramModel: BigramModel,
    private val engineWeight: Double = 0.4,
    private val bigramWeight: Double = 0.6
) {
    fun decode(candidateSets: List<List<WordCandidate>>): ViterbiResult {
        if (candidateSets.isEmpty()) return ViterbiResult(emptyList(), 0.0)
        if (candidateSets.size == 1) {
            val best = candidateSets[0].maxByOrNull { it.confidence }
                ?: return ViterbiResult(emptyList(), 0.0)
            return ViterbiResult(listOf(best.bengali), ln(maxOf(best.confidence, 0.01)))
        }

        val n = candidateSets.size
        // viterbi[position][candidateIdx] = best log-probability reaching this state
        val viterbi = Array(n) { DoubleArray(candidateSets[it].size) { Double.NEGATIVE_INFINITY } }
        val backpointer = Array(n) { IntArray(candidateSets[it].size) { -1 } }

        // Phase 1: Initialize first word
        for ((c, candidate) in candidateSets[0].withIndex()) {
            val uniProb = bigramModel.unigramProb(candidate.bengali)
            val engineScore = ln(maxOf(candidate.confidence, 0.01))
            viterbi[0][c] = bigramWeight * ln(uniProb) + engineWeight * engineScore
        }

        // Phase 2: Recursion
        for (w in 1 until n) {
            for ((c, candidate) in candidateSets[w].withIndex()) {
                val engineScore = ln(maxOf(candidate.confidence, 0.01))
                for ((p, prevCandidate) in candidateSets[w - 1].withIndex()) {
                    val transScore = ln(bigramModel.bigramProb(prevCandidate.bengali, candidate.bengali))
                    val score = viterbi[w - 1][p] + bigramWeight * transScore + engineWeight * engineScore
                    if (score > viterbi[w][c]) {
                        viterbi[w][c] = score
                        backpointer[w][c] = p
                    }
                }
            }
        }

        // Phase 3: Backtrack
        var bestEnd = 0
        for (c in candidateSets[n - 1].indices) {
            if (viterbi[n - 1][c] > viterbi[n - 1][bestEnd]) bestEnd = c
        }

        val path = mutableListOf<String>()
        var currentIdx = bestEnd
        for (w in n - 1 downTo 0) {
            path.add(0, candidateSets[w][currentIdx].bengali)
            if (w > 0) currentIdx = backpointer[w][currentIdx]
        }

        return ViterbiResult(path, viterbi[n - 1][bestEnd])
    }
}
