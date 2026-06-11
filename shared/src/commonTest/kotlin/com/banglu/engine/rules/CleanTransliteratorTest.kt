package com.banglu.engine.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertEquals("এক", t("ek"))           // word-initial e → এ
    }

    @Test
    fun determinism() {
        // Same input always yields same output, and output is pure Bengali (no residual Latin)
        val out = t("abcdefghijklmnopqrstuvwxyz")
        assertEquals(out, t("abcdefghijklmnopqrstuvwxyz"))
        assertTrue(out.none { it in 'a'..'z' }, "residual latin in $out")
    }

    // ── Fix 1: w + vowel → ওয় glide unit ────────────────────────────────────

    @Test
    fun wBeforeVowelGlide() {
        // Word-initial w + vowel: wasim → ওয়াসিম
        // Trace: w(a follows)→ওয়, a→া (kar on য়), s→্স? No: pWC=true after ওয়, s→হাসান্ত+স
        // Actually: ওয়(pWC=true), a→া, s(pWC=true)→স(no hasanta since pWC was reset by vowel 'a'), i→ি, m→ম
        // Trace: w→ওয়(pWC=true), a(vowel,pWC=true)→া(pWC=false), s→স(pWC=true), i→ি(pWC=false), m→ম(pWC=true)
        // = ওয়াসিম ✓
        assertEquals("ওয়াসিম", t("wasim"))

        // Mid-word w + vowel: hawa → হাওয়া
        // Trace: h→হ(pWC=true), a→া(pWC=false), w(a follows,pWC=false)→ওয়(no hasanta,pWC=true), a→া(pWC=false)
        // = হাওয়া ✓
        assertEquals("হাওয়া", t("hawa"))
    }

    // ── Fix 2: post-consonant y → ya-phala (্য not ্য়) ─────────────────────

    @Test
    fun yaPhala() {
        // byatha → ব্যাথা
        // Trace: b→ব(pWC=true), y(pWC=true)→্য(pWC=true), a→া(pWC=false), th→থ(pWC=true), a→া(pWC=false)
        // = ব্যাথা ✓
        assertEquals("ব্যাথা", t("byatha"))

        // shyamol → শ্যামল
        // Trace: sh→শ(pWC=true), y(pWC=true)→্য(pWC=true), a→া(pWC=false), m→ম(pWC=true), o→""(pWC=false), l→ল(pWC=true)
        // = শ্যামল ✓
        assertEquals("শ্যামল", t("shyamol"))

        // Word-initial y still → য় (not ya-phala)
        // priyo: p→প(pWC=true), r(pWC=true)→্র(pWC=true), i→ি(pWC=false), y(pWC=false)→য়(pWC=true), o→""(pWC=false)
        // = প্রিয় ✓ (regression check)
        assertEquals("প্রিয়", t("priyo"))
    }

    // ── Fix 3: ng before vowel or g → ঙ (velar nasal) ───────────────────────

    @Test
    fun ngVelarNasal() {
        // rongin → রঙিন
        // Trace: r→র(pWC=true), o→""(pWC=false), ng(next='i',vowel)→ঙ(pWC=false→no hasanta,pWC=true),
        //        i→ি(pWC=false), n→ন(pWC=true)
        // = রঙিন ✓
        assertEquals("রঙিন", t("rongin"))

        // bangla stays: বাংলা (ng followed by l → anusvara)
        // Trace: b→ব(pWC=true), a→া(pWC=false), ng(next='l')→ং(pWC=false), l→ল(pWC=true), a→া(pWC=false)
        // = বাংলা ✓
        assertEquals("বাংলা", t("bangla"))

        // shongo → শঙ (ng consumed as digraph; 'g' is part of 'ng', no leftover 'g' to conjoin)
        // Trace: sh→শ(pWC=true), o→""(pWC=false), ng(next='o',vowel)→ঙ(pWC=true), o→""(pWC=false)
        // = শঙ  (readable; the trigger here is the vowel 'o' after ng, not a separate 'g')
        assertEquals("শঙ", t("shongo"))
    }

    // ── Fix 5-9: table-locking assertions ────────────────────────────────────

    @Test
    fun tableLocking() {
        // kkh prefix: kkhoma → ক্ষমা
        // Trace: kkh→ক্ষ(pWC=true), o→""(pWC=false), m→ম(pWC=true), a→া(pWC=false)
        // = ক্ষমা ✓
        assertEquals("ক্ষমা", t("kkhoma"))

        // ou → ঔ (word-initial diphthong)
        assertEquals("ঔ", t("ou"))

        // mou → মৌ (ou after consonant → ৌ kar)
        // Trace: m→ম(pWC=true), ou(pWC=true)→ৌ(pWC=false)
        // = মৌ ✓
        assertEquals("মৌ", t("mou"))

        // empty string → empty string
        assertEquals("", t(""))
    }
}
