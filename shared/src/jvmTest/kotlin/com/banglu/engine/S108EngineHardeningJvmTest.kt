package com.banglu.engine

import com.banglu.engine.platform.ExtendedDictionaryHit
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.platform.PhoneticIndexStore
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S108 production-hardening pins:
 * 1. convertForInstantPreview is STRUCTURALLY zero-I/O — enforced with a
 *    store that throws on any access, not just a timing budget (the old
 *    S27 <1000µs assertion would not reliably catch a warm SQLite lookup).
 * 2. JvmSqlitePhoneticIndexStore refuses a wrong-version db (the desktop
 *    half of the cross-surface version gate) and NEVER throws out of a
 *    query — SmartEngine has no catch blocks by design.
 */
class S108EngineHardeningJvmTest {

    private class ThrowingStore : PhoneticIndexStore {
        private fun boom(): Nothing =
            throw AssertionError("instant preview must never touch the phonetic index store")

        override fun lookupExact(key: String): List<PhoneticIndexHit> = boom()
        override fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit> = boom()
        override fun lookupEnglish(key: String): String? = boom()
        override fun containsWord(bengali: String): Boolean = boom()
        // Probed ONCE at attach time by SmartDictionary.attachExtendedStore —
        // that's setup I/O off the keystroke path, so it is allowed here.
        override fun hasExtendedData(): Boolean = false
        override fun lookupExtendedExact(key: String): List<ExtendedDictionaryHit> = boom()
        override fun lookupExtendedPrefix(prefix: String, limit: Int): List<ExtendedDictionaryHit> = boom()
        override fun extendedPhoneticForBengali(bengali: String): String? = boom()
    }

    @Test
    fun instantPreviewIsStructurallyZeroIo() {
        val engine = SmartEngine()
        engine.initializeSync()
        engine.setPhoneticIndex(ThrowingStore())
        // Shorthand, plain words, compounds, single letters — every class the
        // preview path handles. Any store access throws AssertionError here.
        for (word in listOf("ami", "kmon", "hm", "bujteparcina", "korchi", "a", "shomoy", "kk")) {
            val preview = engine.convertForInstantPreview(word)
            assertTrue(preview.isNotEmpty(), "preview for '$word' should not be empty")
        }
    }

    @Test
    fun jvmStoreRejectsWrongVersionDb() {
        val tmp = kotlin.io.path.createTempFile("banglu-wrong-version", ".sqlite").toFile()
        tmp.delete()
        DriverManager.getConnection("jdbc:sqlite:${tmp.absolutePath}").use { c ->
            c.createStatement().use { st ->
                st.executeUpdate("CREATE TABLE metadata(key TEXT, value TEXT)")
                st.executeUpdate("CREATE TABLE phonetic_index(key TEXT)")
                st.executeUpdate("INSERT INTO metadata VALUES('version','0.0.1')")
            }
        }
        try {
            val store = JvmSqlitePhoneticIndexStore(tmp)
            assertFalse(store.isAvailable, "a wrong-version db must be rejected")
            // Degrade contract: unavailable store answers empty, never throws.
            assertTrue(store.lookupExact("ami").isEmpty())
            assertFalse(store.containsWord("আমি"))
            store.close()
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun jvmStoreAcceptsCurrentDbAndDegradesAfterClose() {
        val store = JvmSqlitePhoneticIndexStore(JvmSqliteDictionaryLoader.findDictionarySqlite())
        assertTrue(store.isAvailable, "repo dictionary.sqlite must satisfy DictionaryVersion.REQUIRED")
        assertTrue(store.lookupExact("ami").isNotEmpty())
        store.close()
        // A dead connection mid-session (the desktop crash class) degrades to
        // empty results instead of propagating SQLException into convertWord.
        assertTrue(store.lookupExact("ami").isEmpty())
    }
}
