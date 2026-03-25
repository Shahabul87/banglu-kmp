package com.banglu.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parity test suite: asserts the KMP SmartEngine produces identical output
 * to the web (TypeScript) SmartEngine for 237 fixture inputs.
 *
 * Fixtures live in jvmTest/resources/parity-fixtures.json and are grouped
 * by category so failures can be isolated per feature area.
 *
 * Many tests are EXPECTED TO FAIL initially — this establishes the baseline
 * before porting missing layers to KMP.
 */
class ParityFixTest {

    // ── JSON model ──────────────────────────────────────────────────────

    @Serializable
    data class Fixture(
        val input: String,
        val expected: String,
        val category: String,
        val priority: String,
    )

    // ── Shared engine + fixture loading ─────────────────────────────────

    private val json = Json { ignoreUnknownKeys = true }

    private val engine: SmartEngine by lazy {
        SmartEngine().also { it.initializeSync() }
    }

    private val fixtures: List<Fixture> by lazy {
        val jsonText = javaClass.getResourceAsStream("/parity-fixtures.json")
            ?.bufferedReader()
            ?.readText()
            ?: error("parity-fixtures.json not found on classpath")
        json.decodeFromString<List<Fixture>>(jsonText)
    }

    private fun fixturesByCategory(category: String): List<Fixture> =
        fixtures.filter { it.category == category }

    /**
     * Convert a fixture input through the engine.
     * Uses [SmartEngine.parse] for multi-word inputs (contains internal spaces)
     * and [SmartEngine.convertWord] for single-word inputs.
     */
    private fun convert(input: String): String {
        // Multi-word: delegate to parse() which handles whitespace + per-word conversion
        if (input.contains(' ') && input.trim().contains(' ')) {
            return engine.parse(input)
        }
        // Single word (or whitespace-only): convertWord trims and handles empty
        return engine.convertWord(input).bengali
    }

    // ── Per-category test methods ───────────────────────────────────────

    @Test
    fun testP0_Layer55Swaps() {
        assertCategory("layer5.5-swap")
    }

    @Test
    fun testP0_TypoCorrection() {
        assertCategory("typo-correction")
    }

    @Test
    fun testP0_Layer6Recovery() {
        assertCategory("layer6-recovery")
    }

    @Test
    fun testP0_CommonWords() {
        assertCategory("common-words")
    }

    @Test
    fun testP0_NatvaShatvaRegression() {
        assertCategory("natva-shatva-regression")
    }

    @Test
    fun testP1_RootDecomposition() {
        assertCategory("root-decomposition")
    }

    @Test
    fun testP1_EnglishDetection() {
        assertCategory("english-detection")
    }

    @Test
    fun testP1_AIDisambiguation() {
        assertCategory("ai-disambiguation")
    }

    @Test
    fun testP2_UppercaseForcers() {
        assertCategory("uppercase-forcers")
    }

    @Test
    fun testP2_EdgeCases() {
        assertCategory("edge-cases")
    }

    // ── Assertion helper ────────────────────────────────────────────────

    /**
     * Runs every fixture in [category], collects failures, and reports
     * them all at once so the full picture is visible in a single run.
     */
    private fun assertCategory(category: String) {
        val cases = fixturesByCategory(category)
        require(cases.isNotEmpty()) { "No fixtures found for category: $category" }

        val failures = mutableListOf<String>()

        for (fixture in cases) {
            val actual = convert(fixture.input)
            if (actual != fixture.expected) {
                failures.add(
                    "[${fixture.priority}] '${fixture.input}': " +
                        "expected '${fixture.expected}', got '$actual'"
                )
            }
        }

        if (failures.isNotEmpty()) {
            val summary = buildString {
                appendLine("PARITY FAILURES in $category: ${failures.size}/${cases.size}")
                appendLine("─".repeat(60))
                failures.forEach { appendLine(it) }
            }
            // Use assertEquals so the full diff is shown in IDE / CI output
            assertEquals(
                0, failures.size,
                summary
            )
        }
    }
}
