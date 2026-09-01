package com.banglu.engine

import java.io.File
import java.sql.DriverManager
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test

/**
 * S163 SPIKE (throwaway): can a SHARK2-style shape decoder over the
 * dictionary's canonical romans hit usable glide-typing accuracy?
 *
 * Opt-in: BANGLU_S163_PROBE=1. Synthesizes noisy finger paths for real
 * canonical romans on an idealized QWERTY grid and measures how often the
 * true roman is recovered top-1 / top-6, plus decode latency. The decoder
 * here is the feasibility yardstick, NOT the shipping implementation.
 */
class S163GlideProbeJvm {

    // Idealized Banglu letter grid: unit keys; rows q..p / a..l / z..m.
    private val keyCenter: Map<Char, Pair<Double, Double>> = buildMap {
        "qwertyuiop".forEachIndexed { i, c -> put(c, i + 0.5 to 0.5) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, i + 1.0 to 1.5) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, i + 2.0 to 2.5) }
    }

    private fun template(word: String): List<Pair<Double, Double>> =
        word.mapNotNull { keyCenter[it] }

    private fun arcLength(p: List<Pair<Double, Double>>): Double =
        (1 until p.size).sumOf { dist(p[it - 1], p[it]) }

    private fun dist(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = a.first - b.first; val dy = a.second - b.second
        return sqrt(dx * dx + dy * dy)
    }

    /** Resample a polyline to n points, evenly by arc length. */
    private fun resample(path: List<Pair<Double, Double>>, n: Int): List<Pair<Double, Double>> {
        if (path.size == 1) return List(n) { path[0] }
        val total = arcLength(path).takeIf { it > 1e-9 } ?: return List(n) { path[0] }
        val step = total / (n - 1)
        val out = ArrayList<Pair<Double, Double>>(n)
        out.add(path[0])
        var acc = 0.0
        var i = 1
        var prev = path[0]
        while (out.size < n && i < path.size) {
            val d = dist(prev, path[i])
            if (acc + d >= step - 1e-12) {
                val t = (step - acc) / d
                val np = prev.first + t * (path[i].first - prev.first) to
                    prev.second + t * (path[i].second - prev.second)
                out.add(np); prev = np; acc = 0.0
            } else {
                acc += d; prev = path[i]; i++
            }
        }
        while (out.size < n) out.add(path.last())
        return out
    }

    /** Synthetic gesture: template resampled dense, Gaussian noise per point. */
    private fun synthesize(word: String, rng: Random, sigma: Double): List<Pair<Double, Double>> {
        val dense = resample(template(word), 24)
        return dense.map {
            it.first + rng.nextGaussian() * sigma to it.second + rng.nextGaussian() * sigma
        }
    }

    private fun Random.nextGaussian(): Double {
        var u1 = nextDouble(); if (u1 < 1e-12) u1 = 1e-12
        return sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * nextDouble())
    }

    private class Entry(
        val roman: String,
        val freq: Int,
        val shape: List<Pair<Double, Double>>,
        val len: Double,
        val start: Pair<Double, Double>,
        val end: Pair<Double, Double>,
    )

    @Test
    fun probe() {
        if (System.getenv("BANGLU_S163_PROBE") != "1") return
        // jvmTest working dir is shared/ — the dev db lives at the repo root.
        val db = listOf(File("../dictionary.sqlite"), File("dictionary.sqlite"))
            .map { it.absoluteFile }.firstOrNull { it.exists() }
            ?: error("dictionary.sqlite not found beside or above the working dir")

        val lexCap = (System.getenv("BANGLU_S163_LEX") ?: "50000").toInt()
        val sigma = (System.getenv("BANGLU_S163_SIGMA") ?: "0.25").toDouble()
        val samples = (System.getenv("BANGLU_S163_N") ?: "400").toInt()
        val n = 32

        // Canonical romans, frequency-ranked, glide-eligible (a-z, len>=2).
        val lex = ArrayList<Entry>(lexCap)
        DriverManager.getConnection("jdbc:sqlite:${db.path}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    """SELECT key, MAX(frequency) f FROM phonetic_index
                       WHERE priority = 0 GROUP BY key
                       ORDER BY f DESC LIMIT ${lexCap * 2}"""
                ).use { rs ->
                    while (rs.next() && lex.size < lexCap) {
                        val p = rs.getString(1) ?: continue
                        if (p.length < 2 || !p.all { it in 'a'..'z' }) continue
                        val t = template(p)
                        if (t.size < 2) continue
                        lex.add(
                            Entry(p, rs.getInt(2), resample(t, n), arcLength(t), t.first(), t.last())
                        )
                    }
                }
            }
        }
        println("S163 lexicon: ${lex.size} canonical romans (cap $lexCap), sigma=$sigma")

        val maxFreq = lex.maxOf { it.freq }.toDouble()
        val rng = Random(163)
        // Test set: spread across the frequency range, longer words included.
        val tests = lex.filterIndexed { i, e -> i % (lex.size / samples).coerceAtLeast(1) == 0 && e.roman.length >= 3 }
            .take(samples)

        var top1 = 0; var top6 = 0; var totalMs = 0.0
        for (t in tests) {
            val g = synthesize(t.roman, rng, sigma)
            val gRes = resample(g, n)
            val gLen = arcLength(g)
            val gStart = g.first(); val gEnd = g.last()
            val t0 = System.nanoTime()
            val ranked = lex.asSequence()
                .filter { dist(it.start, gStart) < 1.6 && dist(it.end, gEnd) < 1.6 }
                .filter { it.len < gLen * 2.6 + 1.0 && gLen < it.len * 2.6 + 1.0 }
                .map { e ->
                    var d = 0.0
                    for (k in 0 until n) d += dist(e.shape[k], gRes[k])
                    val shape = d / n
                    // Rarity penalty: ln(rel) <= 0, so subtracting it adds a
                    // cost that grows as the word gets rarer. Lower = better.
                    val freqTerm = 0.16 * ln((e.freq + 1) / maxFreq)
                    e to (shape - freqTerm)
                }
                .sortedBy { it.second }
                .take(6)
                .map { it.first.roman }
                .toList()
            totalMs += (System.nanoTime() - t0) / 1e6
            if (ranked.firstOrNull() == t.roman) top1++
            if (t.roman in ranked) top6++
        }
        println(
            "S163 PROBE sigma=$sigma lex=${lex.size}: top1=${"%.1f".format(100.0 * top1 / tests.size)}% " +
                "top6=${"%.1f".format(100.0 * top6 / tests.size)}% " +
                "avg=${"%.1f".format(totalMs / tests.size)}ms over ${tests.size} gestures"
        )
    }
}
