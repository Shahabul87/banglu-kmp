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
    /** S163 study ablation: any corner penalty HURT on noisy gestures
     *  (0.35 → −10.6pt top-1 at σ=0.25); default 0, kept as a knob for the
     *  on-device tuning round where real finger noise may differ. */
    val cornerWeight: Float = 0.0f,
    /** S163b (user: "accuracy is not so good"): real thumbs carry a
     *  SYSTEMATIC offset (touch lands below the aimed key, whole gesture
     *  drifts); the centroid-aligned shape channel is immune to it. Blend:
     *  score distance = (1-shapeWeight)*location + shapeWeight*alignedShape.
     *  0.7 locked by ablation: bias=0.18 top-1 59.0→72.1%, no-bias unchanged. */
    val shapeWeight: Float = 0.7f,
    /** The glide's first press was resolved by the normal tap machinery —
     *  candidates starting with that letter earn this score reduction. */
    val firstKeyBonus: Float = 0.25f,
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
    val lexicon: GlideLexicon,
    private val tuning: GlideTuning = GlideTuning(),
) {
    private val maxFreq: Float =
        (0 until lexicon.size).maxOfOrNull { lexicon.freq(it) }?.toFloat()?.coerceAtLeast(1f) ?: 1f

    fun decode(path: List<GlidePoint>, limit: Int = 6, firstKey: Char? = null): List<GlideCandidate> {
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
        var gcx = 0f
        var gcy = 0f
        for (p in g) { gcx += p.x; gcy += p.y }
        gcx /= g.size
        gcy /= g.size
        val first = firstKey?.lowercaseChar()

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
            // Two distance channels: raw location, and shape with both
            // centroids aligned (immune to the systematic thumb offset).
            val tcx = lexicon.centroidX(i)
            val tcy = lexicon.centroidY(i)
            var dLoc = 0f
            var dShape = 0f
            for (k in 0 until GlideLexicon.N_POINTS) {
                val dx = scratch[2 * k] - g[k].x
                val dy = scratch[2 * k + 1] - g[k].y
                dLoc += sqrt(dx * dx + dy * dy)
                val sx = (scratch[2 * k] - tcx) - (g[k].x - gcx)
                val sy = (scratch[2 * k + 1] - tcy) - (g[k].y - gcy)
                dShape += sqrt(sx * sx + sy * sy)
            }
            val distTerm = ((1f - tuning.shapeWeight) * dLoc + tuning.shapeWeight * dShape) /
                GlideLexicon.N_POINTS
            // Clamped: corner counting is a hint, never a veto — unclamped it
            // let residual jitter corners blow past the commit floor.
            val cornerTerm = tuning.cornerWeight *
                abs(gCorners - templateCornerCount(i, scratch)).coerceAtMost(2)
            val freqTerm = tuning.freqWeight * ln((lexicon.freq(i) + 1f) / maxFreq)
            val firstTerm = if (first != null && lexicon.word(i)[0] == first) tuning.firstKeyBonus else 0f
            val score = distTerm + cornerTerm - freqTerm - firstTerm
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
