package com.banglu.engine

import com.banglu.engine.dictionary.ACRONYM_OVERRIDES
import com.banglu.engine.dictionary.ACRONYM_SUGGESTIONS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S52 Task 1 — curated acronym layer regression wall.
 * Evidence: `.superpowers/sdd/probe-english-acronyms.md`,
 * `.superpowers/sdd/explore-loanword-machinery.md`,
 * `.superpowers/sdd/research-banglish-web.md`,
 * `.superpowers/sdd/s52-task-1-report.md` (tier decisions + per-key evidence).
 */
class S52EnglishAcronymJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun tierPKeysConvertToMappedAcronym() {
        for ((key, mapped) in ACRONYM_OVERRIDES) {
            assertEquals(mapped, engine.convertWord(key).bengali, "Tier P key '$key' should commit to '$mapped'")
        }
    }

    @Test
    fun tierPWysiwygInstantPreviewMatchesCommit() {
        // 8 sampled Tier P keys spanning garbage-fix, collision-fix, lexicon-bug-fix,
        // and hardening categories.
        val sample = listOf("ssc", "otp", "nid", "mr", "gps", "phd", "wifi", "vvip")
        for (key in sample) {
            val mapped = ACRONYM_OVERRIDES.getValue(key)
            assertEquals(mapped, engine.convertForInstantPreview(key), "Instant preview for '$key' must match commit (WYSIWYG)")
        }
    }

    @Test
    fun tierSKeysLeavePrimaryUntouchedAndSuggestAcronym() {
        for ((key, acronym) in ACRONYM_SUGGESTIONS) {
            val primary = engine.convertWord(key).bengali
            assertTrue(
                primary != acronym,
                "Tier S key '$key' primary ('$primary') must stay the common Bengali word, not flip to the acronym"
            )
            val suggestions = engine.getSuggestions(key, 6).map { it.bengali }
            assertTrue(
                acronym in suggestions,
                "Tier S key '$key' must offer '$acronym' among getSuggestions(key, 6); got $suggestions"
            )
        }
    }

    // ── Guard pins: S52 must not disturb any pre-existing engine behavior ──

    @Test
    fun guardPinKacciDish() {
        assertEquals("কাচ্চি", engine.convertWord("kacci").bengali)
    }

    @Test
    fun guardPinNameClassStaysBengali() {
        assertEquals("নামে", engine.convertWord("name").bengali)
    }

    @Test
    fun guardPinJosHoiseDibiDefaults() {
        assertEquals("জস", engine.convertWord("jos").bengali)
        assertEquals("হইছে", engine.convertWord("hoise").bengali)
        assertEquals("ডিবি", engine.convertWord("dibi").bengali)
    }

    @Test
    fun guardPinKassiStandardOrthography() {
        assertEquals("কাচছি", engine.convertWord("kassi").bengali)
    }

    @Test
    fun guardPinCommonWordsUnchanged() {
        assertEquals("আমি", engine.convertWord("ami").bengali)
        assertEquals("কেমন", engine.convertWord("kemon").bengali)
        assertEquals("ভালো", engine.convertWord("bhalo").bengali)
        assertEquals("কাল", engine.convertWord("kal").bengali)
        assertEquals("গল্প", engine.convertWord("golpo").bengali)
    }
}
