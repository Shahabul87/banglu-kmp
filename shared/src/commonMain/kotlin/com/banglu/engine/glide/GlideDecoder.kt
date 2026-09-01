package com.banglu.engine.glide

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

data class GlideCandidate(val word: String, val score: Float)

/** All decoder tunables in one place; defaults set by S163GlideStudyJvm. */
data class GlideTuning(
    val anchorRadius: Float = 1.6f,
    val lengthRatio: Float = 2.6f,
    val freqWeight: Float = 0.16f,
    val cornerWeight: Float = 0.35f,
    /** Candidates scoring above this commit NOTHING — no surprise text. */
    val maxScore: Float = 1.15f,
    /** Gestures shorter than this (key widths) are taps, not glides. */
    val minArcLength: Float = 1.2f,
)

/**
 * S163: geometry + frequency ONLY. Language context stays where it already
 * lives — the caller converts the ranked romans and applies the existing
 * context rerank. The decoder never touches SmartEngine.
 */
class GlideDecoder(
    private val lexicon: GlideLexicon,
    private val tuning: GlideTuning = GlideTuning(),
) {
    private val maxFreq: Float =
        (0 until lexicon.size).maxOfOrNull { lexicon.freq(it) }?.toFloat()?.coerceAtLeast(1f) ?: 1f

    fun decode(path: List<GlidePoint>, limit: Int = 6): List<GlideCandidate> {
        if (path.size < 2) return emptyList()
        // Normalize exactly like the templates (resample → smooth): jitter
        // corners and jitter arc-length inflation both cancel, and clean
        // glides of cornery words compare like-for-like. Endpoints survive.
        val g = GlidePath.smooth(GlidePath.resample(path, GlideLexicon.N_POINTS))
        val gLen = GlidePath.arcLength(g)
        if (gLen < tuning.minArcLength) return emptyList()

        val gStart = g.first()
        val gEnd = g.last()
        val gCorners = GlidePath.corners(g).size

        val scratch = FloatArray(GlideLexicon.BYTES_PER_TEMPLATE)
        // Bounded top-list, worst-first eviction; tiny (limit<=8), no heap.
        val bestWords = arrayOfNulls<String>(limit)
        val bestScores = FloatArray(limit) { Float.MAX_VALUE }

        for (i in 0 until lexicon.size) {
            if (dist(lexicon.start(i), gStart) >= tuning.anchorRadius) continue
            if (dist(lexicon.end(i), gEnd) >= tuning.anchorRadius) continue
            val tLen = lexicon.length(i)
            if (tLen >= gLen * tuning.lengthRatio + 1f) continue
            if (gLen >= tLen * tuning.lengthRatio + 1f) continue

            lexicon.template(i, scratch)
            var d = 0f
            for (k in 0 until GlideLexicon.N_POINTS) {
                val dx = scratch[2 * k] - g[k].x
                val dy = scratch[2 * k + 1] - g[k].y
                d += sqrt(dx * dx + dy * dy)
            }
            val shape = d / GlideLexicon.N_POINTS
            // Clamped: corner counting is a hint, never a veto — unclamped it
            // let residual jitter corners blow past the commit floor.
            val cornerTerm = tuning.cornerWeight *
                abs(gCorners - templateCornerCount(i, scratch)).coerceAtMost(2)
            val freqTerm = tuning.freqWeight * ln((lexicon.freq(i) + 1f) / maxFreq)
            val score = shape + cornerTerm - freqTerm
            if (score > tuning.maxScore) continue

            var worst = 0
            for (j in 1 until limit) if (bestScores[j] > bestScores[worst]) worst = j
            if (score < bestScores[worst]) {
                bestScores[worst] = score
                bestWords[worst] = lexicon.word(i)
            }
        }

        return (0 until limit)
            .filter { bestWords[it] != null }
            .sortedBy { bestScores[it] }
            .map { GlideCandidate(bestWords[it]!!, bestScores[it]) }
    }

    // Corner counts per template, computed lazily once (template shapes are
    // immutable); index-aligned with the lexicon.
    private val cornerCache = IntArray(lexicon.size) { -1 }

    private fun templateCornerCount(i: Int, dequantized: FloatArray): Int {
        val cached = cornerCache[i]
        if (cached >= 0) return cached
        val pts = ArrayList<GlidePoint>(GlideLexicon.N_POINTS)
        for (k in 0 until GlideLexicon.N_POINTS) {
            pts.add(GlidePoint(dequantized[2 * k], dequantized[2 * k + 1]))
        }
        val c = GlidePath.corners(pts).size
        cornerCache[i] = c
        return c
    }

    private fun dist(a: GlidePoint, b: GlidePoint): Float = GlidePath.dist(a, b)
}
