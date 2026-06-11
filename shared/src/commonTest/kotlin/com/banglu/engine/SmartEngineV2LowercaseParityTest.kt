package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartEngineV2LowercaseParityTest {
    private fun engine(): SmartEngine = SmartEngine().also { it.initializeSync() }

    @Test
    fun uppercaseIsNotARequiredBanglaControlLayer() {
        val engine = engine()

        assertEquals("ত", engine.convertForComposing("T").bengali)
        assertEquals("দ", engine.convertForComposing("D").bengali)
        assertEquals("র", engine.convertForComposing("R").bengali)
        assertEquals("তা", engine.convertForComposing("Ta").bengali)
        assertEquals("দি", engine.convertForComposing("Di").bengali)
    }

    @Test
    fun lowercaseExplicitControlsMatchSmartEngineV2() {
        val engine = engine()

        assertEquals("্", engine.convertForComposing("x").bengali)
        assertEquals("্", engine.convertForComposing("hc").bengali)
        assertEquals("খ্", engine.convertForComposing("khc").bengali)
        assertEquals("ধ্", engine.convertForComposing("dhc").bengali)
        assertEquals("ঃ", engine.convertForComposing("w").bengali)
        assertEquals("ঁ", engine.convertForComposing("nq").bengali)
        assertEquals("ং", engine.convertForComposing("ng").bengali)
        assertEquals("ং", engine.convertForComposing("ong").bengali)
    }

    @Test
    fun confusingConsonantDefaultsStayLowercasePredictable() {
        val engine = engine()

        assertEquals("ছ", engine.convertForComposing("c").bengali)
        assertEquals("চ", engine.convertForComposing("ch").bengali)
        assertEquals("ত", engine.convertForComposing("t").bengali)
        assertEquals("দ", engine.convertForComposing("d").bengali)
        assertEquals("ন", engine.convertForComposing("n").bengali)
        assertEquals("স", engine.convertForComposing("s").bengali)
        assertEquals("শ", engine.convertForComposing("sh").bengali)
        assertEquals("র", engine.convertForComposing("r").bengali)
        assertEquals("জ", engine.convertForComposing("j").bengali)
        assertEquals("য", engine.convertForComposing("z").bengali)
    }

    @Test
    fun namedSmartEngineV2ClustersCommitToExpectedBangla() {
        val engine = engine()
        val cases = mapOf(
            "torko" to "তর্ক",
            "sworgo" to "স্বর্গ",
            "dhormo" to "ধর্ম",
            "ortho" to "অর্থ",
            "class" to "ক্লাস",
            "tbk" to "ত্বক",
            "sbpno" to "স্বপ্ন",
            "bagmii" to "বাগ্মী",
            "padma" to "পদ্মা",
            "shanto" to "শান্ত",
            "gondho" to "গন্ধ",
            "vondo" to "ভণ্ড",
            "biggan" to "বিজ্ঞান",
            "trivuj" to "ত্রিভুজ",
            "shroddha" to "শ্রদ্ধা",
            "buddhi" to "বুদ্ধি",
            "bidya" to "বিদ্যা",
            "chihno" to "চিহ্ন",
            "bangla" to "বাংলা",
            "iccha" to "ইচ্ছা",
            "lojja" to "লজ্জা",
            "jonmo" to "জন্ম",
            "baxo" to "বাক্স",
            "koxobajar" to "কক্সবাজার"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }

    @Test
    fun lowercaseRankingDoesNotPromoteFlapWithoutTypedR() {
        val suggestions = engine().getSuggestions("daka", 8).map { it.bengali }

        assertFalse(
            suggestions.firstOrNull().orEmpty().contains("ড়"),
            "Primary suggestion should not contain flap without typed r: $suggestions"
        )
        assertTrue(suggestions.isNotEmpty())
    }
}
