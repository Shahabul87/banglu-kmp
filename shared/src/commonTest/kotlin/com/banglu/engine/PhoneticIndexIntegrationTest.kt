package com.banglu.engine

import com.banglu.engine.platform.InMemoryPhoneticIndexStore
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.types.ResolutionSource
import com.banglu.engine.types.SmartDictionaryEntry
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
    fun twoCharKeysReachTheIndexOnCommit() {
        // F3 Bug 1: tryCorpusPhoneticLookup's 3-char floor kept 2-char COMMIT
        // keys away from the index (ob → index অব@74 never consulted).
        // Routing (seed-only engine + store): convertWord("ob") finds no seed
        // dict entry (dictionary.lookup("ob") is empty), "ob" is not detected
        // as English, so it reaches the direct corpus lookup, which now
        // accepts length-2 keys on commit (minKeyLength = 2).
        val e = SmartEngine()
        e.initializeSync()
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(
                entries = listOf(
                    PhoneticIndexHit("অব", 74, PhoneticIndexHit.TIER_A) to "ob"
                )
            )
        )
        val committed = e.convertWord("ob")
        assertEquals("অব", committed.bengali)
        // Source proves the index resolved it (pre-F3 the RULE layer happened
        // to emit অব too, at confidence 0.75).
        assertEquals(ResolutionSource.DICTIONARY, committed.source)

        // Composing keeps its pre-F3 output: the live preview never consults
        // the index for keys shorter than 4, so "ob" stays the pattern-layer
        // default. Captured BEFORE the F3 change: অব, RULE, confidence 0.75.
        val composing = e.convertForComposing("ob")
        assertEquals("অব", composing.bengali)
        assertEquals(ResolutionSource.RULE, composing.source)
    }

    @Test
    fun exactIndexKeyBeatsVariantPhoneticUnlessMuchRarer() {
        // F3 Bug 2: the index stores দুটিই@47 under the EXACT key "dutii",
        // but the extended dictionary lists "dutii" as a generated variant of
        // দুটি (canonical "duti"), and the dict-vs-corpus comparison ignored
        // exactness. Routing: convertByDictionary("dutii") returns দুটি (trie
        // hit via the variant phonetic), then the comparison sees an exact
        // store key whose typed input EXTENDS the dictionary word's canonical
        // phonetic (dutii = duti + i) — the exact corpus hit wins because the
        // validator is not loaded (dictFreq 0 clears no dominance bar).
        val e = SmartEngine()
        e.initializeSync()
        // Mimic the extended dictionary's variant family (seed alone has no
        // "dutii" phonetic; without this the input would bypass the dict-vs-
        // corpus comparison entirely and resolve via the direct corpus path).
        e.dictionary.addEntry(SmartDictionaryEntry("দুটি", listOf("duti", "dutii"), 76))
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(
                entries = listOf(
                    PhoneticIndexHit("দুটিই", 47, PhoneticIndexHit.TIER_A) to "dutii"
                )
            )
        )
        // Routing precondition: the dictionary side really proposes দুটি.
        assertEquals("দুটি", e.dictionary.lookup("dutii").firstOrNull()?.bengali)
        assertEquals("duti", e.dictionary.getPhoneticForBengali("দুটি"))
        assertEquals("দুটিই", e.convertWord("dutii").bengali)
    }

    @Test
    fun variantPhoneticWithStrongFrequencyAdvantageStillWins() {
        // F3 Bug 2 counter-case: a variant-matched dictionary word with a
        // clear frequency advantage (+15 absolute AND 2x relative) still
        // beats the exact index key. Routing: seed নদী lists "nodii" among
        // its phonetics (canonical "nodi", so typed "nodii" extends it);
        // the store has নদিই@10 under the exact key "nodii"; the validator
        // frequency seam gives নদী 40 — 40 > 10 + 15 and 40 > 10 * 2, so
        // the dictionary word dominates and stays primary.
        val e = SmartEngine()
        e.initializeSync()
        e.loadValidatorFrequencies(mapOf("নদী" to 40))
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(
                entries = listOf(
                    PhoneticIndexHit("নদিই", 10, PhoneticIndexHit.TIER_A) to "nodii"
                )
            )
        )
        // Routing precondition: dictionary proposes নদী via the variant "nodii".
        assertEquals("নদী", e.dictionary.lookup("nodii").firstOrNull()?.bengali)
        assertEquals("nodi", e.dictionary.getPhoneticForBengali("নদী"))
        assertEquals("নদী", e.convertWord("nodii").bengali)
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
