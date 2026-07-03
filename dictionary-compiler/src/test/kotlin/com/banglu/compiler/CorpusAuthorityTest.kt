package com.banglu.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorpusAuthorityTest {

    private val usage = mapOf(
        "তৈরি" to 5552L,   // modern majority spelling
        "তৈরী" to 562L,    // archaic minority twin
        "এবং" to 190_000L, // corpus maximum
        "খণ্ড" to 1950L,
        "খন্ড" to 140L,
        "বিরল" to 3L,      // just at the evidence threshold
        "নিচে" to 2L,      // below threshold — unevidenced
    )

    private val legacy = mapOf(
        "তৈরি" to 82, "তৈরী" to 82,   // legacy tie: the W1 failure shape
        "খণ্ড" to 70, "খন্ড" to 75,   // legacy misorder: the W2 failure shape
        "বিরল" to 40, "নিচে" to 90, "অচল" to 65,
    )

    private val refreshed = CorpusAuthority.refreshFrequencies(legacy, usage)

    @Test
    fun majorityTwinOutranksMinorityTwin() {
        assertTrue(refreshed["তৈরি"]!! > refreshed["তৈরী"]!!, "$refreshed")
        assertTrue(refreshed["খণ্ড"]!! > refreshed["খন্ড"]!!, "$refreshed")
    }

    @Test
    fun evidencedWordsLandOnTierAScale() {
        assertTrue(refreshed["তৈরি"]!! >= CorpusAuthority.EVIDENCED_FLOOR)
        assertTrue(refreshed["বিরল"]!! == CorpusAuthority.EVIDENCED_FLOOR)
        assertEquals(100, refreshed["এবং"])
    }

    @Test
    fun unevidencedWordsAreCappedBelowEveryEvidencedWord() {
        assertTrue(refreshed["নিচে"]!! <= CorpusAuthority.UNEVIDENCED_CAP)
        assertTrue(refreshed["অচল"]!! <= CorpusAuthority.UNEVIDENCED_CAP)
        assertTrue(refreshed["নিচে"]!! < refreshed["বিরল"]!!)
    }

    @Test
    fun emptyUsageKeepsLegacyUntouched() {
        assertEquals(legacy, CorpusAuthority.refreshFrequencies(legacy, emptyMap()))
    }
}
