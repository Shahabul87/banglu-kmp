package com.banglu.engine.ai

import com.banglu.engine.types.BigramModelData
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class BigramModelTest {
    private fun createModel(): BigramModel {
        val model = BigramModel()
        model.loadFromData(BigramModelData(
            unigrams = mapOf("আমি" to 100, "তুমি" to 80, "ভালো" to 60, "খারাপ" to 30),
            bigrams = mapOf("আমি\tভালো" to 20, "আমি\tখারাপ" to 5, "তুমি\tভালো" to 15),
            totalUnigrams = 270,
            totalBigrams = 40
        ))
        return model
    }

    @Test
    fun testIsLoaded() {
        assertTrue(createModel().isLoaded())
    }

    @Test
    fun testBigramProb() {
        val model = createModel()
        val prob = model.bigramProb("আমি", "ভালো")
        assertTrue(prob > 0.0)
        assertTrue(prob < 1.0)
    }

    @Test
    fun testUnigramProb() {
        val model = createModel()
        val prob = model.unigramProb("আমি")
        assertTrue(prob > model.unigramProb("খারাপ")) // আমি more frequent
    }

    @Test
    fun testScoreSequence() {
        val model = createModel()
        val score1 = model.scoreSequence(listOf("আমি", "ভালো"))  // likely
        val score2 = model.scoreSequence(listOf("আমি", "খারাপ")) // less likely
        assertTrue(score1 > score2) // Higher bigram count -> higher score
    }

    @Test
    fun testPredictions() {
        val model = createModel()
        val preds = model.getTopPredictions("আমি", 5)
        assertTrue(preds.isNotEmpty())
        assertEquals("ভালো", preds[0].bengali) // Highest count after আমি
    }

    @Test
    fun testUnknownWordProb() {
        val model = createModel()
        val prob = model.unigramProb("অজানা")
        assertTrue(prob > 0.0) // Laplace smoothing ensures non-zero
    }
}
