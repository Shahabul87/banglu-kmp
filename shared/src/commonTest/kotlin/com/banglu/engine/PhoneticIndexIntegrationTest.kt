package com.banglu.engine

import com.banglu.engine.platform.InMemoryPhoneticIndexStore
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.types.ResolutionSource
import kotlin.test.Test
import kotlin.test.assertEquals

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
