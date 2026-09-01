package com.banglu.keyboard

import com.banglu.engine.types.SmartSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S162 — strip layout ঙ: leading typed-roman ghost chip, literal dedupe,
 * and the EN-mode Bangla-mirror gates.
 */
class S162TypedChipPolicyTest {

    private fun s(bengali: String, source: String = "store", tier: String = "t0") =
        SmartSuggestion(bengali, 0.9, source, "x", tier)

    @Test
    fun ghostChipLeadsTheBanglaStrip() {
        val out = TypedChipPolicy.decorateBanglaStrip("kmon", listOf(s("কেমন"), s("কমন")))
        assertEquals(3, out.size)
        assertEquals("kmon", out[0].bengali)
        assertEquals(TypedChipPolicy.TYPED_ROMAN_SOURCE, out[0].source)
        assertEquals(TypedChipPolicy.TYPED_ROMAN_TIER, out[0].tier)
        assertEquals("কেমন", out[1].bengali)
    }

    @Test
    fun emptyBufferChangesNothing() {
        val engine = listOf(s("কেমন"))
        assertEquals(engine, TypedChipPolicy.decorateBanglaStrip("", engine))
    }

    @Test
    fun literalDuplicatesFoldIntoTheGhost() {
        // S141 typed_literal / S142 english_passthrough carry the raw roman —
        // with the ghost chip leading, a second literal chip is noise.
        val out = TypedChipPolicy.decorateBanglaStrip(
            "phone",
            listOf(s("ফোনে"), s("phone", source = "english_passthrough"), s("Phone"))
        )
        assertEquals(listOf("phone", "ফোনে"), out.map { it.bengali })
    }

    @Test
    fun ghostTiersAreExactlyTheTwoGhosts() {
        assertTrue(TypedChipPolicy.isGhostTier(TypedChipPolicy.TYPED_ROMAN_TIER))
        assertTrue(TypedChipPolicy.isGhostTier(TypedChipPolicy.EN_BANGLA_MIRROR_TIER))
        assertFalse(TypedChipPolicy.isGhostTier("prediction"))
        assertFalse(TypedChipPolicy.isGhostTier("punctuation"))
        assertFalse(TypedChipPolicy.isGhostTier(""))
    }

    @Test
    fun mirrorGateRejectsEverydayEnglishAndJunk() {
        // kemon is not an English word — worth converting.
        assertTrue(TypedChipPolicy.mirrorWorthTrying("kemon", isCommonEnglishWord = false))
        // the/was ARE everyday English — never a দ্য chip.
        assertFalse(TypedChipPolicy.mirrorWorthTrying("the", isCommonEnglishWord = true))
        // Too short, digits, emails: never.
        assertFalse(TypedChipPolicy.mirrorWorthTrying("k", isCommonEnglishWord = false))
        assertFalse(TypedChipPolicy.mirrorWorthTrying("k2mon", isCommonEnglishWord = false))
        assertFalse(TypedChipPolicy.mirrorWorthTrying("a@b", isCommonEnglishWord = false))
        // Non-ASCII letters (already Bengali) make no sense to mirror.
        assertFalse(TypedChipPolicy.mirrorWorthTrying("কেমন", isCommonEnglishWord = false))
    }

    @Test
    fun mirrorAcceptsOnlyConfidentBengaliScript() {
        assertTrue(TypedChipPolicy.mirrorAccepts("কেমন", 0.9))
        assertFalse(TypedChipPolicy.mirrorAccepts("kemon", 0.9))   // engine echoed Latin
        assertFalse(TypedChipPolicy.mirrorAccepts("কেমন", 0.3))    // floor guess
    }
}
