package com.banglu.engine

import com.banglu.engine.platform.InMemoryStorage
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * S165 — handwritten tester note (2026-09-01), two reports:
 * (1) hoi/hoy family: hoyce/hoyche must reach হয়েছে (they were owned by
 *     হয়ছে, not a standard word, with হয়েছে nowhere on the strip); the
 *     final-ই/য় homograph twin sits BESIDE the primary (slot 1), not
 *     buried at the end of the strip.
 * (2) pyra: an explicit divergent pick becomes the device's primary on the
 *     next conversion; picking the primary back clears the preference
 *     (the S26/S78 law, pinned end-to-end through the adapter).
 */
class S165TesterRoundJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @AfterTest
    fun tearDown() {
        SmartEngineAdapter.reset()
    }

    @Test
    fun yeDroppedChatSpellingsReachHoyeche() {
        for (k in listOf("hoyce", "hoyche")) {
            assertEquals("হয়েছে", engine.convertWord(k).bengali, "convert $k")
            assertEquals("হয়েছে", engine.convertForComposing(k).bengali, "preview $k")
            // The displaced old owner stays reachable (nukta-folded compare —
            // the store serves য় decomposed, the S86 lesson).
            val fold = com.banglu.engine.util.ReverseTransliterator::foldNukta
            assertTrue(
                engine.getSuggestions(k, 6).any { fold(it.bengali) == fold("হয়ছে") },
                "হয়ছে keeps a strip slot on $k — got ${engine.getSuggestions(k, 6).map { it.bengali }}"
            )
        }
        // The dialect-chat spellings keep their deliberate dialect rendering.
        assertEquals("হইছে", engine.convertWord("hoiche").bengali)
        assertEquals("হইছে", engine.convertWord("hoise").bengali)
    }

    @Test
    fun homographTwinSitsBesideThePrimary() {
        for ((key, twin) in listOf("hoi" to "হয়", "hoy" to "হই", "jai" to "যায়")) {
            val strip = engine.getSuggestions(key, 6)
            val idx = strip.indexOfFirst { it.bengali == twin }
            assertTrue(idx in 0..1, "$key: twin $twin at slot $idx, want 0-1 — strip=${strip.map { it.bengali }}")
        }
    }

    @Test
    fun explicitDivergentPickBecomesNextPrimary() = runBlocking {
        SmartEngineAdapter.setPhoneticIndex(ConjunctSolutionRoundJvmTest.store)
        SmartEngineAdapter.initialize(InMemoryStorage(), TestDictionaryLoader())

        val before = SmartEngineAdapter.convertWord("pyra").bengali
        assertEquals("প্যারা", before) // the tester's word already resolves right

        // Divergent pick: choose a non-primary from the strip.
        val divergent = SmartEngineAdapter.getSuggestions("pyra", 6)
            .map { it.bengali }.first { it != before }
        SmartEngineAdapter.onWordSelected("pyra", divergent, false, true)
        assertEquals(divergent, SmartEngineAdapter.convertWord("pyra").bengali,
            "the picked word is the device's primary next time")

        // Picking the (original) primary back clears the preference (S78).
        SmartEngineAdapter.onWordSelected("pyra", before, false, true)
        assertEquals(before, SmartEngineAdapter.convertWord("pyra").bengali)
    }

    @Test
    fun pickingCurrentPrimaryRecordsNothing() = runBlocking {
        SmartEngineAdapter.setPhoneticIndex(ConjunctSolutionRoundJvmTest.store)
        val storage = InMemoryStorage()
        SmartEngineAdapter.initialize(storage, TestDictionaryLoader())

        val primary = SmartEngineAdapter.convertWord("pyra").bengali
        SmartEngineAdapter.onWordSelected("pyra", primary, false, true)
        // S26: accepting the engine's own primary must not freeze the ranking.
        assertTrue(
            storage.getLearnedWords().none { it.phonetic == "pyra" },
            "primary-pick must not persist a preference row"
        )
    }
}
