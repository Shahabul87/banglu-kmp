package com.banglu.engine

import kotlin.test.Test

class StripProbeJvm {
    @Test
    fun probe() {
        if (System.getenv("PROBE") == null) return
        val e = ConjunctSolutionRoundJvmTest.engine
        for (k in listOf("toiri", "brit", "khond", "kori", "kotha", "sastho")) {
            val p = e.convertWord(k)
            val s = e.getSuggestions(k, 8).joinToString(" | ") { "${it.bengali}(${it.source})" }
            println("PROBE $k -> primary=${p.bengali} src=${p.source} strip: $s")
        }
    }
}
