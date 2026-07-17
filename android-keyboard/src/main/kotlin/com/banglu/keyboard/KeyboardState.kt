package com.banglu.keyboard

enum class KeyboardMode {
    BANGLU,     // Bengali phonetic conversion
    ENGLISH,    // Direct English passthrough
    SYMBOLS_1,  // Symbols page 1
    SYMBOLS_2,  // Symbols page 2
    EMOJI,      // Emoji picker panel
    CLIPBOARD   // Local clipboard history panel
}

enum class ShiftState {
    OFF,        // Lowercase
    ON,         // Uppercase for one letter, then auto-off
    CAPS_LOCK   // Uppercase until toggled off
}

enum class VoiceInputState {
    IDLE,
    LISTENING,
    PROCESSING,
    STOPPED,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    ERROR,
    /** S55 (F-ANDROID-006): startListening was called but no RecognitionListener
     *  callback arrived within the watchdog window — the recognizer is dead,
     *  not just slow. Distinct from ERROR so the UI can give the specific
     *  "try again" message instead of a generic failure. */
    WATCHDOG_TIMEOUT,
    /** S55 (F-ANDROID-006): the offline Bangla speech pack is not installed
     *  and no online recognizer is reachable — never leave a live listening
     *  chip on screen for this case, show the actionable message instead. */
    OFFLINE_PACK_MISSING
}

enum class ThemeMode {
    LIGHT,
    DARK,
    AMOLED
}
