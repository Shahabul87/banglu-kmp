package com.banglu.engine

import com.banglu.engine.types.ResolutionSource
import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S78 (tester round 2026-08-04, "boi pore" kept committing বই পরে): the
 * corpus n-gram tables are wiki-register (zero বই→পড়ে rows), so context
 * rerank could never arbitrate the পরে/পড়ে near-tie — and the user's OWN
 * repeated commits (recordUserBigram) fed next-word prediction only, never
 * the rerank. These pins cover the personal-bigram promotion: two commits
 * of (খাতা, পড়ে) make "pore" after খাতা resolve to পড়ে, marked USER_HISTORY
 * so the context-blind last-tap preference cannot undo it.
 * S100 note: the chat corpus now carries real বই→পড়ে rows, so বই is no
 * longer corpus-silent — the personal-promotion pins use খাতা (zero
 * bigram/trigram rows in db 3.9.0) to keep testing the PERSONAL layer in
 * isolation; the corpus-driven বই behavior is pinned in
 * S100ChatRegisterJvmTest instead.
 * All Bengali comparisons are nukta-folded (ড় precomposed vs combining).
 */
class S78PersonalContextJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    // The engine emits and records the folded form — record what a real
    // commit would record.
    private val poreY = ReverseTransliterator.foldNukta("পড়ে")

    @AfterTest
    fun tearDown() {
        // The full-store engine is a shared singleton — leave no personal
        // pairs behind for other tests.
        engine.setUserBigrams(emptyMap())
    }

    @Test
    fun baselineWithoutPersonalEvidenceStaysFrequencyRanked() {
        val base = engine.convertWord("pore")
        assertEquals("পরে", base.bengali, "corpus near-tie: পরে@91 over পড়ে@87")
        assertTrue(
            base.alternatives.any { fold(it.bengali) == poreY },
            "পড়ে must be an alternative: ${base.alternatives.map { it.bengali }}"
        )
        // No personal pairs: context alone has no corpus evidence for খাতা→পড়ে.
        assertEquals("পরে", engine.rerankWithContext(null, "খাতা", base).bengali)
    }

    @Test
    fun twoPersonalCommitsPromoteTheContextualWord() {
        engine.recordUserBigram("খাতা", poreY)
        assertEquals(
            "পরে",
            engine.rerankWithContext(null, "খাতা", engine.convertWord("pore")).bengali,
            "a single commit must NOT promote (min count 2)"
        )
        engine.recordUserBigram("খাতা", poreY)
        val promoted = engine.rerankWithContext(null, "খাতা", engine.convertWord("pore"))
        assertEquals(poreY, fold(promoted.bengali))
        assertEquals(ResolutionSource.USER_HISTORY, promoted.source)
        // The displaced primary stays first alternative — one tap away.
        assertEquals("পরে", promoted.alternatives.first().bengali)
    }

    @Test
    fun promotionIsPerContextNotGlobal() {
        engine.recordUserBigram("খাতা", poreY)
        engine.recordUserBigram("খাতা", poreY)
        // After a DIFFERENT previous word there is no personal evidence —
        // the frequency-ranked primary stands.
        assertEquals("পরে", engine.rerankWithContext(null, "তার", engine.convertWord("pore")).bengali)
        // And with no context at all, nothing changes.
        assertEquals("পরে", engine.convertWord("pore").bengali)
    }

    @Test
    fun primaryWithMorePersonalEvidenceIsNotDethroned() {
        engine.recordUserBigram("খাতা", poreY)
        engine.recordUserBigram("খাতা", poreY)
        repeat(3) { engine.recordUserBigram("খাতা", "পরে") }
        // পরে (primary) has 3 personal commits vs পড়ে's 2 — primary stands.
        assertEquals("পরে", engine.rerankWithContext(null, "খাতা", engine.convertWord("pore")).bengali)
    }

    @Test
    fun userBigramCountReadsWhatWasRecorded() {
        assertEquals(0, engine.userBigramCount("খাতা", poreY))
        engine.recordUserBigram("খাতা", poreY)
        assertEquals(1, engine.userBigramCount("খাতা", poreY))
    }

    @Test
    fun wikiCitationJunkIsStoplistedFromPredictions() {
        // বই→উদ্ধৃতি@931 is cite-book template markup, not language.
        val predicted = engine.getNextWordPredictions("বই", 8).map { it.bengali }
        assertTrue("উদ্ধৃতি" !in predicted, "wiki citation artifact must be stoplisted: $predicted")
    }
}
