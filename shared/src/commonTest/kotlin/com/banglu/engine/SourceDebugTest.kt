package com.banglu.engine

import kotlin.test.Test

class SourceDebugTest {
    @Test fun sources() {
        val e = SmartEngine(); e.initializeSync()
        for (w in listOf("cholun", "jabo", "dhori", "from", "dram", "shokal")) {
            val r = e.convertWord(w)
            println("$w → ${r.bengali} | source=${r.source} | conf=${r.confidence}")
        }
    }
}
