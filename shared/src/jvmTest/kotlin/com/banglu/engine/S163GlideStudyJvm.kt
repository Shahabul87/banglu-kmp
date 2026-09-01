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
        // S163b realism: real thumbs carry a SYSTEMATIC per-gesture offset
        // (touch registers below the aimed label, whole gesture drifts) on
        // top of per-point jitter. Vertical bias runs 1.5x horizontal.
        val bias = (System.getenv("BANGLU_S163_BIAS") ?: "0.18").toDouble()
        // Commit-on-down hands the decoder the glide's true first key.
        val useFirstKey = (System.getenv("BANGLU_S163_FIRSTKEY") ?: "1") == "1"

        val grid = GlideGrid()
        // S163b: seeds first (kmon/valo class — no index rows), then ALL
        // priorities (chat aliases korsi/issa/jabo are priority-1 rows).
        val merged = LinkedHashMap<String, Int>(lexCap * 2)
        for ((w, f) in com.banglu.engine.glide.GlideSeedRomans.entries()) merged[w] = f
        DriverManager.getConnection("jdbc:sqlite:${db.path}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    """SELECT key, MAX(frequency) f FROM phonetic_index
                       GROUP BY key ORDER BY f DESC LIMIT ${lexCap * 2}"""
                ).use { rs ->
                    while (rs.next() && merged.size < lexCap) {
                        val p = rs.getString(1) ?: continue
                        if (p.length < 2 || !p.all { it in 'a'..'z' }) continue
                        val f = rs.getInt(2)
                        val prev = merged[p]
                        if (prev == null || f > prev) merged[p] = f
                    }
                }
            }
        }
        // Roman→word_id of the key's strongest row: the metric that matters
        // is the CONVERTED word — decoding "kmon" for a "kemon" glide is a
        // WIN (same Bengali), and alias-inclusive lexicons make roman-exact
        // scoring lie. (SQLite bare-column-with-MAX picks the max row's id.)
        val keyWord = HashMap<String, Long>(merged.size * 2)
        DriverManager.getConnection("jdbc:sqlite:${db.path}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT key, word_id, MAX(frequency) FROM phonetic_index GROUP BY key"
                ).use { rs ->
                    while (rs.next()) {
                        val k = rs.getString(1) ?: continue
                        if (k in merged) keyWord[k] = rs.getLong(2)
                    }
                }
            }
        }
        // v-variants inherit their bh-source's word identity for scoring.
        for ((k, wid) in keyWord.entries.toList()) {
            if ("bh" in k) keyWord.putIfAbsent(k.replace("bh", "v"), wid)
        }
        val lexicon = GlideLexicon.build(
            com.banglu.engine.glide.GlideSeedRomans.expandVariants(merged.map { it.key to it.value }),
            grid
        )
        val tuning = com.banglu.engine.glide.GlideTuning(
            cornerWeight = (System.getenv("BANGLU_S163_CORNER") ?: "0.0").toFloat(),
            freqWeight = (System.getenv("BANGLU_S163_FREQW") ?: "0.16").toFloat(),
            maxScore = (System.getenv("BANGLU_S163_MAXSCORE") ?: "1.15").toFloat(),
            shapeWeight = (System.getenv("BANGLU_S163_SHAPEW") ?: "0.7").toFloat(),
            firstKeyBonus = (System.getenv("BANGLU_S163_FIRSTB") ?: "0.25").toFloat(),
        )
        val decoder = GlideDecoder(lexicon, tuning)
        println("S163 STUDY lexicon=${lexicon.size} sigma=$sigma tuning=$tuning")

        val rng = Random(163)
        val stride = (lexicon.size / samples).coerceAtLeast(1)
        val tests = (0 until lexicon.size step stride)
            .map { lexicon.word(it) }.filter { it.length >= 3 }.take(samples)

        var top1 = 0; var top6 = 0; var none = 0; var totalMs = 0.0
        var top1W = 0; var top6W = 0
        for (w in tests) {
            val dense = GlidePath.resample(w.mapNotNull { grid.center(it) }, 24)
            val bx = rng.gaussian() * bias
            val by = rng.gaussian() * bias * 1.5
            val gesture = dense.map {
                GlidePoint(
                    (it.x + bx + rng.gaussian() * sigma).toFloat(),
                    (it.y + by + rng.gaussian() * sigma).toFloat()
                )
            }
            val t0 = System.nanoTime()
            val out = decoder.decode(gesture, firstKey = if (useFirstKey) w[0] else null)
            totalMs += (System.nanoTime() - t0) / 1e6
            when {
                out.isEmpty() -> none++
                out.first().word == w -> { top1++; top6++ }
                out.any { it.word == w } -> top6++
            }
            if (out.isNotEmpty()) {
                val truth = keyWord[w]
                if (truth != null) {
                    if (keyWord[out.first().word] == truth) top1W++
                    if (out.any { keyWord[it.word] == truth }) top6W++
                }
            }
        }
        println(
            "S163 STUDY sigma=$sigma bias=$bias firstKey=$useFirstKey lex=${lexicon.size} " +
                "top1=${"%.1f".format(100.0 * top1 / tests.size)}% " +
                "top6=${"%.1f".format(100.0 * top6 / tests.size)}% " +
                "top1WORD=${"%.1f".format(100.0 * top1W / tests.size)}% " +
                "top6WORD=${"%.1f".format(100.0 * top6W / tests.size)}% " +
                "none=${"%.1f".format(100.0 * none / tests.size)}% " +
                "avg=${"%.1f".format(totalMs / tests.size)}ms over ${tests.size}"
        )
    }
}
