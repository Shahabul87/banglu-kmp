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
        for (key in listOf("bujtechi", "bujteci", "bujhtechi")) {
            assertEquals("বুঝতেছি", engine.convertWord(key).bengali, "key $key")
        }
        // S18: bujtesi now yields the literal chat spelling (a real chat-
        // lexicon word); বুঝতেছি stays in the strip.
        assertEquals("বুঝতেসি", engine.convertWord("bujtesi").bengali)
        assertTrue("বুঝতেছি" in engine.getSuggestions("bujtesi", 4).map { it.bengali })
    }

    @Test
    fun negatedVariants_allResolve() {
        for (key in listOf("bujhtechhina")) {
            assertEquals("বুঝতেছিনা", engine.convertWord(key).bengali, "key $key")
        }
        // S18: -tesi stems now resolve to the literal chat spelling.
        assertEquals("বুঝতেসিনা", engine.convertWord("bujtesina").bengali)
        assertEquals("বুঝতেসিনা", engine.convertWord("bujhtesina").bengali)
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

    // ── S18 additions (register study follow-up) ────────────────────────

    @Test
    fun naiNegation_resolvesAttachedNai() {
        assertEquals("খাইনাই", engine.convertWord("khainai").bengali)
        assertEquals("জানিনাই", engine.convertWord("janinai").bengali)
        assertEquals("হইনাই", engine.convertWord("hoinai").bengali)
    }

    @Test
    fun chatLexicon_wordsResolve() {
        assertTrue("গেসি" in engine.getSuggestions("gesi", 3).map { it.bengali })
        assertEquals("করতেসি", engine.convertWord("kortesi").bengali)
        assertEquals("ঠিকাছে", engine.convertWord("thikachhe").bengali)
        assertEquals("পুরাই", engine.convertWord("purai").bengali)
        assertEquals("আছোস", engine.convertWord("achhos").bengali)
    }

    @Test
    fun osSuffix_secondPersonInformalReachable() {
        // পারস (evidenced) correctly keeps primary; পারোস must be reachable in
        // the scrollable strip (was entirely absent pre-S18). Whether an exact-
        // key chat word should outrank high-usage prefix continuations
        // (পারস্পরিক) is an open strip-ranking question for the S17-report
        // divergence/strip audit round.
        val paros = engine.getSuggestions("paros", 8).map { it.bengali }
        assertTrue("পারোস" in paros, "পারোস reachable for paros, got $paros")
        assertEquals("আছোস", engine.convertWord("achhos").bengali)
    }

    @Test
    fun jacKhacContinuousClassResolves() {
        // S21: enumerated chat stems (no habit chain derives ite -> c).
        assertEquals("যাইতেছি", engine.convertWord("jactesi").bengali)
        assertEquals("খাইতেছি", engine.convertWord("khactesi").bengali)
        assertEquals("যাইতেছিনা", engine.convertWord("jactesina").bengali)
        assertEquals("খাইতেছিনা", engine.convertWord("khactesina").bengali)
    }
}