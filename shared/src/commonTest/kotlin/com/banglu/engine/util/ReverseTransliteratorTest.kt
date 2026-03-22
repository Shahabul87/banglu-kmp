package com.banglu.engine.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReverseTransliteratorTest {

    // === Basic consonant + dependent vowel ===

    @Test
    fun testConsonantWithAaKar() {
        // কা = ক + া → "ka"
        val result = ReverseTransliterator.reverseWord("কা")
        assertEquals("ka", result)
    }

    @Test
    fun testConsonantWithIKar() {
        // কি = ক + ি → "ki"
        val result = ReverseTransliterator.reverseWord("কি")
        assertEquals("ki", result)
    }

    @Test
    fun testConsonantWithUKar() {
        // কু = ক + ু → "ku"
        val result = ReverseTransliterator.reverseWord("কু")
        assertEquals("ku", result)
    }

    @Test
    fun testConsonantWithEKar() {
        // কে = ক + ে → "ke"
        val result = ReverseTransliterator.reverseWord("কে")
        assertEquals("ke", result)
    }

    // === Independent vowels ===

    @Test
    fun testIndependentVowelA() {
        // আ → "a"
        val result = ReverseTransliterator.reverseWord("আ")
        assertEquals("a", result)
    }

    @Test
    fun testIndependentVowelO() {
        // অ → "o"
        val result = ReverseTransliterator.reverseWord("অ")
        assertEquals("o", result)
    }

    @Test
    fun testIndependentVowelI() {
        // ই → "i"
        val result = ReverseTransliterator.reverseWord("ই")
        assertEquals("i", result)
    }

    @Test
    fun testIndependentVowelE() {
        // এ → "e"
        val result = ReverseTransliterator.reverseWord("এ")
        assertEquals("e", result)
    }

    // === Simple words ===

    @Test
    fun testSimpleWordAmi() {
        // আমি = আ + মি → "ami"
        val result = ReverseTransliterator.reverseWord("আমি")
        assertTrue(result.contains("ami") || result.contains("am"), "Expected 'ami' or 'am', got '$result'")
    }

    @Test
    fun testSimpleWordTumi() {
        // তুমি = ত + ু + ম + ি → "tumi"
        val result = ReverseTransliterator.reverseWord("তুমি")
        assertTrue(result.contains("tumi") || result.contains("tum"), "Expected 'tumi', got '$result'")
    }

    // === Conjuncts ===

    @Test
    fun testConjunctKkh() {
        // ক্ষ → "kkh"
        val result = ReverseTransliterator.reverseWord("ক্ষ")
        assertTrue(result.contains("kkh") || result.contains("ksh"), "Expected 'kkh' or 'ksh', got '$result'")
    }

    @Test
    fun testConjunctGy() {
        // জ্ঞ → "gy"
        val result = ReverseTransliterator.reverseWord("জ্ঞ")
        assertTrue(result.contains("gy"), "Expected 'gy', got '$result'")
    }

    @Test
    fun testConjunctSt() {
        // স্ত → "st"
        val result = ReverseTransliterator.reverseWord("স্ত")
        assertTrue(result.contains("st"), "Expected 'st', got '$result'")
    }

    @Test
    fun testConjunctWithVowel() {
        // ক্ষা = ক্ষ + া → "kkha"
        val result = ReverseTransliterator.reverseWord("ক্ষা")
        assertTrue(result.contains("kkha") || result.contains("ksha"), "Expected 'kkha', got '$result'")
    }

    // === Ra-phala and Ya-phala ===

    @Test
    fun testRaPhala() {
        // প্র = প + ্ + র → "pr"
        val result = ReverseTransliterator.reverseWord("প্র")
        assertTrue(result.contains("pr"), "Expected 'pr', got '$result'")
    }

    @Test
    fun testYaPhala() {
        // ব্য = ব + ্ + য → "by"
        val result = ReverseTransliterator.reverseWord("ব্য")
        assertTrue(result.contains("by"), "Expected 'by', got '$result'")
    }

    // === Special marks ===

    @Test
    fun testAnusvara() {
        // বাং = ব + া + ং → "bang"
        val result = ReverseTransliterator.reverseWord("বাং")
        assertTrue(result.contains("bang"), "Expected 'bang', got '$result'")
    }

    @Test
    fun testChandrabindu() {
        // চাঁদ = চ + া + ঁ + দ
        val result = ReverseTransliterator.reverseWord("চাঁদ")
        assertTrue(result.isNotEmpty(), "Should produce non-empty result")
    }

    // === Multi-word (sentence) ===

    @Test
    fun testMultiWordSentence() {
        val result = ReverseTransliterator.reverseTransliterate("আমি তুমি")
        assertTrue(result.contains(" "), "Expected space-separated result, got '$result'")
    }

    @Test
    fun testEmptyInput() {
        assertEquals("", ReverseTransliterator.reverseWord(""))
        assertEquals("", ReverseTransliterator.reverseTransliterate(""))
    }

    @Test
    fun testWhitespaceOnly() {
        assertEquals("   ", ReverseTransliterator.reverseTransliterate("   "))
    }

    // === Inherent vowel ===

    @Test
    fun testInherentVowelBetweenConsonants() {
        // কম = ক + ম → "kom" (inherent 'o' between ক and ম)
        val result = ReverseTransliterator.reverseWord("কম")
        assertTrue(result.contains("ko") || result.contains("kom"), "Expected 'kom', got '$result'")
    }

    // === Bengali digits ===

    @Test
    fun testBengaliDigits() {
        val result = ReverseTransliterator.reverseWord("১২৩")
        assertEquals("123", result)
    }

    // === Unknown characters pass through ===

    @Test
    fun testUnknownCharacterPassthrough() {
        val result = ReverseTransliterator.reverseWord("hello")
        assertEquals("hello", result)
    }

    // === Aspirated consonants ===

    @Test
    fun testAspiratedConsonantKh() {
        // খা = খ + া → "kha"
        val result = ReverseTransliterator.reverseWord("খা")
        assertEquals("kha", result)
    }

    @Test
    fun testAspiratedConsonantGh() {
        // ঘা = ঘ + া → "gha"
        val result = ReverseTransliterator.reverseWord("ঘা")
        assertEquals("gha", result)
    }

    // === Danda punctuation ===

    @Test
    fun testDandaPunctuation() {
        val result = ReverseTransliterator.reverseWord("।")
        assertEquals(".", result)
    }

    @Test
    fun testDoubleDanda() {
        val result = ReverseTransliterator.reverseWord("।।")
        assertEquals("..", result)
    }
}
