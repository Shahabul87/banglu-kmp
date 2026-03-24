package com.banglu.engine

import kotlin.test.Test

class PatternDebugTest {
    @Test fun trace() {
        val e = SmartEngine(); e.initializeSync()
        // Test cases that fail
        val words = listOf(
            "cholun", "jabo", "dhori", "from", "dram", "shokal", "tren",
            // Compare with working cases  
            "cholo", "jabi", "dhore", "prem", "gram",
            // Minimal reproduction
            "lun", "lun", "bo", "ri", "rom", "ram", "kal"
        )
        for (w in words) {
            val r = e.convertWord(w)
            println("$w → ${r.bengali} (${r.source})")
        }
    }
}
