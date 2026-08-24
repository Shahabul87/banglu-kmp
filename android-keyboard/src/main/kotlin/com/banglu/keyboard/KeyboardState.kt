package com.banglu.keyboard

enum class KeyboardMode {
    BANGLU,     // Bengali phonetic conversion
    ENGLISH,    // Direct English passthrough
    SYMBOLS_1,  // Symbols page 1
    SYMBOLS_2,  // Symbols page 2
    EMOJI,      // Emoji picker panel
    CLIPBOARD,  // Local clipboard history panel
    NUMBER      // S122: numeric keypad for number/phone/PIN fields
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
    OFFLINE_PACK_MISSING,
    /** S55 (review follow-up): ERROR_CLIENT/ERROR_RECOGNIZER_BUSY kept
     *  restarting past the busy-retry cap (a stolen recognition slot never
     *  clears on its own) — give up with an actionable message instead of
     *  destroy+recreate looping forever. */
    BUSY_GIVEUP,
    /** S133: the silence cap fired on a session that never heard ONE word —
     *  a mic that never delivered (held by another app, muted, OEM audio
     *  routing), not a user who stopped talking. Ending that "gracefully"
     *  was the field report "no error shows up but no voice is picked up";
     *  this state carries the actionable message instead. */
    MIC_SILENT
}

enum class ThemeMode {
    LIGHT,
    DARK,
    AMOLED
}
