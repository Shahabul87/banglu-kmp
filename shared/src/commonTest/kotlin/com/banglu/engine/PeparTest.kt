package com.banglu.engine
import kotlin.test.Test
class PeparTest {
    @Test fun test() {
        val e = SmartEngine(); e.initializeSync()
        for (w in listOf("pepar","paper","pe","pep","pepa","pepar","peparer")) {
            val r = e.convertWord(w)
            println("$w → ${r.bengali} (${r.source}, ${r.confidence})")
        }
        // Also check suggestions
        val s = e.getSuggestions("pepar", 8)
        println("Suggestions for pepar: ${s.map { it.bengali }}")
    }
}
