package com.banglu.engine

import com.banglu.engine.platform.InMemoryPhoneticIndexStore
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.rules.CleanTransliterator
import com.banglu.engine.types.ResolutionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommitGateTest {

    /** Engine with a tiny "480k" validator so the gate is armed. */
    private fun gatedEngine(): SmartEngine {
        val e = SmartEngine()
        e.initializeSync()
        e.loadValidatorWords(listOf("আমি", "তুমি", "ভাত", "খাই"))
        return e
    }

    @Test
    fun validWordsPassTheGateUntouched() {
        val result = gatedEngine().convertWord("ami")
        assertEquals("আমি", result.bengali)
    }

    @Test
    fun oovInputCommitsCleanTransliterationNeverPatternGuess() {
        val result = gatedEngine().convertWord("rafsan")
        assertEquals(CleanTransliterator.transliterate("rafsan"), result.bengali)
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, result.source)
    }

    @Test
    fun gateAlternativesAreDictionaryClosed() {
        val e = gatedEngine()
        val result = e.convertWord("rafsan")
        assertTrue(result.alternatives.all {
            e.isGateApprovedForTest(it.bengali)
        }, "non-gate-approved alternative leaked: ${result.alternatives}")
    }

    @Test
    fun composingFallbackIsGatedForCompleteLookingWords() {
        val result = gatedEngine().convertForComposing("rafsan")
        assertEquals(CleanTransliterator.transliterate("rafsan"), result.bengali)
    }

    @Test
    fun gateStaysDisarmedWithoutValidator() {
        val e = SmartEngine()
        e.initializeSync()  // seed only — validator never loaded
        val result = e.convertWord("rafsan")
        assertTrue(result.source != ResolutionSource.CLEAN_TRANSLITERATION,
            "gate fired in legacy mode: ${result.source}")
    }

    @Test
    fun patternTailOutputInValidatorPassesGate() {
        val e = SmartEngine()
        e.initializeSync()
        // Both branches (validator-approval and gate-replacement) converge on the
        // floor string here: loading `floor` into the validator ensures the
        // validator.isValid branch approves it directly, while the gate-replacement
        // branch would also produce the same clean-transliteration floor value.
        val floor = CleanTransliterator.transliterate("rafsan")
        e.loadValidatorWords(listOf(floor))
        val result = e.convertWord("rafsan")
        assertTrue(e.isGateApprovedForTest(result.bengali),
            "editor primary '${result.bengali}' is not gate-approved")
    }

    @Test
    fun seedWordsStayCommittableEvenWhenNotInValidator() {
        // "kotha" -> কথা is a seed entry (SeedData.kt, freq 80) that is NOT in the
        // tiny validator above; containsBengali(seed) must approve it.
        val result = gatedEngine().convertWord("kotha")
        assertEquals("কথা", result.bengali)
    }

    @Test
    fun composedSuffixOutputsCannotEscapeTheGate() {
        // Fixture reasoning: "rafsantar" ends with the productive suffix "tar";
        // its stem "rafsan" is in no dictionary, so tryProductiveSuffixConversion
        // composes stem-from-patterns + টার — an invented string. Routing proof:
        // with the gate DISARMED (seed only) the composition layer returns it.
        val ungated = SmartEngine()
        ungated.initializeSync()
        val composed = ungated.convertWord("rafsantar")
        assertEquals("রাফসানটার", composed.bengali, "fixture no longer routes through the composition layer")
        assertEquals(ResolutionSource.RULE, composed.source)

        // Armed gate: validator holds real words plus the clean floor, but neither
        // the bogus composition রাফসানটার nor its stem রাফসান. The composition
        // allowance must NOT apply (stem is not a real word) — primary floors.
        val e = SmartEngine()
        e.initializeSync()
        val floor = CleanTransliterator.transliterate("rafsantar")
        e.loadValidatorWords(listOf("আমি", "তুমি", floor))
        val result = e.convertWord("rafsantar")
        assertEquals(floor, result.bengali, "invented composition escaped the commit gate")
        assertTrue(e.isGateApprovedForTest(result.bengali),
            "editor primary '${result.bengali}' is not gate-approved")
    }

    @Test
    fun gateArmsInLiteModeViaStore() {
        // F5: lite mode = validator never loaded, sqlite store attached. The
        // gate must arm off the store's words table: ungated pattern garbage
        // (kkkkx) floors to the clean transliteration, while a store word
        // reached through the index passes untouched.
        val e = SmartEngine()
        e.initializeSync()  // validator never loaded — lite mode
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(
                entries = listOf(
                    PhoneticIndexHit("আমি", 100, PhoneticIndexHit.TIER_A) to "ami"
                ),
                words = setOf("আমি")
            )
        )
        val garbage = e.convertWord("kkkkx")
        assertEquals(CleanTransliterator.transliterate("kkkkx"), garbage.bengali,
            "lite-mode gate did not floor pattern garbage")
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, garbage.source)

        val known = e.convertWord("ami")
        assertEquals("আমি", known.bengali, "store word blocked by the lite-mode gate")
    }

    @Test
    fun storeWordsApprovedAsGatePrimary() {
        // A word that exists ONLY in the store (not seed, not validator) must
        // resolve via the index AND be gate-approved as the editor primary.
        val e = SmartEngine()
        e.initializeSync()  // validator never loaded — lite mode
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(
                entries = listOf(
                    PhoneticIndexHit("পরীক্ষাগার", 40, PhoneticIndexHit.TIER_A) to "porikkhagar"
                )
            )
        )
        val result = e.convertWord("porikkhagar")
        assertEquals("পরীক্ষাগার", result.bengali)
        assertTrue(e.isGateApprovedForTest(result.bengali),
            "store-only word 'পরীক্ষাগার' is not gate-approved in lite mode")
    }

    @Test
    fun legitimateInflectionsSurviveTheGate() {
        // Fixture reasoning: "kothata" = seed root "kotha" (কথা) + productive "ta"
        // (টা); convertByRootDecomposition case 1 composes কথাটা.

        // (a) Inflected form itself in the validator -> plain gate approval.
        val e = SmartEngine()
        e.initializeSync()
        e.loadValidatorWords(listOf("আমি", "কথা", "কথাটা"))
        val result = e.convertWord("kothata")
        assertEquals("কথাটা", result.bengali)
        assertTrue(e.isGateApprovedForTest(result.bengali))

        // (b) Inflected form NOT in the validator (ট্রাম্পের-class, corpus-verified):
        // a real root + whitelisted productive suffix is an approved composition
        // and must not be floored to the clean transliteration.
        val e2 = SmartEngine()
        e2.initializeSync()
        e2.loadValidatorWords(listOf("আমি", "কথা"))  // root only
        val result2 = e2.convertWord("kothata")
        assertEquals("কথাটা", result2.bengali,
            "legitimate root+suffix inflection was wrongly floored by the gate")
    }
}
