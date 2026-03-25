package com.banglu.engine.ai

data class DisambiguationResult(
    val bengali: String,
    val confidence: Double,
    val improved: Boolean
)

class AIDisambiguator {
    private val knownWords = mutableSetOf<String>()
    private val bigramFreq = mutableMapOf<String, Int>()
    private var totalBigrams = 0
    private var initialized = false

    companion object {
        const val MAX_CANDIDATES = 1500

        // Swap rules ordered: conjunct swaps first, then single-char
        val SWAP_RULES = listOf(
            // Conjunct swaps
            "স্ত" to "ষ্ট", "ষ্ট" to "স্ত",
            "স্থ" to "ষ্ঠ", "ষ্ঠ" to "স্থ",
            "স্ন" to "ষ্ণ", "ষ্ণ" to "স্ন",
            "ন্ত" to "ণ্ট", "ণ্ট" to "ন্ত",
            "ন্দ" to "ণ্ড", "ণ্ড" to "ন্দ",
            "ন্থ" to "ণ্ঠ", "ণ্ঠ" to "ন্থ",
            "স্ফ" to "ষ্ফ", "ষ্ফ" to "স্ফ",
            "ন্ধ" to "ণ্ঢ", "ণ্ঢ" to "ন্ধ",
            "স্ক" to "ষ্ক", "ষ্ক" to "স্ক",
            "স্প" to "ষ্প", "ষ্প" to "স্প",
            "স্ম" to "ষ্ম", "ষ্ম" to "স্ম",
            // Single-char swaps
            "ত" to "ট", "ট" to "ত",
            "থ" to "ঠ", "ঠ" to "থ",
            "দ" to "ড", "ড" to "দ",
            "ধ" to "ঢ", "ঢ" to "ধ",
            "ন" to "ণ", "ণ" to "ন",
            "স" to "শ", "শ" to "স", "স" to "ষ", "ষ" to "স", "শ" to "ষ", "ষ" to "শ",
            "চ" to "ছ", "ছ" to "চ",
            "জ" to "ঝ", "ঝ" to "জ",
            "জ" to "য", "য" to "জ",
            "র" to "ড়", "ড়" to "র",
            "য" to "য়", "য়" to "য",
            "ি" to "ী", "ী" to "ি",
            "ু" to "ূ", "ূ" to "ু",
            "ই" to "ঈ", "ঈ" to "ই",
            "উ" to "ঊ", "ঊ" to "উ",
            "ে" to "ৈ", "ৈ" to "ে",
            "ো" to "ৌ", "ৌ" to "ো",
            "অ" to "ও", "ও" to "অ",
        )
    }

    fun initialize(bengaliWords: List<String>) {
        knownWords.clear()
        knownWords.addAll(bengaliWords)
        computeBigrams(bengaliWords)
        initialized = true
    }

    fun addKnownWords(words: List<String>) {
        knownWords.addAll(words)
    }

    fun disambiguate(bengali: String, confidence: Double): DisambiguationResult? {
        if (!initialized || bengali.length < 2) return null
        if (confidence >= 0.92) return null  // Already high confidence

        val isOriginalKnown = knownWords.contains(bengali)
        val originalScore = if (isOriginalKnown) scoreCandidate(bengali) else -1.0

        val candidates = generateCandidates(bengali)
        var bestCandidate: String? = null
        var bestScore = originalScore

        for (candidate in candidates) {
            if (candidate == bengali) continue
            if (!knownWords.contains(candidate)) continue
            val score = scoreCandidate(candidate)

            if (isOriginalKnown) {
                // Both are known words - lower threshold when candidate is also a valid word
                val threshold = if (knownWords.contains(candidate)) 1.2 else 1.3
                if (score > bestScore * threshold + 10) {
                    bestScore = score
                    bestCandidate = candidate
                }
            } else {
                if (score > bestScore) {
                    bestScore = score
                    bestCandidate = candidate
                }
            }
        }

        return if (bestCandidate != null) {
            DisambiguationResult(bestCandidate, 0.95, true)
        } else null
    }

    fun isKnownWord(word: String): Boolean = word in knownWords

    fun generateCandidates(bengali: String): Set<String> {
        val candidates = mutableSetOf<String>()

        // Pass 1: Single swaps
        applySingleSwaps(bengali, candidates)
        // Early exit if any known word found
        if (candidates.any { knownWords.contains(it) }) return candidates

        // Pass 2: Double swaps
        val pass1 = candidates.toSet()
        for (c in pass1) {
            if (candidates.size >= MAX_CANDIDATES) break
            applySingleSwaps(c, candidates)
        }
        if (candidates.any { it !in pass1 && knownWords.contains(it) }) return candidates

        // Pass 3: Triple swaps (short words only)
        if (bengali.length <= 8) {
            val pass2 = candidates.toSet() - pass1
            for (c in pass2) {
                if (candidates.size >= MAX_CANDIDATES) break
                applySingleSwaps(c, candidates)
            }
        }

        return candidates
    }

    private fun applySingleSwaps(bengali: String, candidates: MutableSet<String>) {
        for ((from, to) in SWAP_RULES) {
            var idx = bengali.indexOf(from)
            while (idx != -1 && candidates.size < MAX_CANDIDATES) {
                val candidate = bengali.substring(0, idx) + to + bengali.substring(idx + from.length)
                candidates.add(candidate)
                idx = bengali.indexOf(from, idx + 1)
            }
        }
    }

    private fun scoreCandidate(candidate: String): Double {
        var score = 0.0
        if (knownWords.contains(candidate)) score += 100.0

        // Compound word detection
        for (len in 3 until candidate.length) {
            for (start in 0..candidate.length - len) {
                val sub = candidate.substring(start, start + len)
                if (knownWords.contains(sub)) score += len * 1.5
            }
        }

        // Bigram naturalness
        for (i in 1 until candidate.length) {
            val bigram = "${candidate[i - 1]}${candidate[i]}"
            val freq = bigramFreq[bigram] ?: 0
            if (totalBigrams > 0) score += (freq.toDouble() / totalBigrams) * 50.0
        }

        return score
    }

    private fun computeBigrams(words: List<String>) {
        bigramFreq.clear()
        totalBigrams = 0
        for (word in words) {
            for (i in 1 until word.length) {
                val bigram = "${word[i - 1]}${word[i]}"
                bigramFreq[bigram] = (bigramFreq[bigram] ?: 0) + 1
                totalBigrams++
            }
        }
    }
}
