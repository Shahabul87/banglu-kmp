package com.banglu.engine.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Key-generation coverage tests (Engine v3 corpus-fix round, F1).
 *
 * Covers the three root causes that left 20,749 corpus words unindexed:
 * 1. Decomposed nukta (base + U+09BC) not folded to precomposed ড়/ঢ়/য়.
 * 2. 3-consonant clusters leaking a literal hasanta (U+09CD) into keys.
 * 3. Visarga (U+0983) emitting ":" which fails the [a-z]+ key filter.
 */
class ReverseTransliteratorKeyCoverageTest {

    private val romanOnly = Regex("^[a-zA-Z0-9]+$")

    // ========================================================================
    // 1. Nukta fold: decomposed (base + U+09BC) must equal precomposed
    // ========================================================================

    @Test
    fun decomposedNuktaRroEqualsPrecomposed() {
        // বড় decomposed: ব + ড + ় ; precomposed: ব + ড়
        val decomposed = "বড়"
        val precomposed = "বড়"
        assertEquals(
            ReverseTransliterator.reverseWord(precomposed),
            ReverseTransliterator.reverseWord(decomposed),
            "decomposed ড+় must produce the same key as precomposed ড়"
        )
        assertEquals("bor", ReverseTransliterator.reverseWord(decomposed))
    }

    @Test
    fun decomposedNuktaBariGetsRomanKey() {
        // বাড়ি decomposed: ব া ড ় ি
        val decomposed = "বাড়ি"
        val precomposed = "বাড়ি"
        assertEquals(
            ReverseTransliterator.reverseWord(precomposed),
            ReverseTransliterator.reverseWord(decomposed)
        )
        assertEquals("bari", ReverseTransliterator.reverseWord(decomposed))
    }

    @Test
    fun decomposedNuktaGariAndPore() {
        // গাড়ি decomposed
        assertEquals("gari", ReverseTransliterator.reverseWord("গাড়ি"))
        // পড়ে decomposed
        assertEquals("pore", ReverseTransliterator.reverseWord("পড়ে"))
    }

    @Test
    fun decomposedNuktaRrhoAndYoFolded() {
        // ঢ + ় == ঢ় ; য + ় == য়
        assertEquals(
            ReverseTransliterator.reverseWord("আষাঢ়"),  // আষাঢ় precomposed
            ReverseTransliterator.reverseWord("আষাঢ়")
        )
        assertEquals(
            ReverseTransliterator.reverseWord("মেয়ে"),  // মেয়ে precomposed
            ReverseTransliterator.reverseWord("মেয়ে")
        )
    }

    // ========================================================================
    // 2. 3-consonant clusters: no hasanta may leak into the key
    // ========================================================================

    @Test
    fun threeConsonantClustersEmitNoHasanta() {
        val words = listOf(
            "যুক্তরাষ্ট্র", "স্ত্রী", "অস্ত্র", "সম্প্রতি", "লক্ষ্য",
            "স্বাস্থ্য", "সন্ধ্যা", "দারিদ্র্য", "ব্র্যাক", "আফ্রিকা",
            "যুক্তরাষ্ট্রের", "স্বতঃস্ফূর্ত"
        )
        for (word in words) {
            val key = ReverseTransliterator.reverseWord(word)
            assertFalse(key.contains('্'), "hasanta leaked into key for $word: '$key'")
            assertTrue(romanOnly.matches(key), "non-roman key for $word: '$key'")
        }
    }

    @Test
    fun shtrClusterJuktorashtro() {
        // যুক্তরাষ্ট্র: ষ্ট্র -> shtr (user types "juktorashtro" / engine canonical uses z for য)
        assertEquals("zuktorashtr", ReverseTransliterator.reverseWord("যুক্তরাষ্ট্র"))
    }

    @Test
    fun strClusterStri() {
        // স্ত্রী: স্ত্র -> str
        assertEquals("strii", ReverseTransliterator.reverseWord("স্ত্রী"))
    }

    @Test
    fun mprClusterSomproti() {
        // সম্প্রতি: ম্প্র -> mpr
        assertEquals("somproti", ReverseTransliterator.reverseWord("সম্প্রতি"))
    }

    @Test
    fun kkhyClusterLokkhy() {
        // লক্ষ্য: ক্ষ্য -> kkhy
        assertEquals("lokkhy", ReverseTransliterator.reverseWord("লক্ষ্য"))
    }

    @Test
    fun sthyClusterSwasthy() {
        // স্বাস্থ্য: স্থ্য -> sthy
        assertEquals("swasthy", ReverseTransliterator.reverseWord("স্বাস্থ্য"))
    }

    @Test
    fun ndhyClusterSondhya() {
        // সন্ধ্যা: ন্ধ্য -> ndhy
        assertEquals("sondhya", ReverseTransliterator.reverseWord("সন্ধ্যা"))
    }

    @Test
    fun dryAndBryClusters() {
        // দারিদ্র্য: দ্র্য -> dry ; ব্র্যাক: ব্র্য -> bry
        assertEquals("daridry", ReverseTransliterator.reverseWord("দারিদ্র্য"))
        assertEquals("bryak", ReverseTransliterator.reverseWord("ব্র্যাক"))
    }

    @Test
    fun frClusterAfrika() {
        // আফ্রিকা: ফ্র -> fr (users type "afrika", not "aphrika")
        assertEquals("afrika", ReverseTransliterator.reverseWord("আফ্রিকা"))
    }

    // ========================================================================
    // 3. Visarga: zero emission + gemination variant
    // ========================================================================

    @Test
    fun visargaGeminatesFollowingConsonant() {
        // ঃ is silent but geminates the following consonant: দুঃখ is
        // pronounced (and typed) "dukkho", never ":"-anything or "dukho".
        // The ungeminated "dukh" belongs to the distinct word দুখ and must
        // NOT be emitted for দুঃখ.
        assertEquals("dukkh", ReverseTransliterator.reverseWord("দুঃখ"))
        assertEquals("dukkhojonok", ReverseTransliterator.reverseWord("দুঃখজনক"))
    }

    @Test
    fun wordFinalVisargaIsSilent() {
        // পুনঃ: word-final ঃ has nothing to geminate — silent, no ":"
        val key = ReverseTransliterator.reverseWord("পুনঃ")
        assertTrue(romanOnly.matches(key), "expected roman key, got '$key'")
    }

    @Test
    fun swotosphurtoGetsValidKey() {
        val canonical = ReverseTransliterator.reverseWord("স্বতঃস্ফূর্ত")
        assertTrue(romanOnly.matches(canonical), "expected roman key, got '$canonical'")
    }
}
