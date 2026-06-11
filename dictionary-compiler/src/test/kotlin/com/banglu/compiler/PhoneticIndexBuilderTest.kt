package com.banglu.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneticIndexBuilderTest {

    @Test
    fun buildsKeysForSimpleWords() {
        val rows = PhoneticIndexBuilder.build(
            words = listOf("আমি", "তুমি"),
            frequencies = mapOf("আমি" to 100, "তুমি" to 99)
        )
        val amiKeys = rows.filter { it.bengali == "আমি" }.map { it.key }
        assertTrue("ami" in amiKeys, "expected canonical key 'ami', got $amiKeys")
        assertEquals(0, rows.first { it.bengali == "আমি" }.tier) // freq>0 => Tier A
    }

    @Test
    fun tierBForWordsWithoutFrequency() {
        val rows = PhoneticIndexBuilder.build(words = listOf("আমি"), frequencies = emptyMap())
        assertEquals(1, rows.first().tier)
    }

    @Test
    fun chhWordsGetCAlias() {
        // ছবি reverses to chobi/chhobi family; lowercase scheme types it as cobi
        val rows = PhoneticIndexBuilder.build(words = listOf("ছবি"), frequencies = emptyMap())
        val keys = rows.map { it.key }
        assertTrue(keys.any { it.startsWith("c") && !it.startsWith("ch") },
            "expected a c-alias for chh word, got $keys")
    }

    @Test
    fun reportsRoundTripCoverage() {
        PhoneticIndexBuilder.build(words = listOf("আমি", "তুমি"), frequencies = emptyMap())
        assertTrue(PhoneticIndexBuilder.lastReport.totalWords == 2)
    }

    // -------------------------------------------------------------------------
    // New tests
    // -------------------------------------------------------------------------

    /**
     * ছত্রী reverses to "chhotrii" (probe-verified: chh + ii both present).
     * The fully-collapsed combined alias must be "cotri" (chh→c then ii→i).
     * This exercises the composed-collapse path in aliasesFor().
     *
     * Alias derivation:
     *   canonical  : "chhotrii"
     *   chh→c      : "cotrii"
     *   ii→i       : "chhotri"
     *   collapsed  : "cotri"   ← chh→c then ii→i applied in sequence
     */
    @Test
    fun combinedAliasForChhAndLongVowelWords() {
        // Probe finding: ReverseTransliterator.reverseWord("ছত্রী") = "chhotrii"
        // Contains both "chh" and "ii" → fully-collapsed alias = "cotri"
        val rows = PhoneticIndexBuilder.build(words = listOf("ছত্রী"), frequencies = emptyMap())
        val keys = rows.map { it.key }
        assertTrue(
            keys.any { it == "cotri" },
            "expected composed-collapse alias 'cotri' for ছত্রী (canonical 'chhotrii'), got $keys"
        )
    }

    @Test
    fun filtersExcludeOutOfScopeWords() {
        val rows = PhoneticIndexBuilder.build(
            words = listOf("ও", "আমি্", "abc", "আমি আমি"),  // 1-char, hasanta-final, latin, contains space
            frequencies = emptyMap()
        )
        assertTrue(rows.isEmpty())
        assertEquals(0, PhoneticIndexBuilder.lastReport.totalWords)
    }

    /**
     * দুঃখ reverses to "du:kh" (probe-verified).
     * The ":" character fails the ROMAN_ONLY filter, so every alias for this
     * word is dropped and the word contributes 0 rows — exercising the
     * wordsWithNoRows and droppedKeys counters.
     */
    @Test
    fun visargaWordsCountedAsUnindexed() {
        PhoneticIndexBuilder.build(words = listOf("দুঃখ"), frequencies = emptyMap())
        val report = PhoneticIndexBuilder.lastReport
        assertEquals(1, report.totalWords)
        assertEquals(1, report.wordsWithNoRows)
        assertTrue(report.droppedKeys >= 1)
    }

    @Test
    fun coveragePercentZeroWhenNoWords() {
        PhoneticIndexBuilder.build(words = emptyList(), frequencies = emptyMap())
        assertEquals(0.0, PhoneticIndexBuilder.lastReport.coveragePercent)
    }
}
