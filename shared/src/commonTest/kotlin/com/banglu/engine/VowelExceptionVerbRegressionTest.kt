package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VowelExceptionVerbRegressionTest {
    private fun engine(): SmartEngine = SmartEngine().also { it.initializeSync() }

    @Test
    fun waOaYaVerbsUseDictionaryExceptionsBeforeKarRules() {
        val engine = engine()
        val cases = mapOf(
            "khawa" to "খাওয়া",
            "khaowa" to "খাওয়া",
            "khaoya" to "খাওয়া",
            "jawa" to "যাওয়া",
            "jaowa" to "যাওয়া",
            "jaoya" to "যাওয়া",
            "pawa" to "পাওয়া",
            "paowa" to "পাওয়া",
            "paoya" to "পাওয়া",
            "chawa" to "চাওয়া",
            "cawa" to "চাওয়া",
            "howa" to "হওয়া",
            "hoya" to "হওয়া",
            "newa" to "নেওয়া",
            "neowa" to "নেওয়া",
            "dewa" to "দেওয়া",
            "deowa" to "দেওয়া"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }

    @Test
    fun spellingVariantsStayVisibleInSuggestions() {
        val engine = engine()
        val suggestions = engine.getSuggestions("nawa", 8).map { it.bengali }
        assertTrue(
            suggestions.any { it == "নেওয়া" || it == "নাওয়া" },
            "nawa suggestions should expose নেওয়া/নাওয়া style exceptions, got $suggestions"
        )
    }
}
