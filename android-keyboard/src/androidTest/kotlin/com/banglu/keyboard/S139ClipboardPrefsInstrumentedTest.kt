package com.banglu.keyboard

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S139 (production re-audit of 1.5.85): the clipboard preference-type
 * collision — one key held the Boolean switch AND the String payload, which
 * SharedPreferences answers with ClassCastException. Real SharedPreferences,
 * real upgrade shapes.
 */
@RunWith(AndroidJUnit4::class)
class S139ClipboardPrefsInstrumentedTest {

    private lateinit var context: Context

    private fun prefs() = context.getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs().edit()
            .remove(PrefsMigrations.LEGACY_CLIPBOARD_KEY)
            .remove(PrefsMigrations.CLIPBOARD_ENABLED_KEY)
            .remove(PrefsMigrations.CLIPBOARD_ENTRIES_KEY)
            .commit()
    }

    @After
    fun tearDown() {
        prefs().edit()
            .remove(PrefsMigrations.LEGACY_CLIPBOARD_KEY)
            .remove(PrefsMigrations.CLIPBOARD_ENABLED_KEY)
            .remove(PrefsMigrations.CLIPBOARD_ENTRIES_KEY)
            .commit()
    }

    @Test
    fun upgradeWithLegacyStringHistoryIsPurgedAndNeverReadAsBoolean() {
        // 1.5.84 and earlier: String payload under the legacy key.
        prefs().edit().putString(PrefsMigrations.LEGACY_CLIPBOARD_KEY, "aGVsbG8=,1700000000000").commit()
        assertTrue(PrefsMigrations.migrate(prefs()))
        assertNull(prefs().all[PrefsMigrations.LEGACY_CLIPBOARD_KEY], "legacy key removed")
        assertFalse(prefs().contains(PrefsMigrations.CLIPBOARD_ENABLED_KEY), "no opt-in was ever recorded")
        // The reads the keyboard performs at start must not throw.
        assertFalse(prefs().getBoolean(PrefsMigrations.CLIPBOARD_ENABLED_KEY, false))
        assertNull(prefs().getString(PrefsMigrations.CLIPBOARD_ENTRIES_KEY, null))
    }

    @Test
    fun upgradeFrom1585BooleanMovesToTheSwitchKey() {
        // 1.5.85: Boolean opt-in stored under the legacy key.
        prefs().edit().putBoolean(PrefsMigrations.LEGACY_CLIPBOARD_KEY, true).commit()
        assertTrue(PrefsMigrations.migrate(prefs()))
        assertNull(prefs().all[PrefsMigrations.LEGACY_CLIPBOARD_KEY])
        assertEquals(true, prefs().getBoolean(PrefsMigrations.CLIPBOARD_ENABLED_KEY, false))
        // Both typed reads are now safe.
        assertNull(prefs().getString(PrefsMigrations.CLIPBOARD_ENTRIES_KEY, null))
    }

    @Test
    fun migrationIsIdempotentAndKeepsAnExplicitDecision() {
        prefs().edit().putBoolean(PrefsMigrations.CLIPBOARD_ENABLED_KEY, false).commit()
        prefs().edit().putBoolean(PrefsMigrations.LEGACY_CLIPBOARD_KEY, true).commit()
        assertTrue(PrefsMigrations.migrate(prefs()))
        assertEquals(false, prefs().getBoolean(PrefsMigrations.CLIPBOARD_ENABLED_KEY, true), "an existing decision wins")
        assertFalse(PrefsMigrations.migrate(prefs()), "nothing left to migrate")
    }
}
