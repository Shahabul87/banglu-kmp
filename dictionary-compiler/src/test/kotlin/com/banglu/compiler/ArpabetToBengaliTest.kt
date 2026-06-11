package com.banglu.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArpabetToBengaliTest {

    private fun conv(pron: String): String {
        val result = ArpabetToBengali.convert(pron.split(" "))
        assertNotNull(result, "Expected non-null result for: $pron")
        return result
    }

    @Test
    fun simpleWords() {
        // cat: K AE1 T
        assertEquals("ক্যাট", conv("K AE1 T"))
        // bus: B AH1 S
        assertEquals("বাস", conv("B AH1 S"))
        // computer: K AH0 M P Y UW1 T ER0
        assertEquals("কম্পিউটার", conv("K AH0 M P Y UW1 T ER0"))
    }

    @Test
    fun glidesAndDiphthongs() {
        // time: T AY1 M
        assertEquals("টাইম", conv("T AY1 M"))
        // go: G OW1
        assertEquals("গো", conv("G OW1"))
        // house: HH AW1 S
        assertEquals("হাউস", conv("HH AW1 S"))
    }

    @Test
    fun unstressedSchwaMidWordIsInherent() {
        // doctor: D AA1 K T ER0
        assertEquals("ডাক্টার", conv("D AA1 K T ER0"))
    }

    @Test
    fun positionalGlides() {
        // yes: Y EH1 S -> word-initial Y -> ইয়
        assertEquals("ইয়েস", conv("Y EH1 S"))
        // water: W AO1 T ER0 -> word-initial W -> ওয়
        assertEquals("ওয়াটার", conv("W AO1 T ER0"))
        // quick: K W IH1 K -> W after consonant -> ু
        assertEquals("কুইক", conv("K W IH1 K"))
    }

    // ------------------------------------------------------------------
    // Fix 1: NG (velar nasal) lookahead
    // ------------------------------------------------------------------

    @Test
    fun ngWordFinalIsAnusvara() {
        // running: R AH1 N IH0 NG -> রানিং  (NG word-final -> ং, no hasanta)
        assertEquals("রানিং", conv("R AH1 N IH0 NG"))
    }

    @Test
    fun ngBeforeNonGConsonantIsAnusvara() {
        // bank: B AE1 NG K -> ব্যাংক  (NG before K -> ং, then K plain consonant)
        assertEquals("ব্যাংক", conv("B AE1 NG K"))
    }

    @Test
    fun ngBeforeVowelIsVelarNasal() {
        // singer: S IH1 NG ER0 -> সিঙার  (NG before vowel ER0 -> ঙ as consonant)
        assertEquals("সিঙার", conv("S IH1 NG ER0"))
    }

    @Test
    fun ngBeforeGIsVelarNasal() {
        // english: IH1 NG G L IH0 SH -> ইঙ্গ্লিশ  (NG before G -> ঙ, G joins with hasanta)
        assertEquals("ইঙ্গ্লিশ", conv("IH1 NG G L IH0 SH"))
    }

    // ------------------------------------------------------------------
    // Fix 2: word-initial / post-vowel AH0 must not vanish
    // ------------------------------------------------------------------

    @Test
    fun wordInitialAh0EmitsIndependentVowel() {
        // about: AH0 B AW1 T -> আবাউট  (AH0 word-initial -> আ independent)
        assertEquals("আবাউট", conv("AH0 B AW1 T"))
    }

    @Test
    fun midWordAh0AfterConsonantIsInherent() {
        // computer: K AH0 M P Y UW1 T ER0 -> কম্পিউটার  (AH0 after K -> inherent)
        assertEquals("কম্পিউটার", conv("K AH0 M P Y UW1 T ER0"))
        // doctor: D AA1 K T ER0 -> ডাক্টার  (no AH0 but verify stays green)
        assertEquals("ডাক্টার", conv("D AA1 K T ER0"))
    }

    // ------------------------------------------------------------------
    // Fix 3: strict token validation — null on unrecognized tokens
    // ------------------------------------------------------------------

    @Test
    fun unknownPhonemeReturnsNull() {
        // QQ1 is not a valid ARPABET phoneme
        assertNull(ArpabetToBengali.convert(listOf("K", "QQ1", "T")))
    }

    @Test
    fun stressDigitOutOfRangeReturnsNull() {
        // Stress digit 3 is not in the allowed 0-2 range
        assertNull(ArpabetToBengali.convert(listOf("K", "AE3", "T")))
    }

    @Test
    fun emptyPhonemeListReturnsNull() {
        assertNull(ArpabetToBengali.convert(emptyList()))
    }

    // ------------------------------------------------------------------
    // Fix 5: grapheme-validity property test
    // ------------------------------------------------------------------

    private val KARS = "ািীুূৃেৈোৌ".toSet()
    private fun assertValidBengali(s: String) {
        val consonantRange = 'ক'..'হ' // ক..হ
        // ড়, ঢ়, য় are two-codepoint sequences (base + nukta ়); detect by checking
        // if the character before the nukta (U+09BC) is itself a consonant-range char.
        fun isCons(c: Char?) = c != null && (c in consonantRange)
        for (i in s.indices) {
            val prevRaw = s.getOrNull(i - 1)
            // If previous raw char is nukta (়, U+09BC), step back one more to get the base
            val prev = if (prevRaw == '়') s.getOrNull(i - 2) else prevRaw
            when (s[i]) {
                in KARS -> assertTrue(isCons(prev), "kar after non-consonant at $i in $s")
                '্' -> {
                    val next = s.getOrNull(i + 1)
                    // Normal case: consonant + hasanta + consonant (or ya-phala য)
                    // Special case: অ + ্ + য forms the AE cluster "অ্যা" (ya-phala on vowel অ)
                    val validPrev = isCons(prev) || prev == 'অ'
                    val validNext = isCons(next) || next == 'য'
                    assertTrue(validPrev && validNext, "bad hasanta at $i in $s")
                }
                'ং' -> assertTrue(prev != '্', "hasanta before anusvara in $s")
            }
        }
    }

    @Test
    fun allOutputsAreValidBengaliGraphemes() {
        val words = listOf(
            "K AE1 T", "B AH1 S", "K AH0 M P Y UW1 T ER0", "T AY1 M", "G OW1",
            "HH AW1 S", "D AA1 K T ER0", "Y EH1 S", "W AO1 T ER0", "K W IH1 K",
            "S IH1 NG ER0", "B AE1 NG K", "R AH1 N IH0 NG", "IH1 NG G L IH0 SH",
            "AH0 B AW1 T", "V OY1 S", "B ER1 D", "M OW1 B AH0 L", "S K UW1 L",
            "T R EY1 N", "HH AE1 NG ER0", "AE1 NG G AH0 L", "M AH1 NG K IY0"
        )
        for (w in words) {
            val out = ArpabetToBengali.convert(w.split(" "))
            assertTrue(out != null && out.isNotEmpty(), "null/empty for $w")
            assertValidBengali(out)
        }
    }
}
