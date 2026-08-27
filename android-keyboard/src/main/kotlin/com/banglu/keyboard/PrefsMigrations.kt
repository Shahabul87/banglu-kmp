package com.banglu.keyboard

import android.content.SharedPreferences

/**
 * S139 (production re-audit of 1.5.85): `banglu_prefs` key migrations.
 *
 * 1.5.85 stored the clipboard-history opt-in as a Boolean under
 * "clipboard_history" — the very key that had carried the String history
 * payload since S57. SharedPreferences throws ClassCastException on a
 * type mismatch, so an upgrade with saved history crashed the keyboard at
 * every show (getBoolean on a String) and a fresh user who enabled history
 * crashed on opening the panel (getString on a Boolean). Two keys now, and
 * the legacy key is read TYPE-SAFELY through `all` and removed: a Boolean
 * moves to the new switch key; a String payload is purged — history is
 * opt-in, nothing the user did not explicitly enable is kept.
 *
 * Runs at the start of the keyboard process (IME onCreate) and in the
 * preferences provider, before any typed read of these keys. Idempotent.
 */
object PrefsMigrations {
    const val LEGACY_CLIPBOARD_KEY = "clipboard_history"
    const val CLIPBOARD_ENABLED_KEY = "clipboard_history_enabled"
    const val CLIPBOARD_ENTRIES_KEY = "clipboard_history_entries"

    /** @return true when a legacy value was found and migrated/purged durably. */
    fun migrate(prefs: SharedPreferences): Boolean {
        val all = prefs.all
        if (!all.containsKey(LEGACY_CLIPBOARD_KEY)) return false
        val editor = prefs.edit()
        val legacy = all[LEGACY_CLIPBOARD_KEY]
        if (legacy is Boolean && !all.containsKey(CLIPBOARD_ENABLED_KEY)) {
            editor.putBoolean(CLIPBOARD_ENABLED_KEY, legacy)
        }
        editor.remove(LEGACY_CLIPBOARD_KEY)
        return editor.commit()
    }
}
