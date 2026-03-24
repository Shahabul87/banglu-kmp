package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Cross-Platform Parity Tests
 *
 * 55+ phonetic inputs that must produce identical Bengali output across all platforms
 * (JVM, Android, iOS, JS). All cases use seed dictionary words only (no 480K needed).
 *
 * These tests serve as the contract between the TypeScript web engine and the KMP engine.
 * Any platform divergence will cause a failure here.
 */
class ParityTest {
    private val engine = SmartEngine().also { it.initializeSync() }

    data class Case(val input: String, val expected: String)

    private val cases = listOf(
        // ═══════════════════════ Pronouns ═══════════════════════
        Case("ami", "আমি"),
        Case("tumi", "তুমি"),
        Case("apni", "আপনি"),
        Case("se", "সে"),
        Case("amra", "আমরা"),
        Case("tara", "তারা"),
        Case("amar", "আমার"),
        Case("tomar", "তোমার"),

        // ═══════════════════════ Question words ═══════════════════════
        Case("ki", "কি"),
        Case("ke", "কে"),
        Case("keno", "কেনো"),
        Case("kokhon", "কখন"),
        Case("kothay", "কোথায়"),
        Case("koto", "কতো"),

        // ═══════════════════════ Common verbs ═══════════════════════
        Case("ache", "আছে"),
        Case("kori", "করি"),
        Case("kore", "করে"),
        Case("hoy", "হয়"),
        Case("hobe", "হবে"),

        // ═══════════════════════ Common nouns ═══════════════════════
        Case("bangla", "বাংলা"),
        Case("bangladesh", "বাংলাদেশ"),
        Case("dhaka", "ঢাকা"),
        Case("bari", "বাড়ি"),
        Case("ghor", "ঘর"),
        Case("pani", "পানি"),
        Case("ma", "মা"),
        Case("baba", "বাবা"),
        Case("din", "দিন"),
        Case("rat", "রাত"),
        Case("sokal", "সকাল"),

        // ═══════════════════════ Adjectives ═══════════════════════
        Case("bhalo", "ভালো"),
        Case("kharap", "খারাপ"),
        Case("sundor", "সুন্দর"),
        Case("notun", "নতুন"),
        Case("lal", "লাল"),

        // ═══════════════════════ Conjunctions / Particles ═══════════════════════
        Case("ebong", "এবং"),
        Case("kintu", "কিন্তু"),
        Case("ar", "আর"),
        Case("tai", "তাই"),
        Case("ekhon", "এখন"),

        // ═══════════════════════ Food / Common nouns ═══════════════════════
        Case("khabar", "খাবার"),
        Case("jol", "জল"),
        Case("boi", "বই"),
        Case("desh", "দেশ"),
        Case("gram", "গ্রাম"),
        Case("kaj", "কাজ"),
        Case("mon", "মন"),

        // ═══════════════════════ Aspirated consonant test ═══════════════════════
        Case("ghar", "ঘর"),

        // ═══════════════════════ Verb roots ═══════════════════════
        Case("bol", "বল"),
        Case("kor", "কর"),
        Case("dekh", "দেখ"),
        Case("shon", "শোন"),

        // ═══════════════════════ Greetings ═══════════════════════
        Case("dhonnobad", "ধন্যবাদ"),

        // ═══════════════════════ Numbers ═══════════════════════
        Case("ek", "এক"),
        Case("dui", "দুই"),
        Case("tin", "তিন"),
    )

    @Test
    fun testAllParityCases() {
        val failures = mutableListOf<String>()
        for (case in cases) {
            val result = engine.convertWord(case.input).bengali
            if (result != case.expected) {
                failures.add("'${case.input}': expected '${case.expected}', got '$result'")
            }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size}/${cases.size} parity failures:\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun testParsePreservesStructure() {
        val input = "ami  tumi   ache"
        val result = engine.parse(input)
        // Should preserve double and triple spaces
        assertTrue(result.contains("  "), "Result '$result' should preserve double spaces")
    }

    @Test
    fun testParseMultiWordConversion() {
        val result = engine.parse("ami bhalo achi")
        assertTrue(result.contains("আমি"), "Result '$result' should contain আমি")
        assertTrue(result.contains("ভালো"), "Result '$result' should contain ভালো")
    }

    @Test
    fun testAdapterConvert() {
        SmartEngineAdapter.reset()
        SmartEngineAdapter.initializeSync()
        val result = SmartEngineAdapter.convert("ami")
        assertTrue(result == "আমি", "Adapter convert('ami') should return আমি, got '$result'")
    }

    @Test
    fun testAdapterConvertWord() {
        SmartEngineAdapter.reset()
        SmartEngineAdapter.initializeSync()
        val result = SmartEngineAdapter.convertWord("bangladesh")
        assertTrue(
            result.bengali == "বাংলাদেশ",
            "Adapter convertWord('bangladesh') should return বাংলাদেশ, got '${result.bengali}'"
        )
        assertTrue(result.confidence > 0.85, "Confidence should be > 0.85, got ${result.confidence}")
    }

    @Test
    fun testAdapterParse() {
        SmartEngineAdapter.reset()
        SmartEngineAdapter.initializeSync()
        val result = SmartEngineAdapter.parse("ami tumi")
        assertTrue(result.contains("আমি"), "Adapter parse should contain আমি")
        assertTrue(result.contains("তুমি"), "Adapter parse should contain তুমি")
    }

    @Test
    fun testAdapterGetSuggestions() {
        SmartEngineAdapter.reset()
        SmartEngineAdapter.initializeSync()
        val suggestions = SmartEngineAdapter.getSuggestions("am", 6)
        assertTrue(suggestions.isNotEmpty(), "Adapter getSuggestions('am') should not be empty")
    }

    @Test
    fun testAdapterOnWordSelected() {
        SmartEngineAdapter.reset()
        SmartEngineAdapter.initializeSync()
        // Use a phonetic/bengali pair that doesn't conflict with consonant filtering rules
        SmartEngineAdapter.onWordSelected("parikkhya", "পরীক্ষা")
        val result = SmartEngineAdapter.convert("parikkhya")
        assertTrue(result == "পরীক্ষা", "After onWordSelected, convert should return পরীক্ষা, got '$result'")
        SmartEngineAdapter.reset()
    }

    @Test
    fun testAdapterClearCache() {
        SmartEngineAdapter.reset()
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.convert("ami")
        SmartEngineAdapter.clearCache()
        // Should still work after cache clear
        val result = SmartEngineAdapter.convert("ami")
        assertTrue(result == "আমি", "After clearCache, convert('ami') should still return আমি")
        SmartEngineAdapter.reset()
    }

    @Test
    fun testAdapterReset() {
        SmartEngineAdapter.reset()
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.reset()
        // After reset, should re-initialize lazily
        val result = SmartEngineAdapter.convert("tumi")
        assertTrue(result == "তুমি", "After reset and re-init, convert('tumi') should return তুমি")
        SmartEngineAdapter.reset()
    }

    @Test
    fun testCaseInsensitivity() {
        val lower = engine.convertWord("ami").bengali
        val upper = engine.convertWord("AMI").bengali
        val mixed = engine.convertWord("Ami").bengali
        assertTrue(lower == upper && upper == mixed, "Case should not matter: $lower vs $upper vs $mixed")
    }

    @Test
    fun testEmptyInput() {
        val result = engine.convertWord("").bengali
        assertTrue(result.isEmpty(), "Empty input should produce empty output")
    }

    @Test
    fun testWhitespaceOnlyInput() {
        val result = engine.parse("   ")
        assertTrue(result == "   ", "Whitespace-only input should be preserved, got '$result'")
    }
}
