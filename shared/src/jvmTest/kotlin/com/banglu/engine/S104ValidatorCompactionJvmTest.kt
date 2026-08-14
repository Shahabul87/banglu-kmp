package com.banglu.engine

import com.banglu.engine.dictionary.BengaliWordValidator
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S104: the validator's 476K-string storage became a 1-byte-per-char blob
 * (~23MB -> ~6MB). These tests prove the swap is BEHAVIOR-IDENTICAL on the
 * real compiled word list — every membership answer, every frequency, the
 * prefix scans, and the full sorted view — and gate that the compiled db
 * never silently falls back to legacy storage (which would mean a character
 * escaped the encoding table).
 */
class S104ValidatorCompactionJvmTest {

    private companion object {
        val words: List<String> by lazy {
            val db = TestDictionaryLoader.findDictionarySqlite()
            DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT bengali FROM words").use { rs ->
                        buildList { while (rs.next()) add(rs.getString(1)) }
                    }
                }
            }
        }

        val freqs: Map<String, Int> by lazy {
            val db = TestDictionaryLoader.findDictionarySqlite()
            DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT bengali, frequency FROM words WHERE frequency > 1").use { rs ->
                        buildMap { while (rs.next()) put(rs.getString(1), rs.getInt(2)) }
                    }
                }
            }
        }

        val compactValidator: BengaliWordValidator by lazy {
            BengaliWordValidator().also { it.loadWords(words); it.loadFrequencies(freqs) }
        }
    }

    @Test
    fun compiledDbEngagesCompactStorage() {
        // If this fails, a character escaped the encoding table and the
        // validator silently fell back to legacy strings — correct but big.
        assertTrue(compactValidator.isCompactStorage(), "compact storage must engage on the real db")
    }

    @Test
    fun everyWordIsValidAndMutationsAreNot() {
        val v = compactValidator
        var checked = 0
        for (w in words) {
            assertTrue(v.isValid(w), "member word must validate: $w")
            checked++
        }
        assertEquals(words.size, checked)
        // Negative probes: append a letter — overwhelmingly absent; verify
        // answers match a plain HashSet oracle exactly either way.
        val oracle = HashSet<String>().also { s ->
            for (w in words) s.add(com.banglu.engine.util.ReverseTransliterator.foldNukta(w))
        }
        var negChecked = 0
        for (i in words.indices step 37) {
            val probe = words[i] + "ঘ"
            assertEquals(
                com.banglu.engine.util.ReverseTransliterator.foldNukta(probe) in oracle,
                v.isValid(probe),
                "negative probe must match oracle: $probe"
            )
            negChecked++
        }
        assertTrue(negChecked > 10_000)
    }

    @Test
    fun everyFrequencyMatchesTheSource() {
        val v = compactValidator
        for ((w, f) in freqs) {
            assertEquals(f, v.getFrequency(w), "frequency mismatch for $w")
        }
    }

    @Test
    fun sortedViewIsFullyOrderedAndComplete() {
        val v = compactValidator
        val view = v.getSortedWords()
        assertEquals(v.getSize(), view.size)
        var prev: String? = null
        for (i in 0 until view.size) {
            val w = view[i]
            if (prev != null) assertTrue(prev < w, "order violation at $i: $prev !< $w")
            prev = w
        }
    }

    @Test
    fun prefixScansMatchALinearOracle() {
        val v = compactValidator
        val view = v.getSortedWords()
        val prefixes = listOf("অ", "আম", "কর", "বি", "মা", "শ্র", "ত্ব", "দেখ", "অস্তিত্ব", "নাই-এমন-প্রিফিক্স")
        for (p in prefixes) {
            val folded = com.banglu.engine.util.ReverseTransliterator.foldNukta(p)
            val expected = buildList {
                for (i in 0 until view.size) {
                    val w = view[i]
                    if (w.startsWith(folded)) { add(w); if (size == 10) break }
                }
            }
            assertEquals(expected, v.findByPrefix(p, 10), "prefix scan mismatch for '$p'")
        }
    }
}
