package com.banglu.engine.disambiguation

import com.banglu.engine.rules.NatvaVidhan
import com.banglu.engine.rules.ShatvaVidhan

enum class SwapType {
    N_NN,    // ন↔ণ
    SH_SS,   // শ↔ষ
}

data class DisambiguationSignal(val name: String, val score: Int)

data class DisambiguationResult(
    val totalScore: Int,
    val signals: List<DisambiguationSignal>,
    val recommendation: String // "candidate", "current", or "neutral"
)

object DisambiguationScorer {

    data class FrequencyPair(val current: Int, val candidate: Int)

    private const val THRESHOLD = 8

    private val RETROFLEX_N_CONJUNCTS = listOf("ণ্ড", "ণ্ঠ", "ণ্য", "ণ্ণ", "ণ্ব", "ণ্ম")
    private val RETROFLEX_SH_CONJUNCTS = listOf("ষ্ট", "ষ্ঠ", "ষ্ণ", "ষ্ক", "ষ্প", "ষ্ম", "ষ্ফ", "ষ্ট্র")

    fun score(
        current: String,
        candidate: String,
        swapIndex: Int,
        swapType: SwapType,
        frequency: FrequencyPair
    ): DisambiguationResult {
        val signals = mutableListOf<DisambiguationSignal>()

        // Signal 1: Linguistic rule compliance
        when (swapType) {
            SwapType.N_NN -> signals.add(scoreNatvaVidhan(current, candidate, swapIndex))
            SwapType.SH_SS -> signals.add(scoreShatvaVidhan(current, candidate, swapIndex))
        }

        // Signal 2: Conjunct pattern bonus
        signals.add(scoreConjunctPattern(current, candidate, swapIndex, swapType))

        // Signal 3: Position heuristic
        signals.add(scorePosition(candidate, swapIndex, swapType))

        // Signal 4: Frequency delta
        signals.add(scoreFrequency(frequency))

        val totalScore = signals.sumOf { it.score }
        val recommendation = when {
            totalScore > THRESHOLD -> "candidate"
            totalScore < -THRESHOLD -> "current"
            else -> "neutral"
        }

        return DisambiguationResult(totalScore, signals, recommendation)
    }

    private fun scoreNatvaVidhan(current: String, candidate: String, swapIndex: Int): DisambiguationSignal {
        val precedingContext = candidate.substring(0, swapIndex)
        val triggers = NatvaVidhan.shouldBeRetroflex(precedingContext)
        return if (triggers) {
            DisambiguationSignal("natva_vidhan", 15)
        } else {
            DisambiguationSignal("natva_vidhan", -5)
        }
    }

    private fun scoreShatvaVidhan(current: String, candidate: String, swapIndex: Int): DisambiguationSignal {
        val precedingContext = candidate.substring(0, swapIndex)
        val triggers = ShatvaVidhan.shouldBeRetroflex(precedingContext)
        val hasStrongTrigger = precedingContext.contains('ঋ') ||
                precedingContext.contains('ৃ') ||
                precedingContext.contains("র্")

        return when {
            triggers && hasStrongTrigger -> DisambiguationSignal("shatva_vidhan", 15)
            triggers -> DisambiguationSignal("shatva_vidhan", 8)
            else -> DisambiguationSignal("shatva_vidhan", -5)
        }
    }

    private fun scoreConjunctPattern(
        current: String,
        candidate: String,
        swapIndex: Int,
        swapType: SwapType
    ): DisambiguationSignal {
        val conjuncts = when (swapType) {
            SwapType.N_NN -> RETROFLEX_N_CONJUNCTS
            SwapType.SH_SS -> RETROFLEX_SH_CONJUNCTS
        }
        val window = candidate.substring(
            maxOf(0, swapIndex - 1),
            minOf(candidate.length, swapIndex + 4)
        )
        val hasConjunct = conjuncts.any { window.contains(it) }

        val currentWindow = current.substring(
            maxOf(0, swapIndex - 1),
            minOf(current.length, swapIndex + 4)
        )
        val currentHasConjunct = conjuncts.any { currentWindow.contains(it) }

        return when {
            hasConjunct && !currentHasConjunct -> DisambiguationSignal("conjunct_pattern", 14)
            currentHasConjunct && !hasConjunct -> DisambiguationSignal("conjunct_pattern", -14)
            else -> DisambiguationSignal("conjunct_pattern", 0)
        }
    }

    private fun scorePosition(candidate: String, swapIndex: Int, swapType: SwapType): DisambiguationSignal {
        if (swapIndex != 0) return DisambiguationSignal("position", 0)
        return when (swapType) {
            SwapType.N_NN -> {
                if (candidate.isNotEmpty() && candidate[0] == 'ণ') {
                    DisambiguationSignal("position", -20)
                } else {
                    DisambiguationSignal("position", 0)
                }
            }
            SwapType.SH_SS -> {
                if (candidate.isNotEmpty() && candidate[0] == 'ষ') {
                    DisambiguationSignal("position", -15)
                } else {
                    DisambiguationSignal("position", 0)
                }
            }
        }
    }

    private fun scoreFrequency(frequency: FrequencyPair): DisambiguationSignal {
        val delta = frequency.candidate - frequency.current
        if (kotlin.math.abs(delta) <= 5) return DisambiguationSignal("frequency", 0)
        val score = delta.coerceIn(-20, 20)
        return DisambiguationSignal("frequency", score)
    }
}
