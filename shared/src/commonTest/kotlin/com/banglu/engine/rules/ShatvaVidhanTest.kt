package com.banglu.engine.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class ShatvaVidhanTest {

    @Test
    fun testAfterRiKar() {
        // কৃ context → should be ষ
        val result = ShatvaVidhan.resolve("\u0995\u09C3", "krishno", 2) // কৃ
        assertEquals('\u09B7', result.bengali) // ষ
    }

    @Test
    fun testWordInitial() {
        // Empty context, sibilant at position 0 → শ
        val result = ShatvaVidhan.resolve("", "shokal", 0)
        assertEquals('\u09B6', result.bengali) // শ
    }

    @Test
    fun testWithPrefix() {
        // nishod — "ni" prefix + "shod" remaining starts with 's'/'sh'
        val result = ShatvaVidhan.resolve("", "nishod", 2)
        assertEquals('\u09B7', result.bengali) // ষ (prefix match)
    }

    @Test
    fun testDefault() {
        // "basha" with sibilant at position 2, no prefix, no context triggers
        val result = ShatvaVidhan.resolve("", "basha", 2)
        assertEquals('\u09B6', result.bengali) // শ (default medial)
    }

    @Test
    fun testAfterRefForm() {
        // র্ context (র followed by hasanta) → ষ
        val result = ShatvaVidhan.resolve("\u09B0\u09CD", "test", 0) // র্
        assertEquals('\u09B7', result.bengali) // ষ
    }

    @Test
    fun testVowelSignBefore() {
        // ি before sibilant → weaker signal for ষ
        // Use a word that does NOT match any triggering prefix to test the vowel sign path
        val result = ShatvaVidhan.resolve("\u09BF", "koshi", 2) // ি context, "koshi" has no prefix match
        assertEquals('\u09B7', result.bengali) // ষ (with low confidence)
        assertEquals(0.55, result.confidence)
    }

    @Test
    fun testWordFinal() {
        // sibilant at end of word → শ
        val result = ShatvaVidhan.resolve("", "desh", 2) // "sh" at position 2, length 4 → 2+2 >= 4
        assertEquals('\u09B6', result.bengali) // শ
        assertEquals(0.65, result.confidence)
    }
}
