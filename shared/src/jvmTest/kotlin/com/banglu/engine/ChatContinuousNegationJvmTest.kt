package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S16 acceptance (bujtecina round, db 3.7.2).
 *
 * Chat-register continuous forms and attached negation:
 *  - compiler habit chains: verb_o_drop_te + h_lazy_jh + chh->ch/c/s make
 *    bujtechi/bujteci/bujtesi key বুঝতেছি (canonical "bujhotechhi");
 *  - tryNegationCompound resolves X+na through the store-backed prefix
 *    (bujtecina -> বুঝতেছি + না), running before the invented-composition
 *    layers that used to emit বুজয়তেছিনা@0.97;
 *  - stem trust: validator validity + frequency orders roots, so junk
 *    corpus words (বুজয়) never anchor compositions.
 */
class ChatContinuousNegationJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun bujtecina_resolvesToRealNegatedContinuous() {
        val r = engine.convertWord("bujtecina")
        assertEquals("বুঝতেছিনা", r.bengali, "user-reported garbage case (was বুজয়তেছিনা)")
    }

    @Test
    fun continuousAliases_allKeyTheRealWord() {
        for (key in listOf("bujtechi", "bujteci", "bujtesi", "bujhtechi")) {
            assertEquals("বুঝতেছি", engine.convertWord(key).bengali, "key $key")
        }
    }

    @Test
    fun negatedVariants_allResolve() {
        for (key in listOf("bujtesina", "bujhtechhina", "bujhtesina")) {
            assertEquals("বুঝতেছিনা", engine.convertWord(key).bengali, "key $key")
        }
        assertEquals("পারতেছিনা", engine.convertWord("partesina").bengali)
        assertEquals("করতেছিনা", engine.convertWord("kortecina").bengali)
    }

    @Test
    fun stemTrust_bujteNoLongerComposesFromJunkRoot() {
        // Was বুজয়তে (junk root বুজয় + তে); the store alias now wins.
        assertEquals("বুঝতে", engine.convertWord("bujte").bengali)
        val garbage = engine.getSuggestions("bujte", 4).map { it.bengali }
        assertTrue(garbage.none { "য়ত" in it }, "no য়-epenthesis garbage in strip: $garbage")
    }

    @Test
    fun wholeWordsStillBeatNegationLayer() {
        // Real corpus words ending in na must resolve as themselves.
        assertEquals("করছিনা", engine.convertWord("korchina").bengali)
        assertEquals("পারিনা", engine.convertWord("parina").bengali)
    }

    @Test
    fun composingPreviewMatchesCommitForNegationCompounds() {
        for (key in listOf("bujtecina", "bujtesina")) {
            val commit = engine.convertWord(key).bengali
            val preview = engine.convertForComposing(key).bengali
            assertEquals(commit, preview, "preview/commit divergence for $key")
        }
    }
}
