package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test

class StripProbeJvm {
    @Test
    fun probe() {
        if (System.getenv("PROBE") == null) return
        val e = ConjunctSolutionRoundJvmTest.engine
        for (w in listOf("অবাস্তবায়নযোগ্য", "করছাড়ের", "নিত্যপণ্যে")) {
            val roman = ReverseTransliterator.reverseWord(w)
            val out = e.convertWord(roman)
            val match = ReverseTransliterator.foldNukta(out.bengali) == ReverseTransliterator.foldNukta(w)
            println("PROBE OOV $w roman=$roman -> ${out.bengali} (${out.source}) match=$match")
        }
    }
}
