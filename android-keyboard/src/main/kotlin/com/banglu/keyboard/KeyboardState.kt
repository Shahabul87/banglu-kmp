package com.banglu.keyboard

enum class KeyboardMode {
    BANGLU,     // Bengali phonetic conversion
    ENGLISH,    // Direct English passthrough
    SYMBOLS_1,  // Symbols page 1
    SYMBOLS_2,  // Symbols page 2
    EMOJI       // Emoji picker panel
}

enum class ShiftState {
    OFF,        // Lowercase
    ON,         // Uppercase for one letter, then auto-off
    CAPS_LOCK   // Uppercase until toggled off
}

enum class ThemeMode {
    LIGHT,
    DARK,
    AMOLED
}
