package com.banglu.engine

import kotlin.test.Test

class StripProbeJvm {
    @Test
    fun probe() {
        if (System.getenv("PROBE") == null) return
        val e = ConjunctSolutionRoundJvmTest.engine
        for (k in listOf("ghumacco", "ghumaccho", "gummacco", "ghumacc", "ghumaccha")) {
            val c = e.convertForComposing(k)
            val w = e.convertWord(k)
            val s = e.getSuggestions(k, 8).map { it.bengali }
            println("PROBE $k composing=${c.bengali}(${c.source}) commit=${w.bengali}(${w.source}) strip=$s")
        }
    }
}
