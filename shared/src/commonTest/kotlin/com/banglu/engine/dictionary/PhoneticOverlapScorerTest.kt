package com.banglu.engine.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneticOverlapScorerTest {
    @Test fun testExactMatch() {
        val r = PhoneticOverlapScorer.score("chhata", "chhata")
        assertEquals(1.0, r.score)
    }
    @Test fun testPrefixMatch() {
        val r = PhoneticOverlapScorer.score("chh", "chhata")
        assertTrue(r.score in 0.60..0.95)
        assertTrue(r.isPrefix)
        assertEquals(1.0, r.inputCoverage)
    }
    @Test fun testPartialOverlap() {
        val r = PhoneticOverlapScorer.score("chhata", "chhad")
        assertTrue(r.score in 0.30..0.90)
    }
    @Test fun testUnrelated() {
        val r = PhoneticOverlapScorer.score("xyz", "chhata")
        assertTrue(r.score < 0.30)
    }
    @Test fun testEmptyInput() {
        val r = PhoneticOverlapScorer.score("", "chhata")
        assertEquals(0.0, r.score)
    }
    @Test fun testCandidateIsPrefix() {
        val r = PhoneticOverlapScorer.score("chhata", "chh")
        assertTrue(r.score > 0.0)
        assertTrue(r.score < 1.0)
    }
}
