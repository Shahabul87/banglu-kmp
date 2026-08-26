package com.banglu.keyboard

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S136 (production re-audit, F-001/F-006): the erase path as the settings
 * screen really invokes it — a ContentResolver call into
 * [BangluPrefsProvider] hosted by the keyboard process — verified on a real
 * device against the real SharedPreferences files. Seeds every learning
 * category (active scope AND a legacy unscoped key AND a foreign scope) and
 * asserts the provider reports success only after all of them are gone.
 */
@RunWith(AndroidJUnit4::class)
class S136EraseProviderInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        learningPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        learningPrefs().edit().clear().commit()
    }

    private fun learningPrefs() = context.getSharedPreferences("banglu_learning", Context.MODE_PRIVATE)

    private fun learningKeys(): Set<String> = learningPrefs().all.keys
        .filterNot { it == "dict_version" }
        .toSet()

    private fun seedEverything() = runBlocking {
        val storage = AndroidStorage(context)
        storage.saveLearnedWord("ami", "আমিই", 94)
        storage.saveUserBigram("আমি", "ভালো", 2)
        storage.saveEnglishUserData("w\tallah\t2\n")
        storage.saveIdentityUserData("e\trahim@gmail.com\nd\tgmail.com\n")
        storage.saveCustomConversion("kmn", "কেমন")
        // Legacy unscoped spelling and a foreign user scope — both must die too.
        learningPrefs().edit()
            .putString("learned_words", "old::পুরনো::1::1")
            .putString("identity_user_data_user_123", "e\tother@example.com\n")
            .putString("dict_version", "keep-me")
            .commit()
        assertTrue(learningKeys().size >= 7, "seeded keys: ${learningKeys()}")
    }

    @Test
    fun eraseAllRemovesEveryLearningKeyAndKeepsTheDictionaryVersion() {
        seedEverything()
        val ok = eraseLearningInKeyboardProcess(context, BangluPrefsProvider.ERASE_SCOPE_ALL)
        assertTrue(ok, "provider must confirm the durable delete")
        assertEquals(emptySet(), learningKeys(), "no learning key of any scope may survive")
        assertEquals("keep-me", learningPrefs().getString("dict_version", null))
        assertTrue(
            context.getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE)
                .getLong(BangluPrefsProvider.KEY_LEARNING_ERASED_AT, 0L) > 0L,
            "the IME rebuild stamp is written"
        )
    }

    @Test
    fun eraseIdentityRemovesOnlyIdentities() {
        seedEverything()
        val ok = eraseLearningInKeyboardProcess(context, BangluPrefsProvider.ERASE_SCOPE_IDENTITY)
        assertTrue(ok)
        val remaining = learningKeys()
        assertTrue(remaining.none { it.startsWith("identity_user_data") }, "identities gone: $remaining")
        assertTrue(remaining.any { it.startsWith("learned_words") }, "learned words untouched: $remaining")
    }

    @Test
    fun unknownScopeIsRefusedAndDeletesNothing() {
        seedEverything()
        val before = learningKeys()
        assertFalse(eraseLearningInKeyboardProcess(context, "everything-please"))
        assertEquals(before, learningKeys())
    }
}
