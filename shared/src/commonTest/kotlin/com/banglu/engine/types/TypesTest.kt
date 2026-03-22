package com.banglu.engine.types

import kotlin.test.Test
import kotlin.test.assertEquals

class TypesTest {
    @Test
    fun testSmartDictionaryEntry() {
        val entry = SmartDictionaryEntry(
            bengali = "আমি",
            phonetics = listOf("ami", "amii"),
            frequency = 100,
            category = WordCategory.TADBHAVA
        )
        assertEquals("আমি", entry.bengali)
        assertEquals(2, entry.phonetics.size)
        assertEquals(100, entry.frequency)
    }

    @Test
    fun testLookupResult() {
        val result = LookupResult(
            bengali = "আমি",
            matchedPhonetic = "ami",
            frequency = 100,
            confidence = 0.95,
            source = ResolutionSource.DICTIONARY
        )
        assertEquals(0.95, result.confidence)
        assertEquals(ResolutionSource.DICTIONARY, result.source)
    }

    @Test
    fun testConversionResult() {
        val result = ConversionResult(
            bengali = "আমি",
            confidence = 0.90,
            source = ResolutionSource.DICTIONARY,
            alternatives = listOf(
                Alternative("আমী", 0.70)
            )
        )
        assertEquals(1, result.alternatives.size)
    }

    @Test
    fun testTrieEntry() {
        val entry = TrieEntry(bengali = "আমি", frequency = 100)
        assertEquals("আমি", entry.bengali)
    }

    @Test
    fun testOverlapResult() {
        val result = OverlapResult(score = 0.85, inputCoverage = 1.0, isPrefix = true)
        assertEquals(0.85, result.score)
        assertEquals(true, result.isPrefix)
    }
}
