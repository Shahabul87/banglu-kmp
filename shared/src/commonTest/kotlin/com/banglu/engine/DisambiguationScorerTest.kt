package com.banglu.engine

import com.banglu.engine.disambiguation.DisambiguationScorer
import com.banglu.engine.disambiguation.SwapType
import kotlin.test.Test
import kotlin.test.assertEquals

class DisambiguationScorerTest {

    @Test
    fun testNatvaVidhanFavorsRetroflex() {
        val result = DisambiguationScorer.score(
            current = "খন্ডন",
            candidate = "খণ্ডন",
            swapIndex = 1,
            swapType = SwapType.N_NN,
            frequency = DisambiguationScorer.FrequencyPair(current = 45, candidate = 50)
        )
        assertEquals("candidate", result.recommendation)
    }

    @Test
    fun testWordStartNPenalty() {
        val result = DisambiguationScorer.score(
            current = "নদী",
            candidate = "ণদী",
            swapIndex = 0,
            swapType = SwapType.N_NN,
            frequency = DisambiguationScorer.FrequencyPair(current = 60, candidate = 10)
        )
        assertEquals("current", result.recommendation)
    }

    @Test
    fun testShatvaVidhanWithRiKar() {
        val result = DisambiguationScorer.score(
            current = "কৃশ",
            candidate = "কৃষ",
            swapIndex = 2,
            swapType = SwapType.SH_SS,
            frequency = DisambiguationScorer.FrequencyPair(current = 30, candidate = 35)
        )
        assertEquals("candidate", result.recommendation)
    }

    @Test
    fun testWordStartShPenalty() {
        val result = DisambiguationScorer.score(
            current = "শান্তি",
            candidate = "ষান্তি",
            swapIndex = 0,
            swapType = SwapType.SH_SS,
            frequency = DisambiguationScorer.FrequencyPair(current = 70, candidate = 5)
        )
        assertEquals("current", result.recommendation)
    }

    @Test
    fun testNeutralWhenSignalsCancel() {
        val result = DisambiguationScorer.score(
            current = "মন",
            candidate = "মণ",
            swapIndex = 1,
            swapType = SwapType.N_NN,
            frequency = DisambiguationScorer.FrequencyPair(current = 50, candidate = 50)
        )
        assertEquals("neutral", result.recommendation)
    }

    @Test
    fun testConjunctPatternBonus() {
        val result = DisambiguationScorer.score(
            current = "নষ্ট",
            candidate = "নশ্ট",
            swapIndex = 1,
            swapType = SwapType.SH_SS,
            frequency = DisambiguationScorer.FrequencyPair(current = 55, candidate = 20)
        )
        assertEquals("current", result.recommendation)
    }

    @Test
    fun testFrequencyDeltaInfluence() {
        val result = DisambiguationScorer.score(
            current = "বিশেষ",
            candidate = "বিষেষ",
            swapIndex = 2,
            swapType = SwapType.SH_SS,
            frequency = DisambiguationScorer.FrequencyPair(current = 80, candidate = 10)
        )
        assertEquals("current", result.recommendation)
    }
}
