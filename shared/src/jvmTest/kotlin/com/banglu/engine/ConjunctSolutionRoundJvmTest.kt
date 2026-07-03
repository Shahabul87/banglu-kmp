package com.banglu.engine

import com.banglu.engine.platform.InMemoryPhoneticIndexStore
import com.banglu.engine.platform.PhoneticIndexHit
import kotlinx.coroutines.runBlocking
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S6/S7 solution-round acceptance tests (study: docs/engine-conjunct-study-2026-07-03.md).
 *
 * S6 — store-first arbitration: a canonical (priority-0, tier-A) exact store hit
 *      beats the seed layer when its usage is at least the seed word's (W1: the
 *      তৈরী/ট্রি/খন্ড class).
 * S7 — composing continuation preference: an alias-only exact hit (priority 1)
 *      must not become primary when a canonical continuation of the typed key
 *      is at least as common (W3: brit → বৃত্ত mid-word ri-kar flash).
 */
class ConjunctSolutionRoundJvmTest {

    companion object {
        val engine: SmartEngine by lazy {
            SmartEngine().also { eng ->
                eng.initializeSync()
                runBlocking { eng.initialize(storage = null, loader = TestDictionaryLoader()) }
                eng.setPhoneticIndex(loadStore())
            }
        }

        private fun loadStore(): InMemoryPhoneticIndexStore {
            val dbFile = TestDictionaryLoader.findDictionarySqlite()
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                val entries = ArrayList<Pair<PhoneticIndexHit, String>>(1_100_000)
                conn.createStatement().use { st ->
                    st.executeQuery(
                        """SELECT p.key, w.bengali, p.frequency, p.tier, p.priority
                           FROM phonetic_index p JOIN words w ON w.id = p.word_id"""
                    ).use { rs ->
                        while (rs.next()) {
                            entries.add(
                                PhoneticIndexHit(
                                    rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)
                                ) to rs.getString(1)
                            )
                        }
                    }
                }
                val english = HashMap<String, String>()
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT key, bengali FROM english_lexicon").use { rs ->
                        while (rs.next()) english[rs.getString(1)] = rs.getString(2)
                    }
                }
                val words = HashSet<String>(600_000)
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT bengali FROM words").use { rs ->
                        while (rs.next()) words.add(rs.getString(1))
                    }
                }
                return InMemoryPhoneticIndexStore(entries, english, words)
            }
        }
    }

    private fun primary(input: String): String = engine.convertWord(input).bengali

    // ── S6: store canonical-first beats seed layer ──────────────────────

    @Test
    fun s6_toiri_prefersModernSpelling() = assertEquals("তৈরি", primary("toiri"))

    @Test
    fun s6_tri_prefersBengaliConjunctOverLoanword() = assertEquals("ত্রি", primary("tri"))

    @Test
    fun s6_khond_prefersMurdhonyoSpelling() = assertEquals("খণ্ড", primary("khond"))

    // ── S7: alias exact hit must not flash over canonical continuations ──

    @Test
    fun s7_brit_neverShowsRiKarPrimary() {
        val p = primary("brit")
        assertTrue('ৃ' !in p, "brit primary must not be a ri-kar word, got $p")
    }

    @Test
    fun s7_brit_stripLeadsWithContinuationNotRiKar() {
        val strip = engine.getSuggestions("brit", 6).map { it.bengali }
        assertTrue('ৃ' !in strip.first(), "brit strip #1 must not be ৃ word, got $strip")
        assertTrue(strip.any { it == "ব্রিটিশ" }, "brit strip should offer ব্রিটিশ, got $strip")
    }

    // ── Regressions: alias commits and canonical wins must survive ──────

    @Test
    fun regression_kori() = assertEquals("করি", primary("kori"))

    @Test
    fun regression_sastho() = assertEquals("স্বাস্থ্য", primary("sastho"))

    @Test
    fun regression_jonno() = assertEquals("জন্য", primary("jonno"))

    @Test
    fun regression_utor() = assertEquals("উত্তর", primary("utor"))

    @Test
    fun regression_biswas() = assertEquals("বিশ্বাস", primary("bishash"))
}
