package com.banglu.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S71 regression (tester screenshot 2026-08-02): voice output lost every
 * inter-word space the moment a segment contained one Latin token
 * ("এটা কেন হচ্ছে … repetition হচ্ছে" → "এটাকেনহচ্ছে…রেদাতিওনহচ্ছে"), and the
 * glued text then shared no word prefix with the on-screen live text, so
 * VoicePartialDiff appended it as a "fresh segment" — the duplicated
 * sentences in the same report.
 */
class VoiceTextNormalizerTest {

    private val fakeConvert: (String) -> String = { token ->
        when (token) {
            "repetition" -> "রেপিটিশন"
            "test" -> "টেস্ট"
            "ok" -> "ওকে"
            else -> token // unconverted — stays Latin, normalizer keeps original
        }
    }

    @Test
    fun `root-cause pin - Kotlin split discards capturing-group delimiters (unlike JavaScript)`() {
        // The old implementation relied on JS semantics where the captured
        // whitespace stays in the array. In Kotlin/Java it does NOT — this is
        // the exact mechanism that glued the tester's sentence together.
        assertEquals(listOf("a", "b", "c"), "a b  c".split(Regex("(\\s+)")))
    }

    @Test
    fun `spaces are preserved around converted Latin tokens`() {
        assertEquals(
            "এটা কেন হচ্ছে রেপিটিশন হচ্ছে",
            VoiceTextNormalizer.normalizeLatinTokens("এটা কেন হচ্ছে repetition হচ্ছে", fakeConvert)
        )
    }

    @Test
    fun `the tester's exact shape - multiple Bengali words plus one Latin token keeps every boundary`() {
        assertEquals(
            "ভয়েস টাইপিং টেস্ট করার জন্য",
            VoiceTextNormalizer.normalizeLatinTokens("ভয়েস টাইপিং test করার জন্য", fakeConvert)
        )
    }

    @Test
    fun `pure Bengali text passes through byte-identical`() {
        val text = "আমি ভয়েস টাইপিং করতেছি ভয়েস টাইপিং কেমন হচ্ছে"
        assertEquals(text, VoiceTextNormalizer.normalizeLatinTokens(text, fakeConvert))
    }

    @Test
    fun `multiple Latin tokens convert independently with spacing intact`() {
        assertEquals(
            "ওকে টেস্ট হবে",
            VoiceTextNormalizer.normalizeLatinTokens("ok test হবে", fakeConvert)
        )
    }

    @Test
    fun `punctuation attached to a Latin token survives conversion`() {
        assertEquals(
            "রেপিটিশন, হচ্ছে",
            VoiceTextNormalizer.normalizeLatinTokens("repetition, হচ্ছে", fakeConvert)
        )
    }

    @Test
    fun `unconvertible Latin tokens stay untouched`() {
        assertEquals(
            "xyz হচ্ছে",
            VoiceTextNormalizer.normalizeLatinTokens("xyz হচ্ছে", fakeConvert)
        )
    }

    @Test
    fun `digits and mixed tokens are never converted`() {
        assertEquals(
            "রুম 42 নম্বর",
            VoiceTextNormalizer.normalizeLatinTokens("রুম 42 নম্বর", fakeConvert)
        )
    }

    @Test
    fun `multiple consecutive spaces are preserved exactly`() {
        assertEquals(
            "টেস্ট  হবে",
            VoiceTextNormalizer.normalizeLatinTokens("test  হবে", fakeConvert)
        )
    }

    @Test
    fun `converter exceptions leave the token untouched`() {
        val throwing: (String) -> String = { throw IllegalStateException("boom") }
        assertEquals(
            "test হবে",
            VoiceTextNormalizer.normalizeLatinTokens("test হবে", throwing)
        )
    }
}
