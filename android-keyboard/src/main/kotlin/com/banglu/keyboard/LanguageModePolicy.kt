package com.banglu.keyboard

/**
 * S95: the language-mode state machine, extracted pure so the snap-back class
 * of bugs is pinned by unit tests.
 *
 * Tester (2026-08-14): "switch to english, type some english words, keyboard
 * auto switches back to bengali". Root cause was ONE field doing two jobs:
 * `letterModeBeforeSymbols` was both "the letter mode to return to from
 * transient layers" AND "the settings default for a fresh app" — the globe
 * toggle never updated it, and reloadSettings() (every keyboard show)
 * clobbered it back to the default, so every transient return (symbols,
 * emoji, clipboard, keyboard reopen) snapped ENGLISH users to Bengali.
 *
 * The policy splits the concepts:
 *  - [Result.letterMode] — the user's CURRENT letter-mode choice. Only the
 *    globe toggle and the app-change reset may change it. Settings never do.
 *  - the settings default — consulted ONLY when the user enters a different
 *    app (the S67 contract: one accidental toggle must not haunt every app).
 */
internal object LanguageModePolicy {

    data class Result(val mode: KeyboardMode, val letterMode: KeyboardMode)

    private fun KeyboardMode.isLetter() =
        this == KeyboardMode.BANGLU || this == KeyboardMode.ENGLISH

    /** Globe key: toggle language (or the underlying letter mode from symbols). */
    fun globeToggle(current: KeyboardMode, letterMode: KeyboardMode): Result = when (current) {
        KeyboardMode.BANGLU -> Result(KeyboardMode.ENGLISH, KeyboardMode.ENGLISH)
        KeyboardMode.ENGLISH -> Result(KeyboardMode.BANGLU, KeyboardMode.BANGLU)
        KeyboardMode.SYMBOLS_1, KeyboardMode.SYMBOLS_2 -> {
            // Stay in symbols; flip the letter mode the back-key returns to.
            val flipped = if (letterMode == KeyboardMode.BANGLU) KeyboardMode.ENGLISH else KeyboardMode.BANGLU
            Result(current, flipped)
        }
        KeyboardMode.EMOJI -> {
            // Globe from emoji = "switch language": land on the OPPOSITE
            // letter mode (pre-S95 behavior, kept deliberately).
            val opposite = if (letterMode == KeyboardMode.BANGLU) KeyboardMode.ENGLISH else KeyboardMode.BANGLU
            Result(opposite, opposite)
        }
        KeyboardMode.CLIPBOARD -> Result(letterMode, letterMode)
    }

    /** Keyboard re-shown while a transient layer was up: fall back to letters. */
    fun collapseTransient(current: KeyboardMode, letterMode: KeyboardMode): KeyboardMode =
        if (current.isLetter()) current else letterMode

    /**
     * A new input session started. Same app: the user's choice is sacred
     * (S76 — a deliberate EN toggle survives search-box round trips). New
     * app: reset to the settings default (S67).
     */
    fun onFieldStart(
        samePackage: Boolean,
        current: KeyboardMode,
        letterMode: KeyboardMode,
        defaultMode: KeyboardMode,
    ): Result = if (samePackage) {
        Result(collapseTransient(current, letterMode), letterMode)
    } else {
        Result(defaultMode, defaultMode)
    }
}
