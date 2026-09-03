package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S177 (tester screenshot, 2026-09-03: "I typed hrid and the engine produced
 * হৃদয়; হৃদয় should be in the suggestions"): the extended dictionary maps a
 * key that is the exact roman of an attested word (হৃদ@67) to a LONGER
 * completion (হৃদয়, own roman "hridoy"). S141 law — the engine must not
 * ignore what was typed: the typed word keeps the commit, the completion
 * rides the strip. Shared arbitration, so preview == commit.
 */
class S177TypedWordBeatsCompletionJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun hridCommitsHridAndOffersHridoy() {
        assertEquals("হৃদ", fold(engine.convertWord("hrid").bengali))
        assertEquals("হৃদ", fold(engine.convertForComposing("hrid").bengali))
        val strip = engine.getSuggestions("hrid", 6).map { fold(it.bengali) }
        assertTrue(fold("হৃদয়") in strip, "completion must stay on the strip: $strip")
    }

    @Test
    fun fullKeysStillCommitTheirWords() {
        assertEquals(fold("হৃদয়"), fold(engine.convertWord("hridoy").bengali))
        assertEquals(fold("হৃদয়ের"), fold(engine.convertWord("hridoyer").bengali))
    }
}
