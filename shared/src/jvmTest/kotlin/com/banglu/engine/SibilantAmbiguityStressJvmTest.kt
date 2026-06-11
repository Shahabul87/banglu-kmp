package com.banglu.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SibilantAmbiguityStressJvmTest {
    private val engine = SmartEngine().also { it.initializeSync() }

    @Test
    fun stressSibilantFamiliesAndWriteReport() {
        val report = buildString {
            appendLine("input\tprimary\ttopSuggestions")
            for (input in stressInputs()) {
                val primary = engine.convertWord(input).bengali
                val suggestions = engine.getSuggestions(input, 10)
                    .joinToString(" | ") { "${it.bengali}(${it.source}:${it.phonetic})" }
                appendLine("$input\t$primary\t$suggestions")
            }
        }

        val reportFile = File("build/reports/banglu-sibilant-ambiguity.tsv")
        reportFile.parentFile.mkdirs()
        reportFile.writeText(report)

        assertPrimary("asar", "আসার")
        assertSuggestionsDoNotContain("asar", setOf("আশার", "আষাঢ়"))
        assertSuggestionsContain("ashar", setOf("আশার", "আষাঢ়"))
        assertSuggestionsDoNotContain("ashar", setOf("আসার"))
        assertSuggestionsContain("asha", setOf("আশা"))
        assertSuggestionsDoNotContain("asha", setOf("আসা"))
        assertPrimary("basa", "বাসা")
        assertSuggestionsContain("basha", setOf("ভাষা"))
        assertSuggestionsDoNotContain("basha", setOf("বাসা"))
        assertSuggestionsContain("bhasha", setOf("ভাষা"))
        assertSuggestionsContain("biswas", setOf("বিশ্বাস"))
        assertSuggestionsContain("chash", setOf("চাষ"))
        assertPrimary("santi", "শান্তি")
        assertPrimary("sohor", "শহর")
        assertPrimary("chashar", "চাষার")
    }

    private fun assertSuggestionsContain(input: String, expected: Set<String>) {
        val suggestions = engine.getSuggestions(input, 12).map { it.bengali }.toSet()
        val missing = expected - suggestions
        assertTrue(
            missing.isEmpty(),
            "input=$input missing=$missing suggestions=$suggestions"
        )
    }

    private fun assertSuggestionsDoNotContain(input: String, blocked: Set<String>) {
        val suggestions = engine.getSuggestions(input, 12).map { it.bengali }.toSet()
        val present = blocked.intersect(suggestions)
        assertTrue(
            present.isEmpty(),
            "input=$input should not include=$present suggestions=$suggestions"
        )
    }

    private fun assertPrimary(input: String, expected: String) {
        val primary = engine.convertWord(input).bengali
        assertTrue(primary == expected, "input=$input expected=$expected actual=$primary")
    }

    private fun stressInputs(): List<String> {
        val handPicked = listOf(
            "asa", "asha", "asar", "ashar", "asate", "ashate", "asay", "ashay",
            "asarh", "asharh", "asadh", "ashadh",
            "basa", "basha", "basar", "bashar", "basate", "bashate", "basay", "bashay",
            "bhasa", "bhasha", "bhasar", "bhashar", "bhasate", "bhashate",
            "chash", "chasa", "cash", "casha", "chasar", "chashar",
            "biswas", "biswash", "bishwas", "bishwash", "bissas", "bissash",
            "itihas", "itihash", "prokas", "prokash", "bikas", "bikash",
            "shikkha", "sikkha", "srishti", "sristi", "drishti", "dristi",
            "sombhob", "shombhob", "oshombhob", "osombhob",
            "somoy", "shomoy", "sokal", "shokal", "sundor", "shundor",
            "shanti", "santi", "sahas", "shahas", "shohor", "sohor",
            "sastro", "shastro", "sastho", "shastho", "swastho", "shwastho",
        )

        val roots = listOf(
            "asa", "asha", "basa", "basha", "bhasa", "bhasha",
            "biswas", "bishwas", "chash", "chasa", "prokash", "prokas"
        )
        val suffixes = listOf("", "r", "er", "e", "te", "ke", "ta", "ti", "gulo", "y", "o")
        val generated = roots.flatMap { root -> suffixes.map { suffix -> root + suffix } }

        return (handPicked + generated).distinct()
    }
}
