package com.banglu.engine

import kotlin.test.Test

/**
 * S173 (user: "shororipur … hydrogener — word variation can not handle it
 * properly and this is general"): inflections of legitimate stems must
 * compose even when the inflected form is absent from the validator and the
 * stem is rare — and the genitive after a consonant-final stem is ের.
 */
class S173InflectionCompositionJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = com.banglu.engine.util.ReverseTransliterator.foldNukta(s)
    private fun top(k: String) = fold(engine.convertWord(k).bengali)
    private fun assertEquals(expected: String, actual: String) = kotlin.test.assertEquals(fold(expected), actual)
    private fun assertNotEquals(unexpected: String, actual: String) = kotlin.test.assertNotEquals(fold(unexpected), actual)

    @Test
    fun rareCanonicalOwnerStemComposesItsInflection() {
        // ষড়রিপু is the canonical owner of "shororipu" at corpus frequency 19 —
        // below the composition floor — and ষড়রিপুর is not in the validator.
        // The compound splitter used to win with "সরো রিপুর".
        assertEquals("ষড়রিপু", top("shororipu"))
        assertEquals("ষড়রিপুর", top("shororipur"))
        assertEquals("ষড়রিপুকে", top("shororipuke"))
    }

    @Test
    fun genitiveAfterConsonantFinalLoanStemIsEr() {
        // English silent-e loans: the user types stem+r, the Bengali stem ends
        // in a consonant, so the genitive must be ের (never a bare র).
        assertEquals("টেলিফোন", top("telephone"))
        assertEquals("টেলিফোনের", top("telephoner"))
        assertEquals("মাইক্রোফোনের", top("microphoner"))
    }

    @Test
    fun existingInflectionsUnchanged() {
        assertEquals("হাইড্রোজেনের", top("hydrogener"))
        assertEquals("কম্পিউটারের", top("computerer"))
        assertEquals("ছেলের", top("cheler"))
        assertEquals("বাড়ির", top("barir"))
        assertEquals("বন্ধুদের", top("bondhuder"))
        assertEquals("রাষ্ট্রপতির", top("rashtropotir"))
    }

    @Test
    fun aliasReachedJunkStemsStillDoNotCompose() {
        // S1/D3 law: junk corpus entries reached through ambiguity-aliased
        // keys must not mint invented inflections.
        assertNotEquals("যাতির", top("zatir"))
        assertNotEquals("যেলায়", top("zelay"))
    }
}
