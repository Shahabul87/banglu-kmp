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
        val result = gatedEngine().convertWord("rafsan")
        assertTrue(result.alternatives.all {
            it.bengali in listOf("আমি", "তুমি", "ভাত", "খাই")
        }, "non-dictionary alternative leaked: ${result.alternatives}")
    }

    @Test
    fun composingFallbackIsGatedForCompleteLookingWords() {
        val result = gatedEngine().convertForComposing("rafsan")
        assertEquals(CleanTransliterator.transliterate("rafsan"), result.bengali)
    }

    @Test
    fun seedWordsStayCommittableEvenWhenNotInValidator() {
        // "kotha" -> কথা is a seed entry (SeedData.kt, freq 80) that is NOT in the
        // tiny validator above; containsBengali(seed) must approve it.
        val result = gatedEngine().convertWord("kotha")
        assertEquals("কথা", result.bengali)
    }
}
