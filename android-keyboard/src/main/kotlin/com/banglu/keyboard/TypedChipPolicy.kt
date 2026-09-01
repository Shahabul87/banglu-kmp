package com.banglu.keyboard

import com.banglu.engine.types.SmartSuggestion

/**
 * S162 — the tester-approved strip layout (mock variant ঙ): the typed roman
 * rides the strip as its own leading ghost chip, the blue highlight stays on
 * the Bangla that space will commit, and English mode gets the mirror (a
 * Bangla ghost chip when the typed token confidently reads as Bengali).
 *
 * Pure decisions only — the service owns commits, the view owns styling.
 */
object TypedChipPolicy {

    /** BN mode: tap keeps the English literal exactly as typed. */
    const val TYPED_ROMAN_SOURCE = "typed_roman"
    const val TYPED_ROMAN_TIER = "typed_roman"

    /** EN mode: tap swaps the typed token for its Bangla reading. */
    const val EN_BANGLA_MIRROR_SOURCE = "en_bangla_mirror"
    const val EN_BANGLA_MIRROR_TIER = "bn_mirror"

    fun isGhostTier(tier: String): Boolean =
        tier == TYPED_ROMAN_TIER || tier == EN_BANGLA_MIRROR_TIER

    /**
     * Decorates the engine's Bangla-composing strip: ghost chip first with the
     * raw typed roman, and any engine entry that duplicates the literal
     * (S141 typed_literal, S142 english_passthrough) folds into it.
     */
    fun decorateBanglaStrip(typed: String, engine: List<SmartSuggestion>): List<SmartSuggestion> {
        if (typed.isEmpty()) return engine
        val ghost = SmartSuggestion(
            bengali = typed,
            confidence = 1.0,
            source = TYPED_ROMAN_SOURCE,
            phonetic = typed,
            tier = TYPED_ROMAN_TIER
        )
        return listOf(ghost) + engine.filterNot { it.bengali.equals(typed, ignoreCase = true) }
    }

    /**
     * EN-mode mirror gate: only a token that is NOT everyday English earns a
     * Bangla ghost chip — otherwise every "the" would grow a "দ্য" (the exact
     * noise the S142/S143 English law exists to prevent). Letters only, so
     * emails/numbers/URLs never trigger it.
     */
    fun mirrorWorthTrying(typed: String, isCommonEnglishWord: Boolean): Boolean =
        typed.length >= 2 && !isCommonEnglishWord && typed.all { it.isLetter() && it.code < 128 }

    /** The conversion must actually be Bengali script to be worth a chip. */
    fun mirrorAccepts(bengali: String, confidence: Double): Boolean =
        confidence >= 0.6 && bengali.any { it in 'ঀ'..'৿' }
}
