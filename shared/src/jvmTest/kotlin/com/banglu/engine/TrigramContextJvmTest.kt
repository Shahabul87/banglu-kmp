package com.banglu.engine

import com.banglu.engine.ai.BigramModel
import com.banglu.engine.types.BigramModelData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S20 mechanics: trigram storage, backoff scoring, follower prediction, and
 * the evidence-gated two-word rerank. Synthetic model — no sqlite needed.
 */
class TrigramContextJvmTest {

    private fun model(): BigramModel {
        val m = BigramModel()
        m.loadFromData(
            BigramModelData(
                unigrams = mapOf("মোট" to 500, "মত" to 900, "টাকা" to 700, "সবার" to 300),
                bigrams = mapOf(
                    "থেকে\tমোট" to 12,
                    "থেকে\tমত" to 8,
                    "মোট\tটাকা" to 20
                ),
                totalUnigrams = 2400,
                totalBigrams = 40,
                trigrams = mapOf(
                    "সব\tথেকে\tমোট" to 9,
                    "আমার\tথেকে\tমত" to 6,
                    "থেকে\tমোট\tটাকা" to 7
                )
            )
        )
        return m
    }

    @Test
    fun trigramCountsAndFollowers() {
        val m = model()
        assertTrue(m.hasTrigrams())
        assertEquals(9, m.trigramCount("সব", "থেকে", "মোট"))
        assertEquals(0, m.trigramCount("সব", "থেকে", "মত"))
        val followers = m.getTopTrigramPredictions("সব", "থেকে", 3)
        assertEquals(listOf("মোট"), followers.map { it.bengali })
    }

    @Test
    fun contextProbPrefersObservedTriple() {
        val m = model()
        val withTriple = m.contextProb("সব", "থেকে", "মোট")
        val withoutTriple = m.contextProb("সব", "থেকে", "মত")
        assertTrue(withTriple > withoutTriple,
            "observed triple must outscore unobserved ($withTriple vs $withoutTriple)")
    }

    // ── Real-data acceptance (db 3.8.0, bnwiki trigrams) ────────────────

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun homophoneFlipsWithDiscriminatingContext() {
        val mot = engine.convertWord("mot")
        assertEquals("মত", engine.rerankWithContext("প্রথম", "বারের", mot).bengali,
            "প্রথম বারের mot must resolve to মত")
        assertEquals("মোট", engine.rerankWithContext("কেন্দ্র", "থেকে", mot).bengali,
            "কেন্দ্র থেকে mot must stay মোট")
        val hat = engine.convertWord("hat")
        assertEquals("হাট", engine.rerankWithContext("ইউনিয়নের", "প্রধান", hat).bengali)
        assertEquals("হাত", engine.rerankWithContext("তার", "ডান", hat).bengali)
    }

    @Test
    fun noEvidenceMeansNoFlip() {
        val pore = engine.convertWord("pore")
        // Common but non-discriminating context: primary must not wobble.
        assertEquals(pore.bengali, engine.rerankWithContext("করেন", "এবং", pore).bengali)
        assertEquals(pore.bengali, engine.rerankWithContext(null, null, pore).bengali)
    }
}