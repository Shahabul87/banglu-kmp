package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CChLowercaseMappingRegressionTest {
    private val engine = SmartEngine().also { it.initializeSync() }

    @Test
    fun cMapsToChoInCommonNegativeVerbEndings() {
        val cases = mapOf(
            "partecina" to "পারতেছিনা",
            "kortecina" to "করতেছিনা",
            "khaitecina" to "খাইতেছিনা",
            "likhtecina" to "লিখতেছিনা",
        )

        for ((input, expected) in cases) {
            val result = engine.convertWord(input).bengali
            assertEquals(expected, result, "$input should use c=ছ in the -tecina ending")
        }
    }

    @Test
    fun chRemainsChaAndCRemainsCho() {
        assertEquals("চীন", engine.convertWord("china").bengali)

        val cinaSuggestions = engine.getSuggestions("cina", 6).map { it.bengali }
        assertTrue(
            cinaSuggestions.any { it.startsWith("ছি") || it.startsWith("ছ") },
            "cina suggestions should prefer c=ছ forms, got $cinaSuggestions"
        )
    }

    @Test
    fun commonAcchaTypingVariantsShareSamePrimaryWord() {
        for (input in listOf("accha", "acca", "assa")) {
            assertEquals("আচ্ছা", engine.convertWord(input).bengali, "$input commit conversion")
            assertEquals("আচ্ছা", engine.convertForComposing(input).bengali, "$input composing preview")
            assertEquals("আচ্ছা", engine.getSuggestions(input, 5).first().bengali, "$input primary suggestion")
        }
    }
}
