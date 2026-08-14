package com.banglu.engine.touch

import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import java.sql.DriverManager
import kotlin.math.floor
import kotlin.random.Random
import kotlin.test.Test

/**
 * S99: synthetic noisy-tap study — the evidence gate for probabilistic touch
 * targeting. Simulates real typists' horizontal tap scatter (Gaussian around
 * each intended key center) over corpus words and measures per-letter
 * accuracy with and without the model. Deterministic (seeded).
 *
 * Skipped unless S99_STUDY=1:
 *   S99_STUDY=1 ./gradlew :shared:jvmTest --tests "com.banglu.engine.touch.S99NoisyTapStudyJvm"
 */
class S99NoisyTapStudyJvm {

    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val rowOffset = listOf(0.0, 0.5, 1.5)

    private data class Key(val char: Char, val row: Int, val col: Int)

    private val keyOf = HashMap<Char, Key>().apply {
        rows.forEachIndexed { r, chars -> chars.forEachIndexed { c, ch -> put(ch, Key(ch, r, c)) } }
    }

    @Test
    fun runStudy() {
        if (System.getenv("S99_STUDY") != "1") return
        val sigma = System.getenv("S99_SIGMA")?.toDoubleOrNull() ?: 0.30
        val outDir = File("build/reports/s99-study").apply { mkdirs() }

        val enWords = loadEnglishWords(2000)
        val bnWords = loadBanglaRomanWords(2000)

        val summary = buildString {
            appendLine("S99 NOISY TAP STUDY — sigma=$sigma key-widths, seed=42")
            appendLine(evaluate("ENGLISH", enWords, english = true, sigma = sigma))
            appendLine(evaluate("BANGLA-ROMAN", bnWords, english = false, sigma = sigma))
        }
        File(outDir, "summary.txt").writeText(summary)
        println(summary)
    }

    private fun evaluate(label: String, words: List<String>, english: Boolean, sigma: Double): String {
        val rng = Random(42)
        var taps = 0
        var baselineHits = 0
        var modelHits = 0
        var flips = 0
        var flipsCorrect = 0
        var flipsHarmful = 0

        for (word in words) {
            var decodedPrefix = ""
            for (intended in word) {
                val key = keyOf[intended] ?: continue
                taps++
                // Horizontal scatter around the intended key center.
                val center = rowOffset[key.row] + key.col + 0.5
                val x = center + rng.nextGaussian() * sigma
                val colHit = floor(x - rowOffset[key.row]).toInt()
                    .coerceIn(0, rows[key.row].length - 1)
                val landed = rows[key.row][colHit]
                val frac = (x - rowOffset[key.row] - colHit).toFloat().coerceIn(0f, 1f)
                val left = rows[key.row].getOrNull(colHit - 1)
                val right = rows[key.row].getOrNull(colHit + 1)

                if (landed == intended) baselineHits++
                val resolved = TouchTargetModel.resolve(
                    decodedPrefix, landed, left, right, frac, english
                )
                if (resolved == intended) modelHits++
                if (resolved != landed) {
                    flips++
                    if (resolved == intended) flipsCorrect++
                    if (landed == intended) flipsHarmful++
                }
                decodedPrefix += resolved
            }
        }
        fun pct(n: Int, d: Int) = if (d == 0) "0%" else "${(n * 10000 / d) / 100.0}%"
        return "$label: taps=$taps baseline=${pct(baselineHits, taps)} " +
            "model=${pct(modelHits, taps)} flips=$flips " +
            "flipPrecision=${pct(flipsCorrect, flips)} harmfulFlips=${pct(flipsHarmful, flips)}"
    }

    private fun Random.nextGaussian(): Double {
        // Box-Muller.
        var u1: Double
        do { u1 = nextDouble() } while (u1 <= 1e-12)
        val u2 = nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) *
            kotlin.math.cos(2.0 * kotlin.math.PI * u2)
    }

    private fun loadEnglishWords(limit: Int): List<String> =
        com.banglu.engine.english.EnglishTypingEngine().let {
            // The wordlist is frequency-ordered; take the head via completions
            // being unnecessary — access the data object directly.
            com.banglu.engine.english.EnglishWordData.WORDS
                .filter { w -> w.all { it in 'a'..'z' } && w.length >= 2 }
                .take(limit)
        }

    private fun loadBanglaRomanWords(limit: Int): List<String> {
        val db = com.banglu.engine.TestDictionaryLoader.findDictionarySqlite()
        val out = ArrayList<String>(limit)
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT key FROM phonetic_index WHERE priority=0 " +
                        "GROUP BY key ORDER BY MAX(frequency) DESC LIMIT ${limit * 2}"
                ).use { rs ->
                    while (rs.next() && out.size < limit) {
                        val k = rs.getString(1)
                        if (k.length >= 2 && k.all { it in 'a'..'z' }) out.add(k)
                    }
                }
            }
        }
        return out
    }
}
