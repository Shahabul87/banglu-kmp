package com.banglu.engine.glide

import kotlin.math.acos
import kotlin.math.sqrt

/** S163: polyline geometry the decoder and lexicon share. */
object GlidePath {

    fun arcLength(p: List<GlidePoint>): Float {
        var s = 0f
        for (i in 1 until p.size) s += dist(p[i - 1], p[i])
        return s
    }

    fun dist(a: GlidePoint, b: GlidePoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /** Resample to n points, evenly spaced by arc length; endpoints kept. */
    fun resample(p: List<GlidePoint>, n: Int): List<GlidePoint> {
        if (p.isEmpty() || n <= 0) return emptyList()
        if (p.size == 1) return List(n) { p[0] }
        val total = arcLength(p)
        if (total < 1e-6f) return List(n) { p[0] }
        val step = total / (n - 1)
        val out = ArrayList<GlidePoint>(n)
        out.add(p[0])
        var acc = 0f
        var i = 1
        var prev = p[0]
        while (out.size < n && i < p.size) {
            val d = dist(prev, p[i])
            if (acc + d >= step - 1e-6f && d > 0f) {
                val t = (step - acc) / d
                val np = GlidePoint(prev.x + t * (p[i].x - prev.x), prev.y + t * (p[i].y - prev.y))
                out.add(np)
                prev = np
                acc = 0f
            } else {
                acc += d
                prev = p[i]
                i++
            }
        }
        while (out.size < n) out.add(p.last())
        return out
    }

    /**
     * Midpoint-average smoothing (endpoints kept). Finger paths carry sensor
     * jitter that manufactures fake corners; templates are never smoothed.
     */
    fun smooth(p: List<GlidePoint>, passes: Int = 2): List<GlidePoint> {
        if (p.size < 3 || passes <= 0) return p
        var cur = p
        repeat(passes) {
            val out = ArrayList<GlidePoint>(cur.size)
            out.add(cur.first())
            for (i in 1 until cur.size - 1) {
                out.add(
                    GlidePoint(
                        (cur[i - 1].x + cur[i].x + cur[i + 1].x) / 3f,
                        (cur[i - 1].y + cur[i].y + cur[i + 1].y) / 3f
                    )
                )
            }
            out.add(cur.last())
            cur = out
        }
        return cur
    }

    /**
     * Arc-length fractions (0..1) of direction changes sharper than 55°.
     * Directions are taken over a ±2-sample window of a 24-point resample —
     * a single-sample window aliases a sharp corner that falls BETWEEN
     * samples into two sub-threshold bends. Corners closer than 0.12 merge.
     */
    fun corners(p: List<GlidePoint>): List<Float> {
        val r = resample(p, 24)
        if (r.size < 5) return emptyList()
        val out = ArrayList<Float>()
        for (i in 2 until r.size - 2) {
            val v1x = r[i].x - r[i - 2].x
            val v1y = r[i].y - r[i - 2].y
            val v2x = r[i + 2].x - r[i].x
            val v2y = r[i + 2].y - r[i].y
            val n1 = sqrt(v1x * v1x + v1y * v1y)
            val n2 = sqrt(v2x * v2x + v2y * v2y)
            if (n1 < 1e-5f || n2 < 1e-5f) continue
            val cos = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1f, 1f)
            val deg = acos(cos) * 180f / kotlin.math.PI.toFloat()
            if (deg > 55f) {
                val frac = i / (r.size - 1).toFloat()
                if (out.isEmpty() || frac - out.last() >= 0.12f) out.add(frac)
            }
        }
        return out
    }
}
