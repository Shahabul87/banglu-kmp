package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ProductiveVerbSuffixRegressionTest {
    private val engine = SmartEngine().also { it.initializeSync() }

    @Test
    fun colloquialVerbFormsDoNotNeedDictionaryVariants() {
        val cases = mapOf(
            "korci" to "করছি",
            "korteco" to "করতেছো",
            "kortecen" to "করতেছেন",
            "kortecilam" to "করতেছিলাম",
            "parteci" to "পারতেছি",
            "parteco" to "পারতেছো",
            "partecen" to "পারতেছেন",
            "partecilam" to "পারতেছিলাম",
            "partecina" to "পারতেছিনা",
            "kortecina" to "করতেছিনা",
            "khaitecilam" to "খাইতেছিলাম",
            "khaitecina" to "খাইতেছিনা",
            "likhtecilam" to "লিখতেছিলাম",
            "likhtecina" to "লিখতেছিনা",
            "kortechi" to "করতেছি",
            "kortechen" to "করতেছেন",
            "kortechilam" to "করতেছিলাম",
            "partechilam" to "পারতেছিলাম",
            "khaitechilam" to "খাইতেছিলাম",
            "likhtechilam" to "লিখতেছিলাম",
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, "$input should be composed by the engine")
        }
    }

    @Test
    fun liveComposingUsesSameProductiveVerbFormsAsCommit() {
        val cases = mapOf(
            "kortechi" to "করতেছি",
            "kortechilam" to "করতেছিলাম",
            "korteci" to "করতেছি",
            "kortecilam" to "করতেছিলাম",
            "korbo" to "করবো",
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertForComposing(input).bengali, "$input composing preview must match commit path")
            assertEquals(expected, engine.convertWord(input).bengali, "$input commit path must match composing preview")
        }
    }

    @Test
    fun shortKorWordsDoNotMixWithProgressiveVerbForms() {
        val cases = listOf(
            "korta" to "কর্তা",
            "kormo" to "কর্ম",
            "korbo" to "করবো",
        )
        val blockedProgressiveForms = setOf("করতেছি", "করতেছিলাম", "করতেছো", "করতেছেন")

        for ((input, expectedPrimary) in cases) {
            assertEquals(expectedPrimary, engine.convertWord(input).bengali, "$input primary conversion changed")
            val suggestions = engine.getSuggestions(input, 8).map { it.bengali }
            assertFalse(
                suggestions.any { it in blockedProgressiveForms },
                "$input suggestions should not include unrelated progressive verbs: $suggestions"
            )
        }
    }
}
