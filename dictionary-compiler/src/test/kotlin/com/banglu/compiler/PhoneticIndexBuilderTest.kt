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
}
