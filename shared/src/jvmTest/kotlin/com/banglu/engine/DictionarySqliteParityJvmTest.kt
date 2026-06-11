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

        assertTrue(
            bigram.unigrams.size >= 80_000,
            "Expected at least 80K unigram entries, got ${bigram.unigrams.size}"
        )
        assertTrue(
            bigram.bigrams.size >= 25_000,
            "Expected at least 25K bigram entries, got ${bigram.bigrams.size}"
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
