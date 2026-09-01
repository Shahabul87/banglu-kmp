package com.banglu.keyboard

import com.banglu.engine.types.SmartSuggestion

/**
 * S162 — the tester-approved strip layout (mock variant ঙ): the typed roman
 * rides the strip as its own leading ghost chip, the blue highlight stays on
 *
 * Pure decisions only — the service owns commits, the view owns styling.
 */
object TypedChipPolicy {

    /** BN mode: tap keeps the English literal exactly as typed. */
    const val TYPED_ROMAN_SOURCE = "typed_roman"
    const val TYPED_ROMAN_TIER = "typed_roman"

    // S167: the EN-mode Bangla-mirror ghost chip (S162) was removed on user
    // verdict — English mode shows English only.
    fun isGhostTier(tier: String): Boolean = tier == TYPED_ROMAN_TIER

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

}
