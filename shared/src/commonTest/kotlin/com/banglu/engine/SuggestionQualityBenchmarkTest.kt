package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class SuggestionQualityBenchmarkTest {
    private data class Case(val input: String, val expected: String)

    private val benchmarkCases = listOf(
        Case("ami", "আমি"),
        Case("tumi", "তুমি"),
        Case("amr", "আমার"),
        Case("tomr", "তোমার"),
        Case("bhalo", "ভালো"),
        Case("kemon", "কেমন"),
        Case("acho", "আছো"),
        Case("ache", "আছে"),
        Case("hobe", "হবে"),
        Case("korte", "করতে"),
        Case("bangla", "বাংলা"),
        Case("bangladesh", "বাংলাদেশ"),
        Case("likhte", "লিখতে"),
        Case("chai", "চাই"),
        Case("taka", "টাকা"),
        Case("dorja", "দরজা"),
        Case("kholo", "খোলো"),
        Case("jabo", "যাবো"),
        Case("porikkha", "পরীক্ষা"),
        Case("application", "এপ্লিকেশন"),
        Case("obossoi", "অবশ্যই"),
        Case("sobbdo", "শব্দ"),
        Case("dorkar", "দরকার"),
        Case("ekhon", "এখন"),
        Case("aj", "আজ"),
        Case("apni", "আপনি"),
        Case("khabar", "খাবার"),
        Case("kintu", "কিন্তু"),
        Case("sundor", "সুন্দর"),
        Case("proshno", "প্রশ্ন"),
        Case("uttor", "উত্তর"),
        Case("shikkha", "শিক্ষা"),
        Case("srishti", "সৃষ্টি"),
        Case("drishti", "দৃষ্টি"),
        Case("krishna", "কৃষ্ণ"),
        Case("brihospoti", "বৃহস্পতি"),
        Case("biswas", "বিশ্বাস"),
        Case("bissas", "বিশ্বাস"),
        Case("bhiti", "ভিতি"),
        Case("onekkhon", "অনেকক্ষণ"),
        Case("priyojon", "প্রিয়জন"),
        Case("ghoro", "ঘর"),
        Case("puruskar", "পুরস্কার"),
        Case("education", "এডুকেশন"),
        Case("database", "ডেটাবেস"),
        Case("honeymoon", "হানিমুন"),
        Case("gobeshona", "গবেষণা"),
        Case("byatha", "ব্যথা"),
        Case("montri", "মন্ত্রী"),
        Case("ghoshona", "ঘোষণা"),
        Case("mangsho", "মাংস"),
        Case("ojon", "ওজন"),
        Case("shahid", "শহীদ"),
        Case("shilpi", "শিল্পী"),
        Case("poribohon", "পরিবহন"),
        Case("tyag", "ত্যাগ"),
        Case("tmi", "তুমি"),
        Case("tomi", "তুমি"),
        Case("boro", "বড়"),
        Case("ghora", "ঘোড়া"),
        Case("gari", "গাড়ি"),
        Case("kosto", "কষ্ট"),
        Case("rahman", "রহমান"),
        Case("hothat", "হঠাৎ"),
        Case("dukkho", "দুঃখ"),
        Case("uddog", "উদ্যোগ"),
        Case("biggan", "বিজ্ঞান"),
        Case("bhodro", "ভদ্র"),
        Case("jonmodin", "জন্মদিন"),
        Case("buddhi", "বুদ্ধি"),
        Case("bidya", "বিদ্যা"),
        Case("chihno", "চিহ্ন"),
        Case("iccha", "ইচ্ছা"),
        Case("jonmo", "জন্ম")
    )

    @Test
    fun commonWordsConvertToExpectedBangla() {
        val engine = SmartEngine().also { it.initializeSync() }
        val misses = benchmarkCases.mapNotNull { case ->
            val actual = engine.convertWord(case.input).bengali
            if (actual == case.expected) null else "${case.input}: expected ${case.expected}, got $actual"
        }

        assertTrue(
            misses.isEmpty(),
            "Common Bangla conversion misses:\n${misses.joinToString("\n")}"
        )
    }

    @Test
    fun commonWordsStayInTopThreeSuggestions() {
        val engine = SmartEngine().also { it.initializeSync() }
        val misses = benchmarkCases.mapNotNull { case ->
            val top = engine.getSuggestions(case.input, 6).take(3).map { it.bengali }
            if (case.expected in top) null else "${case.input}: expected ${case.expected}, got $top"
        }

        assertTrue(
            misses.isEmpty(),
            "Common Bangla suggestion top-3 misses:\n${misses.joinToString("\n")}"
        )
    }

    @Test
    fun exactDictionaryWordsOutrankComposerOnlyGeneratedVariants() {
        val engine = SmartEngine().also { it.initializeSync() }
        val suggestions = engine.getSuggestions("doroja", 40)

        val exactDictionaryWords = engine.dictionary.lookup("doroja").map { it.bengali }.toSet()
        val lastExactIndex = suggestions.indexOfLast { it.bengali in exactDictionaryWords }
        val firstComposerOnlyIndex = suggestions.indexOfFirst {
            it.source == "candidate_lattice" &&
                it.bengali !in exactDictionaryWords &&
                engine.dictionary.getPhoneticForBengali(it.bengali) == null
        }

        assertTrue(lastExactIndex >= 0, "Expected exact dictionary candidate in $suggestions")
        assertTrue(firstComposerOnlyIndex >= 0, "Expected composer-only candidate in $suggestions")
        assertTrue(
            lastExactIndex < firstComposerOnlyIndex,
            "Exact dictionary words should rank before composer-only generated variants: $suggestions"
        )
    }

    @Test
    fun commonWordConversionLatencyBudget() {
        val engine = SmartEngine().also { it.initializeSync() }
        repeat(2) { benchmarkCases.forEach { engine.convertWord(it.input) } }

        val mark = TimeSource.Monotonic.markNow()
        repeat(20) {
            benchmarkCases.forEach { engine.convertWord(it.input) }
        }
        val elapsedMs = mark.elapsedNow().inWholeMilliseconds
        val operations = benchmarkCases.size * 20

        assertTrue(
            elapsedMs < operations * 10,
            "Conversion latency budget exceeded: $operations ops took ${elapsedMs}ms"
        )
    }

    @Test
    fun commonSuggestionLatencyBudget() {
        val engine = SmartEngine().also { it.initializeSync() }
        repeat(2) { benchmarkCases.forEach { engine.getSuggestions(it.input, 6) } }

        val mark = TimeSource.Monotonic.markNow()
        repeat(10) {
            benchmarkCases.forEach { engine.getSuggestions(it.input, 6) }
        }
        val elapsedMs = mark.elapsedNow().inWholeMilliseconds
        val operations = benchmarkCases.size * 10

        assertTrue(
            elapsedMs < operations * 20,
            "Suggestion latency budget exceeded: $operations ops took ${elapsedMs}ms"
        )
    }
}
