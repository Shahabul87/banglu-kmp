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
        // doctor: D AA1 K T ER0 -> ডক্টার
        // (Curated-benchmark driven: stressed AA in a closed syllable takes the inherent
        // vowel — block ব্লক, college কলেজ, coffee কফি, boss বস. Curated doctor is ডাক্তার;
        // the remaining ক্ত-vs-ক্ট gap is a consonant question, out of scope for F4.)
        assertEquals("ডক্টার", conv("D AA1 K T ER0"))
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
        // english: IH1 NG G L IH0 SH -> ইঙ্গলিশ  (NG before G -> ঙ, G joins with hasanta;
        // the G-L cluster no longer joins under the curated conjunct policy — cross-syllable
        // clusters stay open: laptop ল্যাপটপ, magnet ম্যাগনেট. "english" itself is not curated.)
        assertEquals("ইঙ্গলিশ", conv("IH1 NG G L IH0 SH"))
    }

    // ------------------------------------------------------------------
    // Fix 2: word-initial / post-vowel AH0 must not vanish
    // ------------------------------------------------------------------

    @Test
    fun wordInitialAh0EmitsIndependentVowel() {
        // about: AH0 B AW1 T -> অ্যাবাউট  (AH0 word-initial -> অ্যা; curated-benchmark
        // driven: account অ্যাকাউন্ট, adapter অ্যাডাপ্টার, alarm অ্যালার্ম, alert অ্যালার্ট)
        assertEquals("অ্যাবাউট", conv("AH0 B AW1 T"))
    }

    @Test
    fun midWordAh0AfterConsonantIsInherent() {
        // computer: K AH0 M P Y UW1 T ER0 -> কম্পিউটার  (pre-tonic AH0 after K -> inherent)
        assertEquals("কম্পিউটার", conv("K AH0 M P Y UW1 T ER0"))
        // doctor: D AA1 K T ER0 -> ডক্টার  (closed-syllable AA1 -> inherent; see above)
        assertEquals("ডক্টার", conv("D AA1 K T ER0"))
    }

    // ------------------------------------------------------------------
    // F4: vowel + conjunct rules tuned against the curated lexicon
    // ------------------------------------------------------------------

    @Test
    fun finalSyllableSchwaIsEKar() {
        // model: M AA1 D AH0 L -> মডেল  (curated মডেল; AA1 inherent, final-syllable AH0 -> ে)
        assertEquals("মডেল", conv("M AA1 D AH0 L"))
        // cancel: K AE1 N S AH0 L -> ক্যানসেল  (curated ক্যানসেল)
        assertEquals("ক্যানসেল", conv("K AE1 N S AH0 L"))
    }

    @Test
    fun postTonicSchwaIsIKar() {
        // president: P R EH1 Z AH0 D EH2 N T -> প্রেজিডেন্ট  (curated প্রেসিডেন্ট; post-tonic
        // AH0 -> ি as in monitor মনিটর, oxygen অক্সিজেন; জ-vs-স measured better as জ overall)
        assertEquals("প্রেজিডেন্ট", conv("P R EH1 Z AH0 D EH2 N T"))
    }

    @Test
    fun stressedAaInClosedSyllableIsInherent() {
        // project: P R AA1 JH EH0 K T -> প্রজেক্ট  (curated প্রজেক্ট)
        assertEquals("প্রজেক্ট", conv("P R AA1 JH EH0 K T"))
        // block: B L AA1 K -> ব্লক  (curated ব্লক)
        assertEquals("ব্লক", conv("B L AA1 K"))
    }

    @Test
    fun crossSyllableClustersStayOpen() {
        // laptop: L AE1 P T AA2 P -> ল্যাপটপ  (curated ল্যাপটপ; no mid-word প্ট conjunct)
        assertEquals("ল্যাপটপ", conv("L AE1 P T AA2 P"))
        // internet: IH1 N T ER0 N EH2 T -> ইন্টারনেট  (homorganic ন্ট joins, unstressed ER0 stays open)
        assertEquals("ইন্টারনেট", conv("IH1 N T ER0 N EH2 T"))
    }

    @Test
    fun stressedErTakesReph() {
        // burger: B ER1 G ER0 -> বার্গার  (curated বার্গার; ER1+consonant conjuncts as reph)
        assertEquals("বার্গার", conv("B ER1 G ER0"))
        // percent: P ER0 S EH1 N T -> পারসেন্ট  (curated পারসেন্ট; ER0 stays open)
        assertEquals("পারসেন্ট", conv("P ER0 S EH1 N T"))
    }

    @Test
    fun rColouredVowelsUseGlide() {
        // chair: CH EH1 R -> চেয়ার  (curated চেয়ার)
        assertEquals("চেয়ার", conv("CH EH1 R"))
        // gear: G IH1 R -> গিয়ার  (curated গিয়ার)
        assertEquals("গিয়ার", conv("G IH1 R"))
    }

    @Test
    fun aeAfterRaLaClusterIsAaKar() {
        // class: K L AE1 S -> ক্লাস  (curated ক্লাস)
        assertEquals("ক্লাস", conv("K L AE1 S"))
        // tram: T R AE1 M -> ট্রাম  (curated ট্রাম)
        assertEquals("ট্রাম", conv("T R AE1 M"))
        // ram: R AE1 M -> র‍্যাম  (curated র‍্যাম; ZWJ keeps র base form before ya-phala)
        assertEquals("র‍্যাম", conv("R AE1 M"))
    }

    @Test
    fun oyClosesWithEKarBeforeCoda() {
        // voice: V OY1 S -> ভয়েস  (curated ভয়েস)
        assertEquals("ভয়েস", conv("V OY1 S"))
        // branch: B R AE1 N CH -> ব্রাঞ্চ  (curated ব্রাঞ্চ; N before CH assimilates to ঞ)
        assertEquals("ব্রাঞ্চ", conv("B R AE1 N CH"))
    }

    @Test
    fun wordInitialYuwIsIU() {
        // youtube: Y UW1 T Y UW2 B -> ইউটিউব  (curated ইউটিউব)
        assertEquals("ইউটিউব", conv("Y UW1 T Y UW2 B"))
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
            // If previous raw char is nukta (়, U+09BC) or ZWJ (U+200D, used for র‍্যা),
            // step back one more to get the base consonant
            val prev = if (prevRaw == '়' || prevRaw == '‍') s.getOrNull(i - 2) else prevRaw
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
            "T R EY1 N", "HH AE1 NG ER0", "AE1 NG G AH0 L", "M AH1 NG K IY0",
            // F4 rule-trigger cases
            "R AE1 M",                       // র‍্যাম (ZWJ + ya-phala)
            "CH EH1 R",                      // চেয়ার (EH+R glide)
            "G IH1 R",                       // গিয়ার (IH+R glide)
            "B ER1 G ER0",                   // বার্গার (stressed-ER reph)
            "P ER0 S EH1 N T",               // পারসেন্ট (unstressed ER stays open)
            "S AY1 AH0 N S",                 // সায়েন্স (AY before vowel + post-vowel schwa)
            "K W AA1 N T AH0 M",             // কোয়ান্টাম (qu- + AA after W + schwa before M)
            "B R AE1 N CH",                  // ব্রাঞ্চ (ঞ্চ assimilation)
            "D EH1 S K",                     // ডেস্ক (স্ক coda)
            "Y UW1 T Y UW2 B",               // ইউটিউব (initial Y+UW)
            "D AA1 K Y AH0 M EH0 N T",       // ডকুমেন্ট (C+Y+schwa -> ু)
            "P L EY1 ER0",                   // প্লেয়ার (ER after vowel -> য়ার)
            "T AW1 AH0 L",                   // টাওয়েল (AW before vowel)
            "T AY1 ER0",                     // টায়ার (AY before ER)
            "D IH0 T EY1 L",                 // ডিটেইল (EY before L)
            "AO1 R D ER0",                   // অর্ডার (word-initial AO)
            "B AO1 R D",                     // বোর্ড (AO+R after B)
            "F AO1 R M AE2 T",               // ফরম্যাট (AO+R inherent)
            "JH IY1 N Z",                    // জিন্স (final-cluster Z -> স)
            "G ER0 AA1 ZH",                  // গারাজ? (word-final ZH -> জ)
            "T EH1 L AH0 V IH2 ZH AH0 N",    // টেলিভিশন (ZH -> শ + -sion schwa)
            "EH1 M P L OY1 IY0",             // এমপ্লয়ি (OY before vowel)
            "P R EH1 Z AH0 D EH2 N T",       // প্রেজিডেন্ট (post-tonic schwa)
            "M AA1 D AH0 L"                  // মডেল (final-syllable schwa)
        )
        for (w in words) {
            val out = ArpabetToBengali.convert(w.split(" "))
            assertTrue(out != null && out.isNotEmpty(), "null/empty for $w")
            assertValidBengali(out)
        }
    }
}
