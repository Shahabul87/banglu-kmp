package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Next-word prediction acceptance (personalization round, 2026-07).
 *
 * Sources blend in strict priority order:
 *   1. user-typed pairs (count >= 2) — personal chat register wins
 *   2. corpus bigrams, with the wiki-meta stoplist applied
 *   3. static conversational fallback
 */
class NextWordPredictionJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    // ── Corpus predictions ──────────────────────────────────────────────

    @Test
    fun commonWords_produceCorpusPredictions() {
        val predictions = engine.getNextWordPredictions("আমি", 4)
        assertTrue(predictions.isNotEmpty(), "expected corpus predictions after আমি")
        assertTrue(predictions.size <= 4)
    }

    @Test
    fun stoplist_filtersWikiMetaFollowers() {
        // ভালো -> নিবন্ধ ("good article" wiki badge) and তুমি -> অ্যান্ড
        // (title transliteration) rank top-5 in the raw corpus table but must
        // never surface as predictions.
        val bhalo = engine.getNextWordPredictions("ভালো", 5).map { it.bengali }
        assertTrue("নিবন্ধ" !in bhalo, "wiki-meta নিবন্ধ leaked into predictions: $bhalo")

        val tumi = engine.getNextWordPredictions("তুমি", 5).map { it.bengali }
        assertTrue("অ্যান্ড" !in tumi, "title junk অ্যান্ড leaked into predictions: $tumi")
    }

    // ── User bigram personalization ─────────────────────────────────────

    @Test
    fun userPair_surfacesFirstAfterTwoObservations() {
        val prev = "পরীক্ষামূলক"
        engine.recordUserBigram(prev, "চিপটেস্ট")
        engine.recordUserBigram(prev, "চিপটেস্ট")

        val predictions = engine.getNextWordPredictions(prev, 4)
        assertEquals(
            "চিপটেস্ট", predictions.firstOrNull()?.bengali,
            "user pair (count=2) must rank first, got ${predictions.map { it.bengali }}"
        )
    }

    @Test
    fun userPair_singleObservationDoesNotSurface() {
        val prev = "একবারমাত্র"
        engine.recordUserBigram(prev, "ভুলটাইপ")

        val predictions = engine.getNextWordPredictions(prev, 4).map { it.bengali }
        assertTrue("ভুলটাইপ" !in predictions, "count=1 pair must not surface (may be a typo)")
    }

    @Test
    fun userPairs_outrankCorpusForSameContext() {
        // আমার has strong corpus followers (কাছে/মনে). A repeated personal
        // pair must still take the first slot; corpus follows after.
        engine.recordUserBigram("আমার", "সোনামণি")
        engine.recordUserBigram("আমার", "সোনামণি")

        val predictions = engine.getNextWordPredictions("আমার", 4).map { it.bengali }
        assertEquals("সোনামণি", predictions.firstOrNull())
        assertTrue(predictions.size >= 2, "corpus predictions should backfill after user pair")
    }

    @Test
    fun setUserBigrams_bulkLoadPowersPredictionsWithoutCorpus() {
        // Fresh seed-only engine: no corpus bigram model loaded.
        val fresh = SmartEngine()
        fresh.initializeSync()
        fresh.setUserBigrams(
            mapOf("শুভরাত্রি" to mapOf("প্রিয়" to 3, "বন্ধু" to 2, "একবার" to 1))
        )

        val predictions = fresh.getNextWordPredictions("শুভরাত্রি", 4).map { it.bengali }
        assertEquals(listOf("প্রিয়", "বন্ধু"), predictions.take(2), "sorted by count desc")
        assertTrue("একবার" !in predictions, "count=1 pair must not surface after bulk load")
    }
}
