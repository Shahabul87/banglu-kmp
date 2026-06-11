package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ConfusingWordsTest {
    private val engine = SmartEngine().also { it.initializeSync() }

    data class Case(val input: String, val expected: String, val category: String)

    private val cases = listOf(
        // ত/ট confusion
        Case("t", "ত", "ত/ট"), Case("ta", "তা", "ত/ট"),
        Case("T", "ত", "ত/ট"), Case("Ta", "তা", "ত/ট"),
        Case("taka", "টাকা", "ত/ট"),
        Case("aponar", "আপনার", "ত/ট"), Case("ekti", "একটি", "ত/ট"),
        Case("ekta", "একটা", "ত/ট"), Case("ektu", "একটু", "ত/ট"),
        Case("pete", "পেতে", "ত/ট"), Case("ete", "এতে", "ত/ট"),
        Case("du", "দু", "ত/ট"),

        // দ/ড confusion
        Case("d", "দ", "দ/ড"), Case("da", "দা", "দ/ড"),
        Case("D", "দ", "দ/ড"), Case("Da", "দা", "দ/ড"),
        Case("dan", "ডান", "দ/ড"), Case("dal", "দাল", "দ/ড"),

        // ন/ণ confusion
        Case("karon", "কারণ", "ন/ণ"), Case("karone", "কারণে", "ন/ণ"),
        Case("kina", "কিনা", "ন/ণ"), Case("ghonta", "ঘণ্টা", "ন/ণ"),

        // র/ড় confusion
        Case("er", "এর", "র/ড়"), Case("por", "পর", "র/ড়"),
        Case("sorokar", "সরকার", "র/ড়"),

        // শ/ষ/স confusion
        Case("s", "স", "শ/ষ/স"), Case("sh", "শ", "শ/ষ/স"),
        Case("shokal", "সকাল", "শ/ষ/স"), Case("shobar", "সবার", "শ/ষ/স"),
        Case("sundor", "সুন্দর", "শ/ষ/স"), Case("proshno", "প্রশ্ন", "শ/ষ/স"),
        Case("ongsh", "অংশ", "শ/ষ/স"),

        // Conjuncts
        Case("jonyo", "জন্য", "conjunct"), Case("ortho", "অর্থ", "conjunct"),
        Case("bondho", "বন্ধ", "conjunct"), Case("modhyo", "মধ্য", "conjunct"),
        Case("sotto", "সত্য", "conjunct"), Case("matro", "মাত্র", "conjunct"),
        Case("byapar", "ব্যাপার", "conjunct"), Case("sahajyo", "সাহায্য", "conjunct"),
        Case("otyadhunik", "অত্যাধুনিক", "conjunct"), Case("tothyo", "তথ্য", "conjunct"),

        // Common daily words
        Case("kono", "কোনো", "daily"), Case("moto", "মতো", "daily"),
        Case("holo", "হলো", "daily"), Case("gota", "গত", "daily"),
        Case("upor", "উপর", "daily"), Case("aro", "আরো", "daily"),
        Case("tui", "তুই", "daily"), Case("ekhon", "এখন", "daily"),
        Case("kintu", "কিন্তু", "daily"), Case("khabar", "খাবার", "daily"),
    )

    @Test
    fun testAllConfusingWords() {
        val failures = mutableListOf<String>()
        val passed = mutableListOf<String>()

        for (case in cases) {
            val result = engine.convertWord(case.input).bengali
            if (result == case.expected) {
                passed.add("✅ ${case.input} → ${result} [${case.category}]")
            } else {
                failures.add("❌ ${case.input}: expected '${case.expected}', got '${result}' [${case.category}]")
            }
        }

        println("\n=== CONFUSING WORDS TEST RESULTS ===")
        for (p in passed) println(p)
        for (f in failures) println(f)
        println("Passed: ${passed.size}/${cases.size} (${passed.size * 100 / cases.size}%)")
        println("=== END ===\n")

        if (failures.isNotEmpty()) {
            fail("${failures.size}/${cases.size} failed:\n${failures.joinToString("\n")}")
        }
    }
}
