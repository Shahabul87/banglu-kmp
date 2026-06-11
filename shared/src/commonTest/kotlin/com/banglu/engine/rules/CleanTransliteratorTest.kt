package com.banglu.engine.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class CleanTransliteratorTest {

    private fun t(roman: String) = CleanTransliterator.transliterate(roman)

    @Test
    fun simpleWords() {
        assertEquals("আমি", t("ami"))
        assertEquals("তুমি", t("tumi"))
        assertEquals("বন্ধু", t("bondhu"))   // inherent o + n+dh conjunct + u-kar
    }

    @Test
    fun namesAreReadableNeverSwapped() {
        assertEquals("রাফ্সান", t("rafsan"))  // f→ফ then s→্স (hasanta join); readable Bengali
        assertEquals("শাকিল", t("shakil"))
        // Deterministic floor: n is ALWAYS ন (no natva ণ swaps), s alone is ALWAYS স
        assertEquals("হাসান", t("hasan"))
    }

    @Test
    fun digraphsAndFolas() {
        assertEquals("খাতা", t("khata"))
        assertEquals("চা", t("cha"))
        assertEquals("ছবি", t("cobi"))        // lowercase rule: bare c → ছ
        assertEquals("প্রিয়", t("priyo"))     // r-phala via hasanta join, final yo → য়
    }

    @Test
    fun vowelForms() {
        assertEquals("ঐ", t("oi"))            // word-initial oi digraph → ঐ
        assertEquals("কৈ", t("koi"))          // after-consonant oi → ৈ kar
    }

    @Test
    fun determinism() {
        // Same input always yields same output, and output is pure Bengali
        val out = t("xyzkqv")
        assertEquals(out, t("xyzkqv"))
        check(out.none { ch -> ch in 'a'..'z' }) { "residual latin in $out" }
    }
}
