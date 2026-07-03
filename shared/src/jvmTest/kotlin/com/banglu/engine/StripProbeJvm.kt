package com.banglu.engine

import kotlin.test.Test

class StripProbeJvm {
    @Test
    fun probe() {
        if (System.getenv("PROBE") == null) return
        val e = ConjunctSolutionRoundJvmTest.engine
        e.clearCache()
        val c = e.convertWord("poriko")
        println("PROBE poriko -> ${c.bengali} src=${c.source} conf=${c.confidence} alts=${c.alternatives.take(3).map{it.bengali}}")
        val s = e.trySuffixStrippedDictionary("poriko")
        println("PROBE suffixStripped=${s?.bengali} conf=${s?.confidence}")
    }
}
