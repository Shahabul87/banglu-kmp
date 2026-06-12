package com.banglu.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
     * F1 corpus fix: দুঃখ used to reverse to "du:kh" (":" failed ROMAN_ONLY,
     * word got 0 rows). Visarga is now silent but geminates the following
     * consonant ("dukkh" + trailing-o alias "dukkho" — how users type it).
     * The ungeminated "dukh" must NOT be indexed: it belongs to দুখ and
     * would shadow that exact word (parity fixture 'dukh' -> দুখ).
     */
    @Test
    fun visargaWordsGetTypeableKeys() {
        val rows = PhoneticIndexBuilder.build(words = listOf("দুঃখ"), frequencies = emptyMap())
        val report = PhoneticIndexBuilder.lastReport
        assertEquals(1, report.totalWords)
        assertEquals(0, report.wordsWithNoRows)
        val keys = rows.map { it.key }
        assertTrue("dukkh" in keys, "expected canonical key 'dukkh', got $keys")
        assertTrue("dukkho" in keys, "expected geminated typing key 'dukkho', got $keys")
        assertFalse("dukh" in keys, "ungeminated 'dukh' must not shadow দুখ, got $keys")
    }

    /** F1 corpus fix: decomposed-nukta words (ড = ড + ়) must be indexed. */
    @Test
    fun decomposedNuktaWordsGetKeys() {
        val decomposedBoro = "বড়" // ব + ড + ় (decomposed বড়)
        val rows = PhoneticIndexBuilder.build(words = listOf(decomposedBoro), frequencies = emptyMap())
        assertEquals(0, PhoneticIndexBuilder.lastReport.wordsWithNoRows)
        assertTrue(rows.any { it.key == "bor" }, "expected key 'bor', got ${rows.map { it.key }}")
    }

    /** য emits canonical "z"; users overwhelmingly type "j" — alias required. */
    @Test
    fun zWordsGetJAlias() {
        val rows = PhoneticIndexBuilder.build(words = listOf("যদি"), frequencies = emptyMap())
        val keys = rows.map { it.key }
        assertTrue("zodi" in keys, "expected canonical 'zodi', got $keys")
        assertTrue("jodi" in keys, "expected j-alias 'jodi', got $keys")
    }

    /**
     * F1 corpus fix: 3-consonant clusters no longer leak hasanta, and the
     * natural typing for ya-phala / cluster-final words is among the keys.
     */
    @Test
    fun clusterWordsGetNaturalTypingKeys() {
        val rows = PhoneticIndexBuilder.build(
            words = listOf("যুক্তরাষ্ট্র", "লক্ষ্য", "সন্ধ্যা", "স্বাস্থ্য", "দারিদ্র্য", "স্ত্রী"),
            frequencies = emptyMap()
        )
        assertEquals(0, PhoneticIndexBuilder.lastReport.wordsWithNoRows)
        fun keysOf(word: String) = rows.filter { it.bengali == word }.map { it.key }
        assertTrue("juktorashtro" in keysOf("যুক্তরাষ্ট্র"), "got ${keysOf("যুক্তরাষ্ট্র")}")
        assertTrue("lokkho" in keysOf("লক্ষ্য"), "got ${keysOf("লক্ষ্য")}")
        assertTrue("sondha" in keysOf("সন্ধ্যা"), "got ${keysOf("সন্ধ্যা")}")
        assertTrue("swastho" in keysOf("স্বাস্থ্য"), "got ${keysOf("স্বাস্থ্য")}")
        assertTrue("daridro" in keysOf("দারিদ্র্য"), "got ${keysOf("দারিদ্র্য")}")
        assertTrue("stri" in keysOf("স্ত্রী"), "got ${keysOf("স্ত্রী")}")
    }

    @Test
    fun coveragePercentZeroWhenNoWords() {
        PhoneticIndexBuilder.build(words = emptyList(), frequencies = emptyMap())
        assertEquals(0.0, PhoneticIndexBuilder.lastReport.coveragePercent)
    }
}
