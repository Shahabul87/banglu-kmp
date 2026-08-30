package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S147 (Android home redesign, 2026-08-30): every word the home screen
 * advertises — in every listed spelling variant — must convert exactly on
 * the real dictionary. The UI reads the same ShowcaseWords list, so a
 * failing pin here means the app would be showing off a conversion the
 * engine gets wrong. Includes the new shassoto/shasstho → স্বাস্থ্য chat
 * alias added this round.
 */
class S147ShowcaseWordsJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun everyShowcasedVariantConvertsToItsWord() {
        val failures = ShowcaseWords.ALL_PAIRS.mapNotNull { (roman, bengali) ->
            val got = fold(engine.convertWord(roman).bengali)
            if (got != fold(bengali)) "$roman -> $got (wanted $bengali)" else null
        }
        assertEquals(emptyList(), failures, "showcased words must never miss")
    }

    @Test
    fun shassotoChatAliasReachesShastho() {
        for (key in listOf("shassoto", "shasstho", "sasstho")) {
            assertEquals(fold("স্বাস্থ্য"), fold(engine.convertWord(key).bengali), key)
        }
    }
}
