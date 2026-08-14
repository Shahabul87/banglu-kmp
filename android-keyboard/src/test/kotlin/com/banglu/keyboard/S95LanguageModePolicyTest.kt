package com.banglu.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S95 (tester 2026-08-14: "switch to english, type, keyboard auto switches
 * back to bengali — very annoying"): the language-mode state machine, pinned.
 */
class S95LanguageModePolicyTest {

    // ── the tester's snap-back scenarios ─────────────────────────────────

    @Test
    fun globeToggleTracksTheLetterMode() {
        val r = LanguageModePolicy.globeToggle(KeyboardMode.BANGLU, KeyboardMode.BANGLU)
        assertEquals(KeyboardMode.ENGLISH, r.mode)
        assertEquals(KeyboardMode.ENGLISH, r.letterMode, "the toggle must update the tracked letter mode")
    }

    @Test
    fun symbolsRoundTripKeepsEnglish() {
        // EN user opens !#1 and comes back: collapse must return ENGLISH.
        val afterToggle = LanguageModePolicy.globeToggle(KeyboardMode.BANGLU, KeyboardMode.BANGLU)
        val back = LanguageModePolicy.collapseTransient(KeyboardMode.SYMBOLS_1, afterToggle.letterMode)
        assertEquals(KeyboardMode.ENGLISH, back)
    }

    @Test
    fun emojiReopenKeepsEnglish() {
        // EN user opens emoji, hides keyboard, reopens (same app):
        // onFieldStart collapses the transient layer back to ENGLISH.
        val r = LanguageModePolicy.onFieldStart(
            samePackage = true,
            current = KeyboardMode.EMOJI,
            letterMode = KeyboardMode.ENGLISH,
            defaultMode = KeyboardMode.BANGLU,
        )
        assertEquals(KeyboardMode.ENGLISH, r.mode)
        assertEquals(KeyboardMode.ENGLISH, r.letterMode)
    }

    @Test
    fun sameAppFieldChangeNeverResetsTheChoice() {
        // The S76 contract: a deliberate EN toggle survives same-app
        // round trips regardless of the settings default.
        val r = LanguageModePolicy.onFieldStart(
            samePackage = true,
            current = KeyboardMode.ENGLISH,
            letterMode = KeyboardMode.ENGLISH,
            defaultMode = KeyboardMode.BANGLU,
        )
        assertEquals(KeyboardMode.ENGLISH, r.mode)
    }

    @Test
    fun newAppResetsToTheSettingsDefault() {
        // The S67 contract: one accidental toggle must not haunt every app.
        val r = LanguageModePolicy.onFieldStart(
            samePackage = false,
            current = KeyboardMode.ENGLISH,
            letterMode = KeyboardMode.ENGLISH,
            defaultMode = KeyboardMode.BANGLU,
        )
        assertEquals(KeyboardMode.BANGLU, r.mode)
        assertEquals(KeyboardMode.BANGLU, r.letterMode)
    }

    @Test
    fun englishDefaultUsersLandInEnglishInNewApps() {
        val r = LanguageModePolicy.onFieldStart(
            samePackage = false,
            current = KeyboardMode.BANGLU,
            letterMode = KeyboardMode.BANGLU,
            defaultMode = KeyboardMode.ENGLISH,
        )
        assertEquals(KeyboardMode.ENGLISH, r.mode)
    }

    // ── pre-S95 behaviors kept ───────────────────────────────────────────

    @Test
    fun globeInsideSymbolsFlipsTheReturnModeButStaysInSymbols() {
        val r = LanguageModePolicy.globeToggle(KeyboardMode.SYMBOLS_2, KeyboardMode.BANGLU)
        assertEquals(KeyboardMode.SYMBOLS_2, r.mode)
        assertEquals(KeyboardMode.ENGLISH, r.letterMode)
    }

    @Test
    fun globeFromEmojiLandsOnTheOppositeLetterMode() {
        val r = LanguageModePolicy.globeToggle(KeyboardMode.EMOJI, KeyboardMode.BANGLU)
        assertEquals(KeyboardMode.ENGLISH, r.mode)
        assertEquals(KeyboardMode.ENGLISH, r.letterMode)
    }

    @Test
    fun clipboardGlobeReturnsToTheCurrentLetterMode() {
        val r = LanguageModePolicy.globeToggle(KeyboardMode.CLIPBOARD, KeyboardMode.ENGLISH)
        assertEquals(KeyboardMode.ENGLISH, r.mode)
        assertEquals(KeyboardMode.ENGLISH, r.letterMode)
    }

    @Test
    fun collapseLeavesLetterModesAlone() {
        assertEquals(
            KeyboardMode.BANGLU,
            LanguageModePolicy.collapseTransient(KeyboardMode.BANGLU, KeyboardMode.ENGLISH)
        )
    }
}
