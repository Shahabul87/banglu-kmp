package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class V2KarCompositionRegressionTest {
    private fun engine(): SmartEngine = SmartEngine().also { it.initializeSync() }

    @Test
    fun liveComposingUsesDependentKarAfterConsonants() {
        val engine = engine()
        val cases = mapOf(
            "ka" to "কা",
            "ki" to "কি",
            "kii" to "কী",
            "ku" to "কু",
            "kuu" to "কূ",
            "ke" to "কে",
            "ko" to "কো",
            "kou" to "কৌ",
            "kri" to "কৃ",
            "kla" to "ক্লা",
            "pro" to "প্রো"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertForComposing(input).bengali, input)
        }
    }

    @Test
    fun liveComposingKeepsWaOaYaVerbExceptionsAheadOfPlainKarRules() {
        val engine = engine()
        val cases = mapOf(
            "khawa" to "খাওয়া",
            "jawa" to "যাওয়া",
            "pawa" to "পাওয়া",
            "howa" to "হওয়া",
            "newa" to "নেওয়া",
            "dewa" to "দেওয়া"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertForComposing(input).bengali, input)
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }

    @Test
    fun completedDictionaryWordsStillBeatLiteralRuleComposition() {
        val engine = engine()
        assertEquals("টাকা", engine.convertForComposing("taka").bengali)
        assertEquals("দরজা", engine.convertForComposing("doroja").bengali)
    }
}
