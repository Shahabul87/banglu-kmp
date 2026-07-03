package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/** Composing preview must agree with the committed word (S6 composing side). */
class LearnedProbeJvm {
    @Test
    fun composingPreviewMatchesCommit() {
        val e = ConjunctSolutionRoundJvmTest.engine
        e.clearCache()
        assertEquals("তৈরি", e.convertForComposing("toiri").bengali)
        assertEquals("খণ্ড", e.convertForComposing("khond").bengali)
        assertEquals("করি", e.convertForComposing("kori").bengali)
        assertEquals("স্বাস্থ্য", e.convertForComposing("sastho").bengali)
        // S8: margin-promoted alias tops must show in the preview too.
        assertEquals("সংখ্যা", e.convertForComposing("songkha").bengali)
    }
}
