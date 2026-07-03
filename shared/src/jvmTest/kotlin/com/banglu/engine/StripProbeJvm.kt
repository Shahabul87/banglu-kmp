package com.banglu.engine

import kotlin.test.Test

class StripProbeJvm {
    @Test
    fun probe() {
        if (System.getenv("PROBE") == null) return
        val e = ConjunctSolutionRoundJvmTest.engine
        val m = e.convertWord("mach")
        println("PROBE mach primary=${m.bengali} alts=${m.alternatives.map { it.bengali to it.confidence }}")
        val rt = e.rerankWithPreviousContext("টেস্ট", m)
        println("PROBE test-mach -> ${rt.bengali}")
        val p = e.convertWord("phebruari")
        println("PROBE phebruari primary=${p.bengali} src=${p.source}")
        println("PROBE phebruari strip=${e.getSuggestions("phebruari", 6).map { it.bengali }}")
    }
}
