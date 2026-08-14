package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S100: chat-register corpus rebuild (db 3.9.0) — the OpenSubtitles
 * conversational register now blends into frequencies (2x) and n-grams
 * (16x vs wiki 4x). These pins are the round's DELIBERATE decisions.
 */
class S100ChatRegisterJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun marleResolvesTheVerbNotBobMarley() {
        // The S79 documented artifact: wiki gave মার্লে@67 (Bob Marley
        // articles) over মারলে@65. Subtitles: মারলে 54, মার্লে 0. FLIPPED
        // deliberately — this pin is the round's headline.
        assertEquals("মারলে", fold(engine.convertWord("marle").bengali))
    }

    @Test
    fun boiPredictsReadingVerbs() {
        // Pre-S100 the corpus had ZERO বই -> পড়-family pairs (wiki gave
        // citation-template junk). The strip after বই must now offer reading.
        val predictions = engine.getNextWordPredictions("বই", 6).map { fold(it.bengali) }
        assertTrue(
            predictions.any { it.startsWith(fold("পড়")) },
            "বই must predict the পড়-family, got $predictions"
        )
    }

    @Test
    fun establishedTwinDecisionsSurvive() {
        // The blend must NOT flip previously-decided twins.
        assertEquals(fold("দেখত"), fold(engine.convertWord("dekhto").bengali), "S25 pin")
        assertEquals(fold("করেছ"), fold(engine.convertWord("korecho").bengali), "S86 frequency-law pin")
        assertEquals(fold("কাচ্চি"), fold(engine.convertWord("kacci").bengali), "invariant 6")
        assertEquals(fold("কেমন"), fold(engine.convertWord("kmon").bengali))
        assertEquals(fold("করতাম"), fold(engine.convertWord("kortam").bengali), "S84 pin")
    }
}
