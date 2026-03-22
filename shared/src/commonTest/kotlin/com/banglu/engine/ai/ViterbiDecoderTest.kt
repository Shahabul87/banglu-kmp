package com.banglu.engine.ai

import com.banglu.engine.types.BigramModelData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViterbiDecoderTest {
    private fun createDecoder(): ViterbiDecoder {
        val model = BigramModel()
        model.loadFromData(BigramModelData(
            unigrams = mapOf("আমি" to 100, "অমি" to 5, "ভালো" to 60, "ভেলো" to 3),
            bigrams = mapOf("আমি\tভালো" to 30, "অমি\tভালো" to 1),
            totalUnigrams = 168,
            totalBigrams = 31
        ))
        return ViterbiDecoder(model)
    }

    @Test
    fun testDecodePicksBestSequence() {
        val decoder = createDecoder()
        val result = decoder.decode(listOf(
            listOf(WordCandidate("আমি", 0.90), WordCandidate("অমি", 0.60)),
            listOf(WordCandidate("ভালো", 0.85), WordCandidate("ভেলো", 0.50))
        ))
        assertEquals(2, result.words.size)
        assertEquals("আমি", result.words[0])
        assertEquals("ভালো", result.words[1])
    }

    @Test
    fun testDecodeSingleWord() {
        val decoder = createDecoder()
        val result = decoder.decode(listOf(
            listOf(WordCandidate("আমি", 0.90), WordCandidate("অমি", 0.60))
        ))
        assertEquals(1, result.words.size)
        assertEquals("আমি", result.words[0])
    }

    @Test
    fun testDecodeEmpty() {
        val decoder = createDecoder()
        val result = decoder.decode(emptyList())
        assertTrue(result.words.isEmpty())
    }
}
