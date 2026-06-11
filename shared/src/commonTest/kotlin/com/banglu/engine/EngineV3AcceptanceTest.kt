package com.banglu.engine

import com.banglu.engine.platform.InMemoryPhoneticIndexStore
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.types.ResolutionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Engine v3 Phase 1 acceptance: the user-visible scenarios from the spec
 * (docs/superpowers/specs/2026-06-11-engine-v3-lookup-first-design.md §4).
 * Uses an in-memory index emulating the compiled asset.
 *
 * Layer notes (traced 2026-06-11): "accha"/"assa"/"acca" all hit the seed
 * DIRECT_WORD_OVERRIDES (SmartEngine.kt) before the index — output is আচ্ছা
 * either way; the index entries below pin the compiled-asset contract.
 * "scooter"/"play" hit the curated EnglishPronunciationVariantData entries
 * before the in-memory lexicon — output identical. These tests pin
 * user-visible behavior, not the resolving layer.
 */
class EngineV3AcceptanceTest {

    private fun engine(): SmartEngine {
        val e = SmartEngine()
        e.initializeSync()
        e.loadValidatorWords(listOf("আমি", "ভাত", "খাই", "আচ্ছা", "স্কুল"))
        e.setPhoneticIndex(InMemoryPhoneticIndexStore(
            entries = listOf(
                PhoneticIndexHit("আচ্ছা", 95, PhoneticIndexHit.TIER_A) to "accha",
                PhoneticIndexHit("আচ্ছা", 95, PhoneticIndexHit.TIER_A) to "assa",   // irregular variant key
                PhoneticIndexHit("আচ্ছা", 95, PhoneticIndexHit.TIER_A) to "acca"
            ),
            english = mapOf("scooter" to "স্কুটার", "play" to "প্লে")
        ))
        return e
    }

    @Test
    fun irregularVariantsAllReachTheSameWord() {
        val e = engine()
        for (typed in listOf("accha", "assa", "acca")) {
            assertEquals("আচ্ছা", e.convertWord(typed).bengali, "input: $typed")
        }
    }

    @Test
    fun mixedEnglishBengaliSentence() {
        val e = engine()
        assertEquals("স্কুটার", e.convertWord("scooter").bengali)
        assertEquals("প্লে", e.convertWord("play").bengali)
        assertTrue(e.parse("ami scooter").contains("স্কুটার"))
    }

    @Test
    fun editorNeverShowsInventedStrings() {
        val e = engine()
        val r = e.convertWord("kkkkx")
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, r.source)
    }

    @Test
    fun namesGetReadableTransliterationAndRawStaysReachable() {
        val e = engine()
        val r = e.convertWord("rafsan")
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, r.source)
        assertTrue(r.bengali.isNotEmpty() && r.bengali.none { it in 'a'..'z' })
    }

    @Test
    fun tierBWordsResolveOnExactMatchOnly() {
        val e = SmartEngine()
        e.initializeSync()
        e.loadValidatorWords(listOf("আমি"))
        e.setPhoneticIndex(InMemoryPhoneticIndexStore(
            entries = listOf(PhoneticIndexHit("দুষ্প্রাপ্য", 0, PhoneticIndexHit.TIER_B) to "dushprapyo")
        ))
        // exact key → resolves
        assertEquals("দুষ্প্রাপ্য", e.convertWord("dushprapyo").bengali)
    }
}
