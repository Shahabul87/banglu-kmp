package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class EnglishLoanwordParityTest {
    private val engine = SmartEngine().also { it.initializeSync() }

    @Test
    fun countryNamesUseDictionaryEntries() {
        val cases = mapOf(
            "america" to "আমেরিকা",
            "china" to "চীন",
            "afghanistan" to "আফগানিস্তান",
            "canada" to "কানাডা",
            "germany" to "জার্মানি"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }

    @Test
    fun scienceTechnologyAndNamesDoNotFallThroughToPatterns() {
        val cases = mapOf(
            "hardware" to "হার্ডওয়্যার",
            "physics" to "ফিজিক্স",
            "chemistry" to "কেমিস্ট্রি",
            "biology" to "বায়োলজি",
            "mathematics" to "ম্যাথমেটিক্স",
            "virus" to "ভাইরাস",
            "john" to "জন",
            "michael" to "মাইকেল",
            "william" to "উইলিয়াম",
            "aisha" to "আইশা"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }
}
