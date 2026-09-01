package com.banglu.engine

import com.banglu.engine.glide.GlideDecoder
import com.banglu.engine.glide.GlideGrid
import com.banglu.engine.glide.GlideLexicon
import com.banglu.engine.glide.GlidePath
import com.banglu.engine.glide.GlidePoint
import java.io.File
import java.sql.DriverManager
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test

/**
 * S163 tuning study: the REAL GlideDecoder + GlideLexicon over the real
 * dictionary's canonical romans, on synthetic noisy gestures.
 *
 * Opt-in: BANGLU_S163_STUDY=1; options BANGLU_S163_SIGMA (default 0.25),
 * BANGLU_S163_LEX (default 50000), BANGLU_S163_N (default 400).
 * Rerun this sweep (σ 0.15/0.25/0.35) before ANY glide tuning change and
 * record the numbers in the commit message (S82/S149 study discipline).
 */
class S163GlideStudyJvm {

    private fun Random.gaussian(): Double {
        var u1 = nextDouble(); if (u1 < 1e-12) u1 = 1e-12
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * nextDouble())
    }

    @Test
    fun study() {
        if (System.getenv("BANGLU_S163_STUDY") != "1") return
        val db = listOf(File("../dictionary.sqlite"), File("dictionary.sqlite"))
            .map { it.absoluteFile }.firstOrNull { it.exists() }
            ?: error("dictionary.sqlite not found beside or above the working dir")

        val lexCap = (System.getenv("BANGLU_S163_LEX") ?: "50000").toInt()
        val sigma = (System.getenv("BANGLU_S163_SIGMA") ?: "0.25").toDouble()
        val samples = (System.getenv("BANGLU_S163_N") ?: "400").toInt()

        val grid = GlideGrid()
        val words = ArrayList<Pair<String, Int>>(lexCap)
        DriverManager.getConnection("jdbc:sqlite:${db.path}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    """SELECT key, MAX(frequency) f FROM phonetic_index
                       WHERE priority = 0 GROUP BY key
                       ORDER BY f DESC LIMIT ${lexCap * 2}"""
                ).use { rs ->
                    while (rs.next() && words.size < lexCap) {
                        val p = rs.getString(1) ?: continue
                        if (p.length < 2 || !p.all { it in 'a'..'z' }) continue
                        words.add(p to rs.getInt(2))
                    }
                }
            }
        }
        val lexicon = GlideLexicon.build(words, grid)
        val tuning = com.banglu.engine.glide.GlideTuning(
            cornerWeight = (System.getenv("BANGLU_S163_CORNER") ?: "0.0").toFloat(),
            freqWeight = (System.getenv("BANGLU_S163_FREQW") ?: "0.16").toFloat(),
            maxScore = (System.getenv("BANGLU_S163_MAXSCORE") ?: "1.15").toFloat(),
        )
        val decoder = GlideDecoder(lexicon, tuning)
        println("S163 STUDY lexicon=${lexicon.size} sigma=$sigma tuning=$tuning")

        val rng = Random(163)
        val stride = (lexicon.size / samples).coerceAtLeast(1)
        val tests = (0 until lexicon.size step stride)
            .map { lexicon.word(it) }.filter { it.length >= 3 }.take(samples)

        var top1 = 0; var top6 = 0; var none = 0; var totalMs = 0.0
        for (w in tests) {
            val dense = GlidePath.resample(w.mapNotNull { grid.center(it) }, 24)
            val gesture = dense.map {
                GlidePoint(
                    (it.x + rng.gaussian() * sigma).toFloat(),
                    (it.y + rng.gaussian() * sigma).toFloat()
                )
            }
            val t0 = System.nanoTime()
            val out = decoder.decode(gesture)
            totalMs += (System.nanoTime() - t0) / 1e6
            when {
                out.isEmpty() -> none++
                out.first().word == w -> { top1++; top6++ }
                out.any { it.word == w } -> top6++
            }
        }
        println(
            "S163 STUDY sigma=$sigma lex=${lexicon.size} " +
                "top1=${"%.1f".format(100.0 * top1 / tests.size)}% " +
                "top6=${"%.1f".format(100.0 * top6 / tests.size)}% " +
                "none=${"%.1f".format(100.0 * none / tests.size)}% " +
                "avg=${"%.1f".format(totalMs / tests.size)}ms over ${tests.size}"
        )
    }
}
