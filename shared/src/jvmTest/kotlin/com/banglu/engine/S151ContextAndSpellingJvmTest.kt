package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S151 (second pass over the S149 backlog): kothai reads the chat intent
 * কোথায় (কথাই stays a strip twin), and the context-homograph pairs keep
 * both readings within the strip so the trigram/bigram rerank — measured
 * by the S149 harness's context pass — always has the right candidate to
 * promote. Real store on ./dictionary.sqlite.
 */
class S151ContextAndSpellingJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)
    private fun primary(k: String) = fold(engine.convertWord(k).bengali)
    private fun strip(k: String) = engine.getSuggestions(k, 6).map { fold(it.bengali) }

    @Test
    fun kothaiReadsTheChatIntent() {
        assertEquals(fold("কোথায়"), primary("kothai"))
        assertTrue(fold("কথাই") in strip("kothai"), "কথাই keeps a slot: ${strip("kothai")}")
    }

    @Test
    fun homographTwinsBothRideTheStrip() {
        val pairs = mapOf(
            "ase" to listOf("আসে", "আছে"),
            "hoi" to listOf("হই", "হয়"),
            "jai" to listOf("যাই", "যায়"),
            "pore" to listOf("পরে", "পড়ে"),
            "jan" to listOf("জান", "যান"),
            "bon" to listOf("বন", "বোন")
        )
        for ((key, twins) in pairs) {
            val s = strip(key)
            for (t in twins) {
                assertTrue(fold(t) in s, "'$key' strip must carry ${t}: $s")
            }
        }
    }

    @Test
    fun contextRerankPromotesTheObservedReading() {
        // The lane the IME uses at commit: rerankWithContext(prev2, prev1).
        // কেমন আছে / চলে আসে — both directions must be reachable from "ase".
        val base = engine.convertWord("ase")
        val ache = engine.rerankWithContext("তুমি", "কেমন", base).bengali
        assertEquals(fold("আছে"), fold(ache), "কেমন __ context must promote আছে")
    }
}
