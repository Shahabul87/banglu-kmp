package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals

/** S198 (S22: "bad ami", caret after বা, hold c → বাঁদ on screen, the commit wrote বাদ): an explicit ^ is never reranked away by context. */
class S198ExplicitMarkerContextJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun anExplicitChandrabinduSurvivesEveryContext() {
        for ((key, word) in listOf("ba^d" to "বাঁদ", "cha^d" to "চাঁদ", "ka^cha" to "কাঁচা", "ta^r" to "তাঁর")) {
            val base = engine.convertWord(key)
            assertEquals(fold(word), fold(base.bengali), key)
            for ((p2, p1) in listOf("" to "আমি", "আমি" to "তুমি", "" to "সে", "" to "")) {
                val ranked = engine.rerankWithContext(p2.ifEmpty { null }, p1.ifEmpty { null }, base, key = key)
                assertEquals(fold(word), fold(ranked.bengali), "$key after '$p2 $p1'")
            }
        }
    }
}
