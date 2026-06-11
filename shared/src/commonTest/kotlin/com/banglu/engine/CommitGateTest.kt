package com.banglu.engine

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
}
