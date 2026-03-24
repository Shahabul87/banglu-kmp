package com.banglu.engine

import kotlin.test.Test

class WebVsKmpTest {
    private val engine = SmartEngine().also { it.initializeSync() }

    @Test
    fun printAll100Words() {
        val words = listOf(
            // Pronouns
            "ami", "tumi", "apni", "se", "amra", "tara", "amar", "tomar", "tui", "tar",
            // Question words
            "ki", "ke", "keno", "kokhon", "kothay", "koto", "kemon", "kar",
            // Common verbs
            "ache", "kori", "kore", "hoy", "hobe", "jai", "ase", "khai", "boli", "dekhi",
            // Verb forms
            "korbo", "korlo", "korte", "korechi", "korchhi", "holo", "chhilo", "thakbo",
            // Common nouns
            "bangla", "bangladesh", "dhaka", "bari", "ghor", "pani", "din", "rat", "sokal",
            "desh", "gram", "rasta", "somoy", "kaj", "kotha", "mon", "boi", "taka",
            // Adjectives
            "bhalo", "kharap", "sundor", "boro", "chhoto", "notun", "lal", "nil", "kalo",
            // Conjunctions
            "ebong", "kintu", "ar", "tai", "othoba", "jodi", "tobe", "jekhane",
            // Daily words
            "ekhon", "khabar", "jol", "ma", "baba", "bondhu", "chhele", "meye",
            // Confusing words (ত/ট ন/ণ র/ড় শ/ষ)
            "er", "ekti", "ekta", "aponar", "du", "karon", "kina", "por", "sorokar",
            "proshno", "ongsh", "jonyo", "ortho", "bondho", "modhyo", "sotto", "matro",
            // Conjunct words
            "otyadhunik", "byapar", "shahajyo", "tothyo", "gota", "upor", "aro", "moto",
            // Colloquial
            "koroto", "boloto", "chhilo", "keno",
            // Pattern engine test
            "apur", "srishti", "biswas", "rashtra"
        )

        println("\n=== KMP ENGINE OUTPUT (100 words) ===")
        for (word in words) {
            val result = engine.convertWord(word)
            println("$word|${result.bengali}|${result.confidence}|${result.source}")
        }
        println("=== END ===\n")
    }
}
