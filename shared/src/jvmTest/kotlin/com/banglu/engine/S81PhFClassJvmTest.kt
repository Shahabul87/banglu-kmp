package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S81 (tester round 2026-08-05, dolfin composed দল্ফিন): ফ romanizes as
 * "ph", so the f-spellings everyone actually types had NO index rows at all
 * (fon/fol were empty keys; ডলফিন sat only under "dolophin"). Db 3.8.10 adds
 * the o_drop_ph + ph_to_f habit rules; the English lexicon retries the f↔ph
 * swap (dolfin/fone class) and its CMU dolphin→ডলফেন error is overridden.
 * Db-gated so pre-3.8.10 checkouts skip.
 */
class S81PhFClassJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun hasFKeys(): Boolean =
        engine.getSuggestions("fol", 4).any { it.bengali == "ফল" }

    @Test
    fun fSpellingsReachTheirPhWords() {
        if (!hasFKeys()) return // pre-3.8.10 db
        assertEquals("ফল", engine.convertWord("fol").bengali)
        assertEquals("ফোন", engine.convertWord("fon").bengali)
        assertEquals("ডলফিন", engine.convertWord("dolfin").bengali)
        assertEquals("ডলফিন", engine.convertWord("dolphin").bengali)
    }

    @Test
    fun composingAgreesWithCommitForTheLoanwordClass() {
        if (!hasFKeys()) return
        // The tester's exact complaint: preview showed দল্ফিন while space
        // committed ডলফিন — the store-backed f-key closes the split.
        assertEquals("ডলফিন", engine.convertForComposing("dolfin").bengali)
        assertEquals("ডলফিন", engine.convertForComposing("dolphin").bengali)
    }

    @Test
    fun impossibleConjunctsNeverSurface() {
        // S81 filter extension: ড়/ঢ়/য় never start a conjunct (the কড়্বনে
        // class on older builds) — no suggestion may contain nukta-letter +
        // hasanta, in either encoding form.
        for (key in listOf("korbone", "korbona", "parle", "dolfin")) {
            val bad = engine.getSuggestions(key, 8).filter { s ->
                s.bengali.windowed(2).any { pair ->
                    // hasanta after precomposed ড়(09DC)/ঢ়(09DD)/য়(09DF) or a
                    // combining nukta (09BC)
                    pair[1] == '্' && pair[0] in "ড়ঢ়য়়"
                }
            }
            assertTrue(bad.isEmpty(), "impossible conjunct for '$key': ${bad.map { it.bengali }}")
        }
    }

    @Test
    fun phSpellingsKeepWorking() {
        assertEquals("ফুল", engine.convertWord("ful").bengali)
        assertEquals("ফেলা", engine.convertWord("fela").bengali)
        // S153 pin flip (documented, corpus 292:40 in the Banglish study):
        // typed "phone" means the loan ফোন in the chat register — the
        // shorthand table owns the key now; the locative ফোনে stays
        // reachable via its store row on the strip and the "phone"+e keys.
        assertEquals("ফোন", engine.convertWord("phone").bengali)
        assertTrue(engine.getSuggestions("phone", 6).any { it.bengali == "ফোন" })
        assertEquals("ফোন", engine.convertWord("phon").bengali)
    }
}
