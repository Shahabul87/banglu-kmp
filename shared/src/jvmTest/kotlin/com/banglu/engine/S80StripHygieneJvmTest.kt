package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * S80 full-store half: even with every oracle loaded, invented-compound keys
 * (korbone, parbonane) leaked grapheme-impossible substitution variants
 * (কদ়বোনে, পাদ়বোনানে) into the strip — pinned gone; and the parle strip
 * carries the real inflection family the tester asked for.
 */
class S80StripHygieneJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun noImpossibleNuktaOnAnyProbedKey() {
        for (key in listOf("parle", "parbone", "parbonane", "korbone", "korbona", "jabone", "bacha")) {
            val bad = engine.getSuggestions(key, 8).filter { s ->
                s.bengali.withIndex().any { (i, c) ->
                    c == '়' && (i == 0 || s.bengali[i - 1] !in "ডঢয")
                }
            }
            assertTrue(bad.isEmpty(), "impossible-nukta junk for '$key': ${bad.map { it.bengali }}")
        }
    }

    @Test
    fun parleStripCarriesTheInflectionFamily() {
        if (engine.getSuggestions("parle", 5).none { fold(it.bengali) == fold("পারলে") }) return // pre-3.8.9 db
        val strip = engine.getSuggestions("parle", 6).map { fold(it.bengali) }
        assertTrue(fold("পারলেন") in strip, "পারলেন must be on the parle strip: $strip")
        assertTrue(fold("পারল") in strip || fold("পারলাম") in strip,
            "the inflection family must fill the strip: $strip")
    }
}
