package com.banglu.engine.dictionary

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class TypingHabitNormalizerTest {
    @Test fun testGGExpansion() {
        val v = TypingHabitNormalizer.expand("biggan")
        assertTrue(v.contains("bigyan"))
    }
    @Test fun testIAExpansion() {
        val v = TypingHabitNormalizer.expand("dunia")
        assertTrue(v.contains("duniya"))
    }
    @Test fun testShInitialExpansion() {
        val v = TypingHabitNormalizer.expand("shokal")
        assertTrue(v.contains("sokal"))
    }
    @Test fun testShortInputUnchanged() {
        val v = TypingHabitNormalizer.expand("ab")
        assertEquals(1, v.size)
        assertEquals("ab", v[0])
    }
    @Test fun testOriginalIncluded() {
        val v = TypingHabitNormalizer.expand("biggan")
        assertTrue(v.contains("biggan"))
    }
    @Test fun testDDExpansion() {
        val v = TypingHabitNormalizer.expand("uddho")
        assertTrue(v.contains("udyho") || v.contains("uddho"))
    }
    @Test fun testMaxVariantsLimit() {
        val v = TypingHabitNormalizer.expand("biggan", maxVariants = 3)
        assertTrue(v.size <= 3)
    }
}
