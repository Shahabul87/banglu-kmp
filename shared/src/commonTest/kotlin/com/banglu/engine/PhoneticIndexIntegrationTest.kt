package com.banglu.engine

import com.banglu.engine.platform.InMemoryPhoneticIndexStore
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.types.ResolutionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneticIndexIntegrationTest {

    private fun engine(): SmartEngine {
        val e = SmartEngine()
        e.initializeSync()
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(
                entries = listOf(
                    // A word NOT in the seed dictionary, only in the index
                    PhoneticIndexHit("পরীক্ষাগার", 40, PhoneticIndexHit.TIER_A) to "porikkhagar",
                    PhoneticIndexHit("নদী", 80, PhoneticIndexHit.TIER_A) to "nodii"
                ),
                english = mapOf("smartwatch" to "স্মার্টওয়াচ")
            )
        )
        return e
    }

    @Test
    fun indexHitResolvesWordsBeyondSeedDictionary() {
        val result = engine().convertWord("porikkhagar")
        assertEquals("পরীক্ষাগার", result.bengali)
        assertEquals(ResolutionSource.DICTIONARY, result.source)
    }

    @Test
    fun queryNormalizationCollapsesLongVowelTyping() {
        // User types "nodee" for নদী; the index stores the canonical key "nodii"
        // plus the compiled ii→i collapsed alias "nodi". normalizeIndexQuery
        // collapses ee→i giving "nodi", which matches the alias.
        val e = SmartEngine()
        e.initializeSync()
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(
                entries = listOf(
                    PhoneticIndexHit("নদী", 80, PhoneticIndexHit.TIER_A) to "nodii",
                    PhoneticIndexHit("নদী", 80, PhoneticIndexHit.TIER_A) to "nodi"
                )
            )
        )
        assertEquals("নদী", e.convertWord("nodee").bengali)
    }

    @Test
    fun englishLexiconBeatsPatternMangling() {
        val result = engine().convertWord("smartwatch")
        assertEquals("স্মার্টওয়াচ", result.bengali)
        assertEquals(ResolutionSource.ENGLISH_LEXICON, result.source)
        assertEquals("smartwatch", result.alternatives.first().bengali)
    }

    @Test
    fun suggestionsIncludeStoreOnlyCorpusWords() {
        // Store mode: the runtime corpusPhoneticIndex is never built, so the
        // corpus suggestion tier must read the store (corpusWordsFor), or it
        // goes dark. নদি exists ONLY in the store; frequency 3 keeps it below
        // the dict-vs-corpus +5 margin so নদী (seed, exact match) stays
        // primary and নদি can only surface through the corpus tier.
        val e = SmartEngine()
        e.initializeSync()
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(
                entries = listOf(
                    PhoneticIndexHit("নদি", 3, PhoneticIndexHit.TIER_A) to "nodi"
                )
            )
        )
        assertEquals("নদী", e.convertWord("nodi").bengali)
        val suggestions = e.getSuggestions("nodi", 25)
        assertTrue(
            suggestions.any { it.bengali == "নদি" },
            "store-only corpus word নদি missing from suggestions: " +
                suggestions.map { it.bengali }
        )
    }

    @Test
    fun storeFrequencyOutranksSeedDictionaryWord() {
        // "ami" passes through convertByDictionary (আমি, exact seed entry,
        // frequency 100 → confidence 1.0, so the confidence < 0.90 escape
        // hatch is closed) AND tryCorpusPhoneticLookup. The 480K validator is
        // not loaded in tests, so validator.getFrequency is 0 for both words;
        // only the store-hit frequency can satisfy corpusFreq > dictFreq + 5.
        val e = SmartEngine()
        e.initializeSync()
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(
                entries = listOf(
                    PhoneticIndexHit("অমি", 5000, PhoneticIndexHit.TIER_A) to "ami"
                )
            )
        )
        assertEquals("অমি", e.convertWord("ami").bengali)
    }

    @Test
    fun ooToUNormalizationReachesStoreEntries() {
        // The store has keys "puja" and "puuja" but no "pooja". The query-side
        // collapse oo→u (normalizeIndexQuery) gives "puja", which matches the
        // store; storeFrequencyOf mirrors the same fallback so the hit keeps
        // its real frequency and outranks the seed word পূজা.
        val e = SmartEngine()
        e.initializeSync()
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(
                entries = listOf(
                    PhoneticIndexHit("পুজো", 5000, PhoneticIndexHit.TIER_A) to "puja",
                    PhoneticIndexHit("পুজো", 5000, PhoneticIndexHit.TIER_A) to "puuja"
                )
            )
        )
        assertEquals("পুজো", e.convertWord("pooja").bengali)
    }

    @Test
    fun curatedEnglishStillWinsOverGeneratedLexicon() {
        // "practice" is curated (EnglishPronunciationVariantData -> প্রাকটিস).
        val e = SmartEngine()
        e.initializeSync()
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(emptyList(), english = mapOf("practice" to "প্র্যাকটিস"))
        )
        assertEquals("প্রাকটিস", e.convertWord("practice").bengali)
    }
}
