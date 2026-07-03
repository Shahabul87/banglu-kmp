package com.banglu.engine

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DictionarySqliteParityJvmTest {

    @Test
    fun sqliteContainsAndroidParityTables() = runBlocking {
        val loader = TestDictionaryLoader()

        val extended = assertNotNull(
            loader.loadExtendedDictionary(),
            "dictionary.sqlite must include extended phonetic dictionary tables"
        )
        val bigram = assertNotNull(
            loader.loadBigramModel(),
            "dictionary.sqlite must include bigram model tables"
        )

        assertTrue(
            extended.size >= 130_000,
            "Expected at least 130K extended entries, got ${extended.size}"
        )
        assertTrue(
            extended.any { it.bengali == "হবে" && "hobe" in it.phonetics },
            "Extended dictionary should include hobe -> হবে"
        )
        assertTrue(
            extended.any { it.bengali == "কবে" && "kobe" in it.phonetics },
            "Extended dictionary should include kobe -> কবে"
        )

        // S9 corpus bigram model (db 3.6.0): unigrams are dictionary-filtered
        // real-usage words (count >= 10), pairs are observed web-text bigrams
        // (weighted count >= 24) — fewer but cleaner unigrams than the legacy
        // 83k table, and ~4x its pairs.
        assertTrue(
            bigram.unigrams.size >= 40_000,
            "Expected at least 40K unigram entries, got ${bigram.unigrams.size}"
        )
        assertTrue(
            bigram.bigrams.size >= 90_000,
            "Expected at least 90K bigram entries, got ${bigram.bigrams.size}"
        )
        assertTrue(
            bigram.bigrams.keys.any { '\t' in it },
            "Bigram keys must use the shared engine format: previous\\tnext"
        )
        assertTrue(
            bigram.totalUnigrams > 0 && bigram.totalBigrams > 0,
            "Bigram totals should be populated from SQLite metadata"
        )
    }
}
