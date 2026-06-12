package com.banglu.engine

import com.banglu.engine.platform.InMemoryPhoneticIndexStore
import com.banglu.engine.platform.PhoneticIndexHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S1 strip-integrity invariants:
 *
 * D1 — the Bengali of convertWord(key) (the editor primary) must ALWAYS survive
 * to the final suggestion list at a rank no worse than 3. It is gate-approved
 * by definition; the cleanliness/phonetic-fit filters must never evict it
 * (pre-fix, 81% of primary-correct append-o keys lost their primary to the
 * reverse-phonetic length rule in hasSuggestionPhoneticFit).
 *
 * D2 — a candidate that is NOT a dictionary word (validator/store containsWord,
 * seed/learned containsBengali) must never outrank a candidate that IS one.
 * Generated variants may fill remaining slots only. The primary and English
 * passthrough/lexicon entries are exempt legitimate non-dictionary entries.
 */
class SuggestionStripIntegrityTest {

    private fun engineWithStore(vararg entries: Pair<PhoneticIndexHit, String>): SmartEngine {
        val e = SmartEngine()
        e.initializeSync()
        e.setPhoneticIndex(InMemoryPhoneticIndexStore(entries = entries.toList()))
        return e
    }

    @Test
    fun primarySurvivesAppendOKeyWithinTopThree() {
        val e = engineWithStore(
            PhoneticIndexHit("বাংলাদেশ", 90, PhoneticIndexHit.TIER_A) to "bangladesh"
        )
        val primary = e.convertWord("bangladesho").bengali
        assertEquals("বাংলাদেশ", primary, "fixture: primary conversion of bangladesho")

        val strip = e.getSuggestions("bangladesho", 10)
        val rank = strip.indexOfFirst { it.bengali == "বাংলাদেশ" }
        assertTrue(
            rank in 0..2,
            "primary বাংলাদেশ must survive to the strip at rank <= 3, got " +
                "${if (rank < 0) "MISSING" else "rank ${rank + 1}"}: ${strip.map { it.bengali }}"
        )
    }

    @Test
    fun primarySurvivalHoldsAcrossAppendOKeys() {
        // Same invariant for further H7-class keys resolvable from the seed
        // dictionary alone (no store needed): primary correct -> rank <= 3.
        val e = SmartEngine()
        e.initializeSync()
        for (key in listOf("oneko", "prodhano", "tadero")) {
            val primary = e.convertWord(key).bengali
            val strip = e.getSuggestions(key, 10)
            val rank = strip.indexOfFirst { it.bengali == primary }
            assertTrue(
                rank in 0..2,
                "key=$key primary=$primary must hold strip rank <= 3, got " +
                    "${if (rank < 0) "MISSING" else "rank ${rank + 1}"}: ${strip.map { it.bengali }}"
            )
        }
    }

    @Test
    fun realStoreWordRanksAboveGeneratedVariant() {
        // প্রধান is a real word (store + seed); the strip also generates
        // dictionary-absent variants for prodhano (ঢ-swaps such as প্রঢান from
        // the ambiguous-char generator). The real word must outrank them all.
        val e = engineWithStore(
            PhoneticIndexHit("প্রধান", 90, PhoneticIndexHit.TIER_A) to "prodhan"
        )
        val strip = e.getSuggestions("prodhano", 10)

        val realRank = strip.indexOfFirst { it.bengali == "প্রধান" }
        assertTrue(
            realRank in 0..2,
            "real word প্রধান must be in the strip top 3: ${strip.map { it.bengali }}"
        )

        val generatedRank = strip.indexOfFirst { it.bengali == "প্রঢান" }
        assertTrue(
            generatedRank != 0,
            "generated non-word প্রঢান must never hold strip rank 1: ${strip.map { it.bengali }}"
        )
        if (generatedRank >= 0) {
            assertTrue(
                realRank < generatedRank,
                "real word প্রধান (rank ${realRank + 1}) must outrank generated " +
                    "প্রঢান (rank ${generatedRank + 1}): ${strip.map { it.bengali }}"
            )
        }
    }

    @Test
    fun noGeneratedCandidateOutranksAnyDictionaryWord() {
        // D2 tier law over the whole strip: excluding the exempt slots (the
        // primary itself and English passthrough), every dictionary word must
        // rank above every non-dictionary (generated) candidate.
        val e = engineWithStore(
            PhoneticIndexHit("প্রধান", 90, PhoneticIndexHit.TIER_A) to "prodhan"
        )
        val primary = e.convertWord("prodhano").bengali
        val strip = e.getSuggestions("prodhano", 10)

        var firstGenerated = -1
        strip.forEachIndexed { index, s ->
            val exempt = s.bengali == primary || s.source == "english_passthrough"
            val isDictionaryWord = e.isGateApprovedForTest(s.bengali)
            if (!exempt && !isDictionaryWord && firstGenerated < 0) firstGenerated = index
            if (isDictionaryWord && firstGenerated in 0 until index) {
                throw AssertionError(
                    "dictionary word ${s.bengali} (rank ${index + 1}) ranked below generated " +
                        "${strip[firstGenerated].bengali} (rank ${firstGenerated + 1}): " +
                        strip.map { it.bengali }
                )
            }
        }
    }
}
