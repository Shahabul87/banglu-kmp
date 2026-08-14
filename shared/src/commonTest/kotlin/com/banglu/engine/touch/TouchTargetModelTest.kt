package com.banglu.engine.touch

import kotlin.test.Test
import kotlin.test.assertEquals

/** S99: probabilistic touch targeting, pinned on the real bigram tables. */
class TouchTargetModelTest {

    // qwerty row 1 neighbors: q w e r t — 'e' sits RIGHT of 'w'.

    @Test
    fun boundaryTapAfterThResolvesToE() {
        // "th" + a tap grazing w's right edge: the language says e (the).
        val r = TouchTargetModel.resolve(
            context = "th", tapped = 'w',
            leftNeighbor = 'q', rightNeighbor = 'e',
            xFraction = 0.97f, english = true
        )
        assertEquals('e', r)
    }

    @Test
    fun centerTapIsAlwaysFinal() {
        val r = TouchTargetModel.resolve(
            context = "th", tapped = 'w',
            leftNeighbor = 'q', rightNeighbor = 'e',
            xFraction = 0.5f, english = true
        )
        assertEquals('w', r, "a center tap never flips, whatever the language says")
    }

    @Test
    fun noContextMeansNoVote() {
        val r = TouchTargetModel.resolve(
            context = "", tapped = 'w',
            leftNeighbor = 'q', rightNeighbor = 'e',
            xFraction = 0.97f, english = true
        )
        assertEquals('w', r)
    }

    @Test
    fun deepPenetrationNeedsOverwhelmingEvidence() {
        // Well inside the edge zone the threshold is orders of magnitude —
        // a mild preference must NOT flip. P(o|c) vs P(i|c) are of the same
        // order in English; at 20% penetration nothing mild flips.
        val r = TouchTargetModel.resolve(
            context = "c", tapped = 'o',
            leftNeighbor = 'i', rightNeighbor = 'p',
            xFraction = 0.23f, english = true
        )
        assertEquals('o', r)
    }

    @Test
    fun banglaRomanTableDrivesBnMode() {
        // "kor" + tap grazing w's right edge toward e: kore dominates the
        // nonexistent korw in the romanized-Bangla table.
        val r = TouchTargetModel.resolve(
            context = "kor", tapped = 'w',
            leftNeighbor = 'q', rightNeighbor = 'e',
            xFraction = 0.97f, english = false
        )
        assertEquals('e', r)
    }

    @Test
    fun plausibleAlternativesDoNotFlip() {
        // "am" + u near i: amu is REAL in Bangla roman (মুখ-class) — a mild
        // preference for i must not override the pressed key.
        val r = TouchTargetModel.resolve(
            context = "am", tapped = 'u',
            leftNeighbor = 'y', rightNeighbor = 'i',
            xFraction = 0.97f, english = false
        )
        assertEquals('u', r)
    }

    @Test
    fun caseOfTheTappedKeyIsPreserved() {
        val r = TouchTargetModel.resolve(
            context = "th", tapped = 'W',
            leftNeighbor = 'q', rightNeighbor = 'e',
            xFraction = 0.97f, english = true
        )
        assertEquals('E', r)
    }

    @Test
    fun rowEdgesNeverFlipOutward() {
        // q has no left neighbor: an extreme-left tap stays q.
        val r = TouchTargetModel.resolve(
            context = "th", tapped = 'q',
            leftNeighbor = null, rightNeighbor = 'w',
            xFraction = 0.02f, english = true
        )
        assertEquals('q', r)
    }
}
