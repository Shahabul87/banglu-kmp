package com.banglu.engine

import kotlin.test.Test

class WebComparisonTest {
    private val engine = SmartEngine().also { it.initializeSync() }

    @Test
    fun printConversions() {
        val words = listOf(
            "ami", "tumi", "apni", "bangladesh", "bhalo", "khabar",
            "otyadhunik", "korbina", "tui", "er", "jonyo", "ekti",
            "aponar", "du", "kono", "upor", "gota", "sundor",
            "ekhon", "kintu", "karon", "bondho", "sorokar", "holo",
            "aro", "moto", "por", "proshno", "ortho", "sotto",
            "modhyo", "matro", "byapar", "shahajyo", "kharap",
            "ebong", "ar", "tai", "din", "rat", "sokal",
            "apur", "korbona", "jabina", "dekhbona"
        )
        println("\n=== KMP ENGINE OUTPUT ===")
        for (word in words) {
            val result = engine.convertWord(word)
            println("$word → ${result.bengali}")
        }
        println("=== END ===\n")
    }
}
